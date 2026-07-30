package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extract.SemanticExtractionBundleBuilder;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.event.SemanticEventCandidate;
import com.relationdetector.semantic.event.SemanticEventCandidateMerger;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 将磁盘输入窗口转换为全局 semantic evidence sections，使用外排 stable-ID 聚合跨窗口 event、
 * graph 和候选后流式写出 full bundle。上游是 SemanticInputStore，下游是全局 owner/shard/KG 执行器；
 * 输入窗口只限制单次物化内存，本类禁止把窗口当作语义边界或持有完整 bundle。
 * EN: Converts disk-backed input windows into global semantic evidence sections, externally merging events, graph
 * records, and candidates across windows before streaming the full bundle. It sits between SemanticInputStore and
 * global owner/shard/KG execution; windows bound materialization memory and never define semantic boundaries.
 */
public final class SemanticEvidenceStore implements AutoCloseable {
    public static final long DEFAULT_WINDOW_BYTES = 8L * 1024L * 1024L;
    public static final int DEFAULT_MAX_INPUT_TOKENS = 800_000;

    public enum Section {
        TABLES("tables", false),
        EVIDENCE("evidence", true),
        METADATA_TABLES("metadataTables", true),
        METADATA_COLUMNS("metadataColumns", true),
        METADATA_CONSTRAINTS("metadataConstraints", true),
        METADATA_INDEXES("metadataIndexes", true),
        RELATIONSHIPS("relationships", true),
        LINEAGE("lineage", true),
        EVENT_CANDIDATES("eventCandidates", true),
        DERIVED_RELATIONSHIPS("derivedRelationships", true),
        DERIVED_LINEAGE("derivedLineage", true),
        NAMING_EVIDENCE("namingEvidence", true),
        REVIEW_ITEM_CANDIDATES("reviewItemCandidates", true),
        TRIPLET_CANDIDATES("tripletCandidates", true),
        DIAGNOSTICS("diagnostics", true);

        private final String wireName;
        private final boolean idRequired;

        Section(String wireName, boolean idRequired) {
            this.wireName = wireName;
            this.idRequired = idRequired;
        }

