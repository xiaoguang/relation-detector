package com.relationdetector.semantic.reader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonGenerator;
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
    private final SemanticInputStore input;
    private final Path workspace;
    private final Map<Section, ExternalJsonRecordStore> sections;
    private final ExternalJsonRecordStore eventContributions;
    private final SemanticGraphRecordStore graphRecords;
    private boolean closed;

    public SemanticEvidenceStore(SemanticInputStore input, Path workspace) {
        this(input, workspace, DEFAULT_WINDOW_BYTES);
    }

    public SemanticEvidenceStore(SemanticInputStore input, Path workspace, long maxWindowBytes) {
        if (input == null || workspace == null || maxWindowBytes <= 0) {
            throw new IllegalArgumentException("semantic input, workspace and window limit are required");
        }
        this.input = input;
        this.workspace = workspace;
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
        if (target == null) {
            throw new IllegalArgumentException("semantic evidence bundle target is required");
        }
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(target);
                 JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
                generator.useDefaultPrettyPrinter();
                generator.writeStartObject();
                writeDatabase(generator);
                generator.writeObjectField(
                        "metadataInventory",
                        SemanticMetadataInventoryEnvelope.from(descriptor().inventory()));
                writeStringArray(generator, "inputFiles", descriptor().inputFiles());
                writeStringArray(generator, "sources", descriptor().sources());
                for (Section section : Section.values()) {
                    sections.get(section).writeArray(generator, section.wireName());
                }
                generator.writeObjectFieldStart("instructions");
                generator.writeBooleanField("allOutputsMustUseEvidenceRefs", true);
                generator.writeBooleanField("llmCannotCreateDatabaseFacts", true);
                generator.writeBooleanField("businessApprovedIsForbidden", true);
                generator.writeBooleanField("markUncertainItemsReviewNeeded", true);
                generator.writeEndObject();
                generator.writeEndObject();
                generator.writeRaw('\n');
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to stream semantic evidence bundle", failure);
        }
    }

    public String writeBundleAndHash(Path target) {
        writeBundle(target);
        return sha256(target);
    }

    private void build(long maxWindowBytes) {
        try (SemanticInputWindowStore windows = new SemanticInputWindowStore(
                input, workspace.resolve("input-windows"))) {
            windows.forEachWindow(maxWindowBytes, window -> {
                ObjectNode bundle = new SemanticExtractionBundleBuilder().build(window.bundle());
                appendBundle(bundle);
                EvidenceGraph evidenceGraph = new SemanticEvidenceBuilder().build(window.bundle());
                graphRecords.append(evidenceGraph);
            });
        }
        appendGlobalEvents();
    }

    private void appendBundle(ObjectNode bundle) {
        for (Section section : Section.values()) {
            JsonNode values = bundle.path(section.wireName());
            if (!values.isArray()) {
                throw new ScanResultContractException(
                        "semantic input window bundle section must be an array: " + section.wireName());
            }
            for (JsonNode item : values) {
                if (section == Section.EVENT_CANDIDATES) {
                    SemanticEventCandidate normalized = new SemanticEventCandidateMerger()
                            .normalize(eventCandidate(item));
                    eventContributions.append(normalized.id(), JSON.valueToTree(normalized));
                    continue;
                }
                if (section == Section.TRIPLET_CANDIDATES
                        && "EVENT_INPUT_OUTPUT".equals(item.path("type").asText())) {
                    continue;
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
        }
    }

    private void appendGlobalEvents() {
        eventContributions.finish();
        sections.get(Section.RELATIONSHIPS).finish();
        sections.get(Section.DERIVED_LINEAGE).finish();
        SemanticEventCandidateMerger merger = new SemanticEventCandidateMerger();
        eventContributions.forEach(record -> {
            SemanticEventCandidate event = eventCandidate(record.value());
            List<String> relationships = new ArrayList<>();
            sections.get(Section.RELATIONSHIPS).forEach(relationship -> {
                if (relationshipTouchesEvent(relationship.value(), event)) {
                    relationships.add(relationship.key());
                }
            });
            List<String> derived = new ArrayList<>();
            sections.get(Section.DERIVED_LINEAGE).forEach(lineage -> {
                if (lineageTouchesEvent(lineage.value(), event)) {
                    derived.add(lineage.key());
                }
            });
            SemanticEventCandidate completed = merger.associate(event, relationships, derived);
            sections.get(Section.EVENT_CANDIDATES).append(
                    completed.id(), JSON.valueToTree(completed));
            graphRecords.appendFact(new SemanticEvidenceBuilder().eventFact(completed));
            appendEventTriplets(completed);
        });
    }

    private boolean relationshipTouchesEvent(JsonNode relationship, SemanticEventCandidate event) {
        String source = relationship.path("source").asText("");
        String target = relationship.path("target").asText("");
        if (event.outputEndpoints().contains(source) || event.outputEndpoints().contains(target)) {
            return true;
        }
        String sourceTable = tableOf(source);
        String targetTable = tableOf(target);
        List<String> inputTables = event.inputEndpoints().stream().map(this::tableOf).distinct().toList();
        List<String> outputTables = event.outputEndpoints().stream().map(this::tableOf).distinct().toList();
        return outputTables.contains(sourceTable) && inputTables.contains(targetTable)
                || outputTables.contains(targetTable) && inputTables.contains(sourceTable);
    }

    private boolean lineageTouchesEvent(JsonNode lineage, SemanticEventCandidate event) {
        List<String> endpoints = new ArrayList<>();
        lineage.path("sources").forEach(value -> endpoints.add(value.asText("")));
        String target = lineage.path("target").asText("");
        if (!target.isBlank()) {
            endpoints.add(target);
        }
        List<String> eventEndpoints = new ArrayList<>(event.inputEndpoints());
        eventEndpoints.addAll(event.outputEndpoints());
        List<String> eventTables = eventEndpoints.stream().map(this::tableOf).distinct().toList();
        return endpoints.stream().anyMatch(endpoint ->
                eventEndpoints.contains(endpoint) || eventTables.contains(tableOf(endpoint)));
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

    private void writeDatabase(JsonGenerator generator) throws IOException {
        generator.writeObjectFieldStart("database");
        generator.writeStringField("type", descriptor().databaseType());
        generator.writeStringField("catalog", descriptor().catalog());
        generator.writeStringField("schema", descriptor().schema());
        generator.writeEndObject();
    }

    private void writeStringArray(JsonGenerator generator, String field, List<String> values) throws IOException {
        generator.writeArrayFieldStart(field);
        for (String value : values) {
            generator.writeString(value);
        }
        generator.writeEndArray();
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = inputStream.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new ScanResultContractException("failed to hash semantic evidence bundle", failure);
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
