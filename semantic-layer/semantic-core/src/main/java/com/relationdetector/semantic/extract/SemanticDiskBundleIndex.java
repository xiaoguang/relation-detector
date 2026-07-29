package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;
import com.relationdetector.semantic.reader.ScanResultContractException;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;
import com.relationdetector.semantic.reader.SemanticMetadataInventoryEnvelope;

/**
 * CN: 为全局磁盘 evidence sections 建立 item-id 到 typed section 的外排索引，并按一个稳定 root 解析其
 * dependency/evidence closure；上游是完整 SemanticEvidenceStore，下游是 owner planner，本类不按字节切断
 * component，也不在内存保存全部 item。
 * EN: Builds an external item-id-to-section index over complete disk-backed evidence sections and resolves one stable
 * root's dependency/evidence closure at a time. It feeds the owner planner without byte-splitting components or
 * retaining every item in memory.
 */
final class SemanticDiskBundleIndex implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> REFERENCE_FIELDS = List.of(
            "factRef", "eventCandidateRef", "targetRef", "lineageRefs",
            "supportingDerivedLineageRefs", "relationshipRefs");
    private static final Map<SemanticEvidenceStore.Section, String> ITEM_SECTIONS = itemSections();

    private final SemanticEvidenceStore evidence;
    private final Path workspace;
    private final ExternalJsonRecordStore locator;
    private boolean closed;

    SemanticDiskBundleIndex(SemanticEvidenceStore evidence, Path workspace) {
        if (evidence == null || workspace == null) {
            throw new IllegalArgumentException("semantic evidence store and disk index workspace are required");
        }
        this.evidence = evidence;
        this.workspace = workspace;
        try {
            if (Files.exists(workspace)) {
                throw new SemanticShardingException("semantic disk bundle index workspace already exists");
            }
            Files.createDirectories(workspace);
            this.locator = new ExternalJsonRecordStore(workspace.resolve("item-locator"));
            for (Map.Entry<SemanticEvidenceStore.Section, String> entry : ITEM_SECTIONS.entrySet()) {
                evidence.forEach(entry.getKey(), item -> {
                    String id = item.path("id").asText("");
                    if (id.isBlank()) {
                        throw new SemanticShardingException(
                                "semantic evidence item is missing an id in " + entry.getValue());
                    }
                    locator.append(id, JSON.createObjectNode().put("section", entry.getKey().name()));
                });
            }
            locator.finish();
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(failure);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ScanResultContractException("failed to create semantic disk bundle index", failure);
        }
    }

    void forEachRoot(Consumer<RootDescriptor> consumer) {
        ensureOpen();
        if (consumer == null) {
            throw new IllegalArgumentException("semantic root consumer is required");
        }
        locator.forEach(record -> consumer.accept(new RootDescriptor(
                record.key(), section(record.value()))));
    }

    Optional<Item> item(String id) {
        ensureOpen();
        return locator.get(id).map(record -> {
            SemanticEvidenceStore.Section section = section(record.value());
            JsonNode document = evidence.find(section, id).orElseThrow(
                    () -> new SemanticShardingException("semantic item locator points to a missing record"));
            return new Item(id, section, document);
        });
    }

    private SemanticEvidenceStore.Section section(JsonNode locatorRecord) {
        try {
            return SemanticEvidenceStore.Section.valueOf(
                    locatorRecord.path("section").asText(""));
        } catch (IllegalArgumentException failure) {
            throw new SemanticShardingException("semantic item locator contains an invalid section");
        }
    }

    /**
     * CN: 从一个稳定root ID沿typed dependency和evidence reference构建不可再切分的单root闭包，
     * 同时收集物理表并生成有界bundle；只保留当前root所需记录。引用悬空或保守字节预算超过
     * maxInputTokens对应上限时在模型调用前失败，不返回部分闭包。
     *
     * EN: Builds an indivisible single-root closure from a stable root ID by following typed dependency and evidence
     * references, collecting physical tables, and assembling a bounded bundle containing only required records.
     * Unresolved references or a conservative byte estimate beyond the max-input budget fail before model invocation,
     * and no partial closure is returned.
     */
    RootClosure closure(String rootId, int maxInputTokens) {
        ensureOpen();
        if (rootId == null || rootId.isBlank() || maxInputTokens <= 0) {
            throw new IllegalArgumentException("semantic root id and input budget are required");
        }
        Map<String, Item> items = new LinkedHashMap<>();
        Set<String> evidenceIds = new LinkedHashSet<>();
        Set<String> tables = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(rootId);
        long rawBytes = 0;
        long conservativeLimit = (long) maxInputTokens * 8L;
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (items.containsKey(id)) {
                continue;
            }
            Item item = item(id).orElseThrow(
                    () -> new SemanticShardingException("semantic root dependency is unresolved"));
            items.put(id, item);
            rawBytes += item.document().toString().length();
            if (rawBytes > conservativeLimit) {
                throw new SemanticShardingException(
                        "atomic semantic root closure exceeds maximum input budget");
            }
            tables.addAll(directTables(item));
            for (String reference : dependencyRefs(item.document())) {
                if (item(reference).isPresent()) {
                    pending.addLast(reference);
                }
            }
            for (String reference : textValues(item.document().path("evidenceRefs"))) {
                if (item(reference).isPresent()) {
                    pending.addLast(reference);
                } else if (evidence.find(SemanticEvidenceStore.Section.EVIDENCE, reference).isPresent()) {
                    evidenceIds.add(reference);
                } else {
                    throw new SemanticShardingException(
                            "semantic root contains an unresolved evidence reference");
                }
            }
        }
        ObjectNode bundle = emptyBundle();
        tables.stream().sorted().forEach(bundle.withArray("tables")::add);
        for (Item item : items.values()) {
            bundle.withArray(wireName(item.section())).add(item.document().deepCopy());
        }
        for (String evidenceId : evidenceIds.stream().sorted().toList()) {
            bundle.withArray("evidence").add(evidence.find(
                    SemanticEvidenceStore.Section.EVIDENCE, evidenceId).orElseThrow().deepCopy());
        }
        return new RootClosure(
                rootId,
                items.get(rootId).section(),
                Set.copyOf(items.keySet()),
                Set.copyOf(tables),
                bundle,
                rawBytes);
    }

    ObjectNode emptyBundle() {
        ObjectNode bundle = JSON.createObjectNode();
        ObjectNode database = bundle.putObject("database");
        database.put("type", evidence.descriptor().databaseType());
        database.put("catalog", evidence.descriptor().catalog());
        database.put("schema", evidence.descriptor().schema());
        bundle.set("metadataInventory",
                SemanticMetadataInventoryEnvelope.from(evidence.descriptor().inventory()));
        ArrayNode inputFiles = bundle.putArray("inputFiles");
        evidence.descriptor().inputFiles().forEach(inputFiles::add);
        ArrayNode sources = bundle.putArray("sources");
        evidence.descriptor().sources().forEach(sources::add);
        bundle.putArray("tables");
        bundle.putArray("evidence");
        ITEM_SECTIONS.values().forEach(bundle::putArray);
        bundle.putObject("instructions")
                .put("allOutputsMustUseEvidenceRefs", true)
                .put("llmCannotCreateDatabaseFacts", true)
                .put("businessApprovedIsForbidden", true)
                .put("markUncertainItemsReviewNeeded", true);
        return bundle;
    }

    private Set<String> directTables(Item item) {
        Set<String> result = new LinkedHashSet<>();
        JsonNode document = item.document();
        switch (item.section()) {
            case METADATA_TABLES -> addTableValue(result, document.path("table"));
            case METADATA_COLUMNS -> addColumnValue(result, document.path("column"));
            case METADATA_CONSTRAINTS, METADATA_INDEXES -> {
                JsonNode fact = document.path("catalogFact");
                addTable(result,
                        fact.path("catalog").asText(""),
                        fact.path("schema").asText(""),
                        fact.path("tableName").asText(""));
                addTable(result,
                        fact.path("referencedCatalog").asText(""),
                        fact.path("referencedSchema").asText(""),
                        fact.path("referencedTable").asText(""));
            }
            case RELATIONSHIPS, DERIVED_RELATIONSHIPS, NAMING_EVIDENCE -> {
                addColumnValue(result, document.path("source"));
                addColumnValue(result, document.path("target"));
            }
            case LINEAGE, DERIVED_LINEAGE -> {
                document.path("sources").forEach(value -> addColumnValue(result, value));
                addColumnValue(result, document.path("target"));
            }
            case EVENT_CANDIDATES -> {
                document.path("inputEndpoints").forEach(value -> addColumnValue(result, value));
                document.path("outputEndpoints").forEach(value -> addColumnValue(result, value));
            }
            default -> {
                // Candidate ownership is inherited from typed dependencies only.
            }
        }
        return result;
    }

    private Set<String> dependencyRefs(JsonNode item) {
        Set<String> result = new LinkedHashSet<>();
        for (String field : REFERENCE_FIELDS) {
            JsonNode value = item.path(field);
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            } else if (value.isArray()) {
                result.addAll(textValues(value));
            }
        }
        return result;
    }

    private Set<String> textValues(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        if (!values.isArray()) {
            return result;
        }
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                result.add(value.asText());
            }
        });
        return result;
    }

    private void addColumnValue(Set<String> result, JsonNode value) {
        if (value.isTextual() && !value.asText().isBlank()) {
            result.add(PhysicalEndpointRef.column(value.asText()).table());
        }
    }

    private void addTableValue(Set<String> result, JsonNode value) {
        if (value.isTextual() && !value.asText().isBlank()) {
            result.add(value.asText());
        }
    }

    private void addTable(Set<String> result, String catalog, String schema, String table) {
        if (table == null || table.isBlank()) {
            return;
        }
        List<String> parts = new ArrayList<>();
        if (catalog != null && !catalog.isBlank()) {
            parts.add(catalog);
        }
        if (schema != null && !schema.isBlank()) {
            parts.add(schema);
        }
        parts.add(table);
        result.add(String.join(".", parts));
    }

    private String wireName(SemanticEvidenceStore.Section section) {
        return ITEM_SECTIONS.get(section);
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic disk bundle index is closed");
        }
    }

    private void closeAfterFailure(Exception failure) {
        try {
            close();
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        try {
            if (locator != null) {
                locator.close();
            }
        } catch (RuntimeException error) {
            failure = error;
        }
        try {
            deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("failed to clean semantic disk bundle index", error);
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
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Map<SemanticEvidenceStore.Section, String> itemSections() {
        Map<SemanticEvidenceStore.Section, String> result =
                new EnumMap<>(SemanticEvidenceStore.Section.class);
        result.put(SemanticEvidenceStore.Section.METADATA_TABLES, "metadataTables");
        result.put(SemanticEvidenceStore.Section.METADATA_COLUMNS, "metadataColumns");
        result.put(SemanticEvidenceStore.Section.METADATA_CONSTRAINTS, "metadataConstraints");
        result.put(SemanticEvidenceStore.Section.METADATA_INDEXES, "metadataIndexes");
        result.put(SemanticEvidenceStore.Section.RELATIONSHIPS, "relationships");
        result.put(SemanticEvidenceStore.Section.LINEAGE, "lineage");
        result.put(SemanticEvidenceStore.Section.EVENT_CANDIDATES, "eventCandidates");
        result.put(SemanticEvidenceStore.Section.DERIVED_RELATIONSHIPS, "derivedRelationships");
        result.put(SemanticEvidenceStore.Section.DERIVED_LINEAGE, "derivedLineage");
        result.put(SemanticEvidenceStore.Section.NAMING_EVIDENCE, "namingEvidence");
        result.put(SemanticEvidenceStore.Section.REVIEW_ITEM_CANDIDATES, "reviewItemCandidates");
        result.put(SemanticEvidenceStore.Section.TRIPLET_CANDIDATES, "tripletCandidates");
        result.put(SemanticEvidenceStore.Section.DIAGNOSTICS, "diagnostics");
        return Map.copyOf(result);
    }

    record Item(String id, SemanticEvidenceStore.Section section, JsonNode document) {
        Item {
            document = document.deepCopy();
        }
    }

    record RootDescriptor(String id, SemanticEvidenceStore.Section section) {
        RootDescriptor {
            if (id == null || id.isBlank() || section == null) {
                throw new IllegalArgumentException("semantic root descriptor is incomplete");
            }
        }
    }

    record RootClosure(
            String rootId,
            SemanticEvidenceStore.Section rootSection,
            Set<String> itemIds,
            Set<String> tables,
            ObjectNode bundle,
            long rawBytes
    ) {
        RootClosure {
            itemIds = Set.copyOf(itemIds);
            tables = Set.copyOf(tables);
            bundle = bundle.deepCopy();
        }
    }
}