        public String wireName() {
            return wireName;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SemanticEvidenceBundleWriter BUNDLE_WRITER = new SemanticEvidenceBundleWriter();
    private final SemanticInputStore input;
    private final Path workspace;
    private final Map<Section, ExternalJsonRecordStore> sections;
    private final ExternalJsonRecordStore eventContributions;
    private final SemanticEventAssociationStore eventAssociations;
    private final SemanticGraphRecordStore graphRecords;
    private final int maxInputTokens;
    private boolean closed;

    public SemanticEvidenceStore(SemanticInputStore input, Path workspace) {
        this(input, workspace, DEFAULT_WINDOW_BYTES, DEFAULT_MAX_INPUT_TOKENS);
    }

    public SemanticEvidenceStore(SemanticInputStore input, Path workspace, long maxWindowBytes) {
        this(input, workspace, maxWindowBytes, DEFAULT_MAX_INPUT_TOKENS);
    }

    public SemanticEvidenceStore(
            SemanticInputStore input,
            Path workspace,
            long maxWindowBytes,
            int maxInputTokens
    ) {
        if (input == null || workspace == null || maxWindowBytes <= 0 || maxInputTokens <= 0) {
            throw new IllegalArgumentException(
                    "semantic input, workspace, window limit and token limit are required");
        }
        this.input = input;
        this.workspace = workspace;
        this.maxInputTokens = maxInputTokens;
        this.sections = new EnumMap<>(Section.class);
        try {
            if (Files.exists(workspace)) {
                throw new ScanResultContractException("semantic evidence workspace already exists");
            }
            Files.createDirectories(workspace);
            for (Section section : Section.values()) {
                sections.put(section, new ExternalJsonRecordStore(
                        workspace.resolve("sections").resolve(section.wireName())));
            }
            SemanticEventCandidateMerger eventMerger = new SemanticEventCandidateMerger();
            this.eventContributions = new ExternalJsonRecordStore(
                    workspace.resolve("event-contributions"),
                    (left, right) -> JSON.valueToTree(eventMerger.merge(
                            eventCandidate(left), eventCandidate(right))));
            this.eventAssociations = new SemanticEventAssociationStore(
                    workspace.resolve("event-associations"));
            this.graphRecords = new SemanticGraphRecordStore(workspace.resolve("graph-records"));
            build(maxWindowBytes);
            sections.values().forEach(ExternalJsonRecordStore::finish);
            graphRecords.finish();
        } catch (RuntimeException failure) {
            closeAfterFailure(failure);
            throw failure;
        } catch (IOException failure) {
            RuntimeException wrapped = new ScanResultContractException(
                    "failed to create semantic evidence store", failure);
            closeAfterFailure(wrapped);
            throw wrapped;
        }
    }

    public SemanticInputStore.Descriptor descriptor() {
        ensureOpen();
        return input.descriptor();
    }

    public long count(Section section) {
        ensureOpen();
        return sections.get(section).count();
    }

    public void forEach(Section section, Consumer<JsonNode> consumer) {
        ensureOpen();
        if (section == null || consumer == null) {
            throw new IllegalArgumentException("semantic evidence section and consumer are required");
        }
        sections.get(section).forEach(record -> consumer.accept(record.value()));
    }

    public void forEachDescriptor(Section section, BiConsumer<String, Long> consumer) {
        ensureOpen();
        if (section == null || consumer == null) {
            throw new IllegalArgumentException("semantic evidence section and descriptor consumer are required");
        }
        sections.get(section).forEachDescriptor(consumer);
    }

    public Optional<JsonNode> find(Section section, String id) {
        ensureOpen();
        if (section == null || id == null || id.isBlank()) {
            return Optional.empty();
        }
        return sections.get(section).get(id).map(ExternalJsonRecordStore.Record::value);
    }

    SemanticGraphRecordStore graphRecords() {
        ensureOpen();
        return graphRecords;
    }

    public boolean containsReference(String reference) {
        ensureOpen();
        if (reference == null || reference.isBlank()) {
            return false;
        }
        return sections.get(Section.EVIDENCE).containsKey(reference)
                || sections.get(Section.METADATA_TABLES).containsKey(reference)
                || sections.get(Section.METADATA_COLUMNS).containsKey(reference)
                || sections.get(Section.METADATA_CONSTRAINTS).containsKey(reference)
                || sections.get(Section.METADATA_INDEXES).containsKey(reference)
                || sections.get(Section.RELATIONSHIPS).containsKey(reference)
                || sections.get(Section.LINEAGE).containsKey(reference)
                || sections.get(Section.DERIVED_RELATIONSHIPS).containsKey(reference)
                || sections.get(Section.DERIVED_LINEAGE).containsKey(reference)
                || sections.get(Section.NAMING_EVIDENCE).containsKey(reference)
                || sections.get(Section.DIAGNOSTICS).containsKey(reference)
                || sections.get(Section.EVENT_CANDIDATES).containsKey(reference)
                || sections.get(Section.TRIPLET_CANDIDATES).containsKey(reference)
                || sections.get(Section.REVIEW_ITEM_CANDIDATES).containsKey(reference);
    }

    public void writeBundle(Path target) {
        ensureOpen();
        BUNDLE_WRITER.write(this, target);
    }

    public String writeBundleAndHash(Path target) {
        ensureOpen();
        return BUNDLE_WRITER.writeAndHash(this, target);
    }

    void writeSectionArray(com.fasterxml.jackson.core.JsonGenerator generator, Section section) throws IOException {
        sections.get(section).writeArray(generator, section.wireName());
    }

    private void build(long maxWindowBytes) {
        try (SemanticInputWindowStore windows = new SemanticInputWindowStore(
                input, workspace.resolve("input-windows"))) {
            windows.forEachWindow(maxWindowBytes, window -> {
                Path transport = workspace.resolve("transport-" + window.id() + ".json");
                try {
                    appendWindowGraph(window.bundle(), transport);
                    appendBundle(transport);
                } finally {
                    try {
                        Files.deleteIfExists(transport);
                    } catch (IOException failure) {
                        throw new ScanResultContractException(
                                "failed to clean semantic transport window", failure);
                    }
                }
            });
        }
        appendGlobalEvents();
    }

    private void appendWindowGraph(ScanBundle bundle, Path transport) {
        EvidenceGraph graph = new SemanticExtractionBundleBuilder()
                .writeTransportWindow(bundle, transport);
        graphRecords.append(graph);
    }

    private void appendBundle(Path bundle) {
        try (JsonParser parser = JSON.getFactory().createParser(bundle.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new ScanResultContractException(
                        "semantic input window bundle must be an object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                parser.nextToken();
                Section section = section(field);
                if (section == null) {
                    parser.skipChildren();
                    continue;
                }
                if (parser.currentToken() != JsonToken.START_ARRAY) {
                    throw new ScanResultContractException(
                            "semantic input window bundle section must be an array: " + field);
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    JsonNode item = JSON.readTree(parser);
                    appendRecord(section, item);
                }
            }
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to stream semantic input window bundle", failure);
        }
    }

    private Section section(String wireName) {
        for (Section section : Section.values()) {
            if (section.wireName().equals(wireName)) {
                return section;
            }
        }
        return null;
    }

    private void appendRecord(Section section, JsonNode item) {
        if (item == null) {
            throw new ScanResultContractException(
                    "semantic input window record is missing in " + section.wireName());
        }
        if (section == Section.EVENT_CANDIDATES) {
            SemanticEventCandidate normalized = new SemanticEventCandidateMerger()
                    .normalize(eventCandidate(item));
            eventContributions.append(normalized.id(), JSON.valueToTree(normalized));
            return;
        }
        String key = section.idRequired
                ? item.path("id").asText("")
                : item.asText("");
        if (key.isBlank()) {
            throw new ScanResultContractException(
                    "semantic input window record key is missing in " + section.wireName());
        }
        sections.get(section).append(key, item);
    }

    private void appendGlobalEvents() {
        eventContributions.finish();
        sections.get(Section.RELATIONSHIPS).finish();
        sections.get(Section.DERIVED_LINEAGE).finish();
        SemanticEventCandidateMerger merger = new SemanticEventCandidateMerger();
        eventContributions.forEach(record ->
                eventAssociations.appendEvent(eventCandidate(record.value())));
        sections.get(Section.RELATIONSHIPS).forEach(record ->
                eventAssociations.appendRelationship(record.value()));
        sections.get(Section.DERIVED_LINEAGE).forEach(record ->
                eventAssociations.appendDerivedLineage(record.value()));
        eventAssociations.finish();
        eventContributions.forEach(record -> {
            SemanticEventCandidate event = eventCandidate(record.value());
            eventAssociations.requireWithinEstimatedBudget(
                    event.id(), serializedBytes(record.value()), maxInputTokens);
            SemanticEventCandidate completed = merger.associate(
                    event,
                    eventAssociations.relationshipRefs(event.id()),
                    eventAssociations.derivedLineageRefs(event.id()));
            sections.get(Section.EVENT_CANDIDATES).append(
                    completed.id(), JSON.valueToTree(completed));
            graphRecords.appendFact(new SemanticEvidenceBuilder().eventFact(completed));
            appendEventTriplets(completed);
        });
    }

    private long serializedBytes(JsonNode value) {
        try {
            return JSON.writeValueAsBytes(value).length;
        } catch (IOException failure) {
            throw new ScanResultContractException(
                    "failed to estimate semantic event serialization", failure);
        }
    }

    private void appendEventTriplets(SemanticEventCandidate event) {
        List<String> inputs = event.inputEndpoints().stream().map(this::tableOf).distinct().toList();
        List<String> outputs = event.outputEndpoints().stream().map(this::tableOf).distinct().toList();
        for (String input : inputs) {
            for (String output : outputs) {
                String id = StableSemanticId.of(
                        "triplet-candidate", "event", event.id(), input, output);
                ObjectNode item = JSON.createObjectNode();
                item.put("id", id);
                item.put("type", "EVENT_INPUT_OUTPUT");
                item.put("subject", input);
                item.put("predicate", event.readableNameHint().isBlank()
                        ? "写入" : "通过" + event.readableNameHint() + "写入");
                item.put("object", output);
                item.put("factRef", event.id());
                item.put("eventCandidateRef", event.id());
                item.put("readable", input + " " + item.path("predicate").asText() + " " + output);
                ArrayNode refs = item.putArray("evidenceRefs");
                event.evidenceRefs().forEach(refs::add);
                sections.get(Section.TRIPLET_CANDIDATES).append(id, item);
            }
        }
    }

    private String tableOf(String endpoint) {
        return endpoint == null || endpoint.isBlank()
                ? ""
                : PhysicalEndpointRef.column(endpoint).table();
    }

    private SemanticEventCandidate eventCandidate(JsonNode value) {
        try {
            return JSON.treeToValue(value, SemanticEventCandidate.class);
        } catch (IOException failure) {
            throw new ScanResultContractException("semantic event candidate is malformed", failure);
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic evidence store is closed");
        }
    }

    private void closeAfterFailure(RuntimeException failure) {
        try {
            close();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (ExternalJsonRecordStore section : sections.values()) {
            try {
                section.close();
            } catch (RuntimeException error) {
                if (failure == null) {
                    failure = error;
                } else {
                    failure.addSuppressed(error);
                }
            }
        }
        try {
            eventContributions.close();
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        try {
            eventAssociations.close();
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        try {
            graphRecords.close();
        } catch (RuntimeException error) {
            if (failure == null) {
                failure = error;
            } else {
                failure.addSuppressed(error);
            }
        }
        try {
            deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("failed to clean semantic evidence store", error);
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
