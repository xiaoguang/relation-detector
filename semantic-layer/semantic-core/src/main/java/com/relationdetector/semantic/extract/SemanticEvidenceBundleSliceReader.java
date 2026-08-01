package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 从path-backed完整evidence bundle中，只物化当前raw shard输出及shardContext所需的typed引用闭包。
 * 输入是受模型输出预算约束的raw对象和可能很大的bundle文件，输出是供单片normalization使用的有界ObjectNode；
 * 本类不裁剪owned/overlap身份、不猜测SQL结构，也不读取完整bundle树。
 *
 * EN: Materializes only the typed reference closure required by one bounded raw shard result and its shardContext
 * from a path-backed evidence bundle. It produces a bounded ObjectNode for single-shard normalization without
 * trimming owned or overlap identity, inferring SQL structure, or loading the complete bundle tree.
 */
public final class SemanticEvidenceBundleSliceReader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ENVELOPE_FIELDS = List.of(
            "database", "metadataInventory", "inputFiles", "sources", "instructions", "shardContext");
    private static final Set<String> REFERENCE_FIELDS = Set.of(
            "ownedGroundingRefs", "evidenceRefs", "eventCandidateRef", "candidateRef", "factRef",
            "lineageRefs", "supportingDerivedLineageRefs", "relationshipRefs");
    private static final Set<String> ENDPOINT_FIELDS = Set.of(
            "source", "target", "sources", "inputEndpoints", "outputEndpoints", "endpoints",
            "table", "column", "constraint", "index");

    public ObjectNode read(Path bundlePath, JsonNode rawDocument, int maxEstimatedTokens) {
        if (bundlePath == null || rawDocument == null || !rawDocument.isObject()
                || maxEstimatedTokens <= 0) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle, raw shard document and token limit are required");
        }
        SliceBudget budget = new SliceBudget(maxEstimatedTokens);
        ObjectNode result = JSON.createObjectNode();
        readEnvelope(bundlePath, result, budget);
        Set<String> requiredReferences = new LinkedHashSet<>();
        collectContextReferences(result.path("shardContext"), requiredReferences);
        collectRawReferences(rawDocument, requiredReferences);

        Map<String, Map<String, JsonNode>> selected = new LinkedHashMap<>();
        for (String section : SemanticShardBundleIndex.ITEM_SECTIONS) {
            selected.put(section, new LinkedHashMap<>());
        }
        selected.put("evidence", new LinkedHashMap<>());

        Set<String> physicalReferences = new LinkedHashSet<>();
        collectRawPhysicalReferences(rawDocument, physicalReferences);
        boolean changed;
        do {
            int beforeReferences = requiredReferences.size();
            int beforeItems = selected.values().stream().mapToInt(Map::size).sum();
            scanSelectedObjects(bundlePath, requiredReferences, selected, budget, (section, item) -> {
                Map<String, JsonNode> sectionItems = selected.get(section);
                if (sectionItems == null) {
                    return;
                }
                String id = item.path("id").asText("");
                sectionItems.put(id, item.deepCopy());
                collectReferences(item, requiredReferences);
                collectPhysicalReferences(item, physicalReferences);
            });
            int afterItems = selected.values().stream().mapToInt(Map::size).sum();
            changed = beforeReferences != requiredReferences.size() || beforeItems != afterItems;
        } while (changed);

        requireReferenceClosure(requiredReferences, selected);
        writeSelectedSections(result, selected);
        writeSelectedTables(bundlePath, result, physicalReferences, budget);
        return result;
    }

    private void readEnvelope(
            Path bundlePath,
            ObjectNode result,
            SliceBudget budget
    ) {
        scanTopLevel(bundlePath, budget.constrainedFactory(), (field, parser) -> {
            if (ENVELOPE_FIELDS.contains(field)) {
                result.set(field, budget.readValue(
                        parser, "semantic evidence bundle " + field));
            } else {
                skipValue(parser);
            }
        });
        for (String field : ENVELOPE_FIELDS) {
            if (result.get(field) == null) {
                throw new SemanticExtractionValidationException(
                        "semantic evidence bundle field is required: " + field);
            }
        }
        requireCompleteInventory(result.path("metadataInventory"));
    }

    private void requireCompleteInventory(JsonNode inventory) {
        if (!inventory.isObject() || !"COMPLETE".equals(inventory.path("status").asText())) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle requires COMPLETE metadata inventory");
        }
        if (!inventory.path("scope").isObject()
                || !inventory.path("counts").isObject()
                || inventory.path("fingerprint").asText("").isBlank()) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle inventory descriptor is incomplete");
        }
    }

    private void collectContextReferences(JsonNode context, Set<String> references) {
        if (!context.isObject()) {
            throw new SemanticExtractionValidationException("semantic shardContext is missing or invalid");
        }
        for (String field : List.of("ownedFactRefs", "ownedCandidateRefs", "overlapRefs")) {
            JsonNode values = context.path(field);
            if (!values.isArray()) {
                throw new SemanticExtractionValidationException(
                        "semantic shardContext field must be an array: " + field);
            }
            addTextValues(values, references);
        }
    }

    private void collectRawReferences(JsonNode rawDocument, Set<String> references) {
        for (String section : SemanticShardOutputOwnershipValidator.OUTPUT_SECTIONS) {
            JsonNode items = rawDocument.path(section);
            if (!items.isArray()) {
                throw new SemanticExtractionValidationException(
                        "semantic shard output section must be an array: " + section);
            }
            items.forEach(item -> collectReferences(item, references));
        }
    }

    private void collectReferences(JsonNode value, Set<String> references) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isArray()) {
            value.forEach(item -> collectReferences(item, references));
            return;
        }
        if (!value.isObject()) {
            return;
        }
        value.fields().forEachRemaining(field -> {
            if (REFERENCE_FIELDS.contains(field.getKey())) {
                addTextValues(field.getValue(), references);
            }
        });
    }

    private void collectRawPhysicalReferences(JsonNode rawDocument, Set<String> physical) {
        collectTextField(rawDocument.path("entities"), "physicalName", physical);
        collectTextField(rawDocument.path("metrics"), "physicalField", physical);
        collectTextField(rawDocument.path("metrics"), "sourceFields", physical);
        collectTextField(rawDocument.path("dimensions"), "physicalField", physical);
        collectTextField(rawDocument.path("dimensions"), "dimensionTable", physical);
        collectTextField(rawDocument.path("lineage"), "fromPhysical", physical);
        collectTextField(rawDocument.path("lineage"), "toPhysical", physical);
    }

    private void collectPhysicalReferences(JsonNode value, Set<String> physical) {
        if (value == null || !value.isObject()) {
            return;
        }
        value.fields().forEachRemaining(field -> {
            if (ENDPOINT_FIELDS.contains(field.getKey())) {
                addTextValues(field.getValue(), physical);
            }
        });
    }

    private void collectTextField(JsonNode items, String field, Set<String> values) {
        if (!items.isArray()) {
            return;
        }
        items.forEach(item -> addTextValues(item.path(field), values));
    }

    private void addTextValues(JsonNode value, Set<String> result) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            result.add(value.asText());
        } else if (value.isArray()) {
            value.forEach(item -> addTextValues(item, result));
        }
    }

    private void requireReferenceClosure(
            Set<String> requiredReferences,
            Map<String, Map<String, JsonNode>> selected
    ) {
        Set<String> resolved = new LinkedHashSet<>();
        selected.values().forEach(items -> resolved.addAll(items.keySet()));
        if (!resolved.containsAll(requiredReferences)) {
            throw new SemanticExtractionValidationException(
                    "semantic shard references are not closed by the evidence bundle");
        }
    }

    private void writeSelectedSections(
            ObjectNode result,
            Map<String, Map<String, JsonNode>> selected
    ) {
        for (String section : SemanticShardBundleIndex.ITEM_SECTIONS) {
            ArrayNode output = result.putArray(section);
            selected.get(section).values().forEach(output::add);
        }
        ArrayNode evidence = result.putArray("evidence");
        selected.get("evidence").values().forEach(evidence::add);
    }

    private void writeSelectedTables(
            Path bundlePath,
            ObjectNode result,
            Set<String> physicalReferences,
            SliceBudget budget
    ) {
        ArrayNode tables = result.putArray("tables");
        scanArrays(bundlePath, Set.of("tables"), (section, item) -> {
            if (!"tables".equals(section) || !item.isTextual()) {
                return;
            }
            String table = item.asText();
            boolean required = physicalReferences.stream()
                    .anyMatch(reference -> reference.equals(table) || reference.startsWith(table + "."));
            if (required) {
                budget.add(item);
                tables.add(table);
            }
        });
    }

    private void scanSelectedObjects(
            Path bundlePath,
            Set<String> requiredReferences,
            Map<String, Map<String, JsonNode>> selected,
            SliceBudget budget,
            BiConsumer<String, JsonNode> consumer
    ) {
        Path spool = createRecordSpool(bundlePath);
        try {
            scanTopLevel(bundlePath, (field, parser) -> {
                if (!"evidence".equals(field)
                        && !SemanticShardBundleIndex.ITEM_SECTIONS.contains(field)) {
                    skipValue(parser);
                    return;
                }
                if (parser.currentToken() != JsonToken.START_ARRAY) {
                    throw new SemanticExtractionValidationException(
                            "semantic evidence bundle section must be an array: " + field);
                }
                try {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        RecordLocation record = scanRecordLocation(parser, field);
                        String id = record.id();
                        Map<String, JsonNode> sectionItems = selected.get(field);
                        if (id.isBlank()
                                || sectionItems == null
                                || !requiredReferences.contains(id)
                                || sectionItems.containsKey(id)) {
                            continue;
                        }
                        copyRecord(bundlePath, spool, record);
                        budget.requireMayFit(spool);
                        JsonNode item = JSON.readTree(spool.toFile());
                        if (item == null || !item.isObject()) {
                            throw new SemanticExtractionValidationException(
                                    "semantic evidence bundle item must be an object: " + field);
                        }
                        budget.add(item);
                        consumer.accept(field, item);
                    }
                } catch (IOException failure) {
                    throw new SemanticExtractionValidationException(
                            "failed to stream semantic evidence bundle section: " + field);
                }
            });
        } finally {
            try {
                Files.deleteIfExists(spool);
            } catch (IOException ignored) {
                // A failed command never publishes its temporary normalization output.
            }
        }
    }

    private Path createRecordSpool(Path bundlePath) {
        try {
            Path parent = bundlePath.toAbsolutePath().normalize().getParent();
            return Files.createTempFile(parent, ".semantic-evidence-record-", ".json");
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to create semantic evidence record spool");
        }
    }

    private RecordLocation scanRecordLocation(JsonParser parser, String section) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle item must be an object: " + section);
        }
        long start = parser.currentTokenLocation().getByteOffset();
        String id = "";
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentToken() != JsonToken.FIELD_NAME) {
                throw new SemanticExtractionValidationException(
                        "semantic evidence bundle item contains an invalid token: " + section);
            }
            String field = parser.currentName();
            parser.nextToken();
            if ("id".equals(field) && parser.currentToken() == JsonToken.VALUE_STRING) {
                id = parser.getValueAsString("");
            }
            parser.skipChildren();
        }
        long end = parser.currentTokenLocation().getByteOffset() + 1L;
        if (start < 0 || end <= start) {
            throw new SemanticExtractionValidationException(
                    "semantic evidence bundle item location is invalid: " + section);
        }
        return new RecordLocation(id, start, end);
    }

    private void copyRecord(Path bundlePath, Path spool, RecordLocation record) throws IOException {
        try (FileChannel input = FileChannel.open(bundlePath, StandardOpenOption.READ);
             FileChannel output = FileChannel.open(
                     spool,
                     StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            long position = record.start();
            long remaining = record.end() - record.start();
            while (remaining > 0) {
                long transferred = input.transferTo(position, remaining, output);
                if (transferred <= 0) {
                    throw new IOException("semantic evidence record transfer made no progress");
                }
                position += transferred;
                remaining -= transferred;
            }
        }
    }

    private void scanArrays(
            Path bundlePath,
            Set<String> selectedSections,
            BiConsumer<String, JsonNode> consumer
    ) {
        scanTopLevel(bundlePath, (field, parser) -> {
            if (!selectedSections.contains(field)) {
                skipValue(parser);
                return;
            }
            if (parser.currentToken() != JsonToken.START_ARRAY) {
                throw new SemanticExtractionValidationException(
                        "semantic evidence bundle section must be an array: " + field);
            }
            try {
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    consumer.accept(field, JSON.readTree(parser));
                }
            } catch (IOException failure) {
                throw new SemanticExtractionValidationException(
                        "failed to stream semantic evidence bundle section: " + field);
            }
        });
    }

    private void scanTopLevel(Path bundlePath, TopLevelConsumer consumer) {
        scanTopLevel(bundlePath, JSON.getFactory(), consumer);
    }

    private void scanTopLevel(
            Path bundlePath,
            JsonFactory factory,
            TopLevelConsumer consumer
    ) {
        try (JsonParser parser = factory.createParser(bundlePath.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new SemanticExtractionValidationException(
                        "semantic evidence bundle must be a JSON object");
            }
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) {
                    throw new SemanticExtractionValidationException(
                            "semantic evidence bundle contains an invalid top-level token");
                }
                String field = parser.currentName();
                parser.nextToken();
                consumer.accept(field, parser);
            }
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to stream semantic evidence bundle");
        }
    }

    private void skipValue(JsonParser parser) {
        try {
            parser.skipChildren();
        } catch (IOException failure) {
            throw new SemanticExtractionValidationException(
                    "failed to skip semantic evidence bundle value");
        }
    }

    @FunctionalInterface
    private interface TopLevelConsumer {
        void accept(String field, JsonParser parser);
    }

    private record RecordLocation(String id, long start, long end) {
    }

    private static final class SliceBudget {
        private final SemanticTokenEstimateBudget budget;

        private SliceBudget(int maxEstimatedTokens) {
            budget = new SemanticTokenEstimateBudget(maxEstimatedTokens);
            addText("{database,metadataInventory,inputFiles,sources,instructions,shardContext,"
                    + "tables,evidence,metadataTables,metadataColumns,metadataConstraints,"
                    + "metadataIndexes,relationships,lineage,derivedRelationships,derivedLineage,"
                    + "namingEvidence,diagnostics,eventCandidates,tripletCandidates,"
                    + "reviewItemCandidates}");
        }

        private void add(JsonNode value) {
            try {
                addText(JSON.writeValueAsString(value));
            } catch (IOException failure) {
                throw new SemanticExtractionValidationException(
                        "failed to estimate semantic evidence slice");
            }
        }

        private void addText(String value) {
            budget.addText(value);
        }

        private JsonNode readValue(JsonParser parser, String label) {
            return budget.readValue(parser, JSON, label);
        }

        private JsonFactory constrainedFactory() {
            return JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxStringLength(budget.maximumSingleStringLength())
                            .build())
                    .build();
        }

        private void requireMayFit(Path value) {
            try {
                budget.requireMayFitUtf8Bytes(Files.size(value));
            } catch (IOException failure) {
                throw new SemanticExtractionValidationException(
                        "failed to inspect semantic evidence record size");
            }
        }
    }
}
