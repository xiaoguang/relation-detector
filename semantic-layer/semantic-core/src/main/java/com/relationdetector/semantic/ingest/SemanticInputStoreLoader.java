package com.relationdetector.semantic.ingest;

import com.relationdetector.semantic.internal.store.SortedTextIndex;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * CN: 流式读取一个或多个scan JSON，校验header与fact wire契约并写入section spool，随后协调metadata
 * inventory索引和fact索引发布SemanticInputStore。失败时关闭全部writer/index并清理工作目录。本类不提供
 * 查询facade，也不实现metadata shape或引用闭包规则。
 *
 * EN: Streams one or more scan JSON documents, validates header and fact wire contracts, writes section spools,
 * and coordinates metadata and fact indexes before publishing SemanticInputStore. Failure closes all writers and
 * indexes and removes the workspace. This loader exposes no query facade and owns no metadata shape or closure rules.
 */
final class SemanticInputStoreLoader {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Map<SemanticInputStore.Section, Path> sectionPaths =
            new EnumMap<>(SemanticInputStore.Section.class);
    private final Map<SemanticInputStore.Section, BufferedWriter> sectionWriters =
            new EnumMap<>(SemanticInputStore.Section.class);
    private final Map<SemanticInputStore.Section, Long> sectionCounts =
            new EnumMap<>(SemanticInputStore.Section.class);
    private final ScanResultContractValidator validator = new ScanResultContractValidator();
    private final Set<String> sources = new LinkedHashSet<>();
    private final List<String> inputFiles = new ArrayList<>();
    private final Map<String, Integer> summary = new LinkedHashMap<>();
    private SemanticMetadataInventoryIndex metadata;
    private BufferedWriter rawFactKeys;
    private Header firstHeader;
    private SemanticInputStore.InventoryDescriptor firstInventory;

    SemanticInputStoreLoader(Path workspace) {
        this.workspace = workspace;
    }

    SemanticInputStore load(List<Path> inputs) {
        SortedTextIndex tables = null;
        SortedTextIndex columns = null;
        SortedTextIndex facts = null;
        try {
            requireFreshWorkspace();
            openWriters();
            metadata = new SemanticMetadataInventoryIndex(workspace, sectionPaths, this::append);
            metadata.open();
            for (int index = 0; index < inputs.size(); index++) {
                readInput(inputs.get(index), index == 0);
            }
            closeWriters();
            SemanticMetadataInventoryIndex.InventoryIndexes inventoryIndexes = metadata.buildIndexes();
            tables = inventoryIndexes.tables();
            columns = inventoryIndexes.columns();
            facts = SortedTextIndex.build(
                    workspace.resolve("fact-keys.raw"), workspace.resolve("facts.index"),
                    workspace.resolve("fact-index-work"), "semantic facts");
            metadata.validateReferences(tables, columns);
            SemanticInputStore.Descriptor descriptor = new SemanticInputStore.Descriptor(
                    firstHeader.databaseType(), firstHeader.catalog(), firstHeader.schema(),
                    firstHeader.generatedAt(), List.copyOf(sources), List.copyOf(inputFiles),
                    Map.copyOf(summary), firstInventory);
            return new SemanticInputStore(
                    workspace, descriptor, sectionPaths, sectionCounts, tables, columns, facts);
        } catch (IOException | RuntimeException failure) {
            closeIndexesAfterFailure(tables, columns, facts, failure);
            cleanAfterFailure(failure);
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new ScanResultContractException("failed to create semantic input store", failure);
        }
    }

    private void requireFreshWorkspace() throws IOException {
        if (Files.exists(workspace)) {
            throw new ScanResultContractException("semantic input workspace already exists");
        }
        Files.createDirectories(workspace);
    }

    private void openWriters() throws IOException {
        for (SemanticInputStore.Section section : SemanticInputStore.Section.values()) {
            Path path = workspace.resolve(section.wireName() + ".jsonl");
            sectionPaths.put(section, path);
            sectionWriters.put(section, Files.newBufferedWriter(
                    path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW));
            sectionCounts.put(section, 0L);
        }
        rawFactKeys = Files.newBufferedWriter(
                workspace.resolve("fact-keys.raw"), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private void readInput(Path input, boolean first) throws IOException {
        if (input == null || !Files.isRegularFile(input)) {
            throw new ScanResultContractException("scan result input file is unavailable");
        }
        InputState state = new InputState();
        try (JsonParser parser = JSON.getFactory().createParser(input.toFile())) {
            require(parser.nextToken() == JsonToken.START_OBJECT, "scan result JSON root must be an object");
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                require(parser.currentToken() == JsonToken.FIELD_NAME, "scan result field name is required");
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (field) {
                    case "database" -> state.database = readSmallObject(parser, value, "database");
                    case "generatedAt" -> state.generatedAt =
                            requireScalarText(parser, value, "generatedAt");
                    case "summary" -> state.summary = readSmallObject(parser, value, "summary");
                    case "metadataInventory" -> state.inventory = metadata.read(parser, value, first);
                    case "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
                            "namingEvidence", "derivedNamingEvidence", "warnings" ->
                            readFactArray(parser, value, field, state);
                    default -> parser.skipChildren();
                }
            }
        }
        Header header = validateHeader(state);
        if (firstHeader == null) {
            firstHeader = header;
            firstInventory = state.inventory;
        } else {
            require(firstHeader.sameDatabase(header),
                    "merged scan results must use the same database identity");
            require(firstInventory.equals(state.inventory),
                    "merged scan results must use the same COMPLETE metadata inventory");
        }
        sources.addAll(header.sources());
        inputFiles.add(SemanticInputPathCanonicalizer.canonicalize(input));
        header.summary().forEach((key, value) -> summary.merge(key, value, Integer::sum));
    }

    private void readFactArray(JsonParser parser, JsonToken token, String field, InputState state)
            throws IOException {
        require(token == JsonToken.START_ARRAY, field + " must be an array");
        long count = 0;
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            JsonNode item = JSON.readTree(parser);
            validator.validateSectionItem(field, item);
            if (!"derivedNamingEvidence".equals(field)) {
                SemanticInputStore.Section section = section(field);
                append(section, item);
                rawFactKeys.write(SemanticInputStore.encodeKey(ScanFactIdentity.of(section, item)));
                rawFactKeys.newLine();
            }
            count++;
        }
        state.factCounts.put(field, count);
    }

    private Header validateHeader(InputState state) {
        require(state.database != null && state.summary != null && state.inventory != null,
                "scan result database, summary, and metadataInventory are required");
        String type = text(state.database, "type", true);
        try {
            DatabaseType.valueOf(type.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new ScanResultContractException("database.type is unknown");
        }
        String catalog = text(state.database, "catalog", false);
        String schema = text(state.database, "schema", false);
        try {
            Instant.parse(state.generatedAt);
        } catch (DateTimeParseException | NullPointerException failure) {
            throw new ScanResultContractException("generatedAt must be an ISO-8601 instant");
        }
        Map<String, Integer> values = summary(state.summary);
        validateFactCounts(values, state.factCounts);
        List<String> sourceValues = new ArrayList<>();
        JsonNode sourceArray = state.summary.path("sources");
        require(sourceArray.isArray(), "summary.sources must be an array");
        sourceArray.forEach(value -> {
            require(value.isTextual(), "summary.sources entries must be strings");
            sourceValues.add(value.asText());
        });
        require(scopeIdentity(catalog, schema).equals(scopeIdentity(
                        state.inventory.scope().catalog(), state.inventory.scope().schema())),
                "metadata inventory scope does not match database identity");
        return new Header(type, catalog, schema, state.generatedAt, sourceValues, values);
    }

    private void validateFactCounts(Map<String, Integer> values, Map<String, Long> facts) {
        equal(values, "directRelationshipCount", facts, "relationships");
        equal(values, "derivedRelationshipCount", facts, "derivedRelationships");
        equal(values, "directDataLineageCount", facts, "dataLineages");
        equal(values, "derivedDataLineageCount", facts, "derivedDataLineages");
        equal(values, "warningCount", facts, "warnings");
        require(values.getOrDefault("totalNamingEvidenceCount", -1).longValue()
                        == facts.getOrDefault("namingEvidence", 0L),
                "summary totalNamingEvidenceCount does not match namingEvidence");
        equal(values, "derivedNamingEvidenceCount", facts, "derivedNamingEvidence");
    }

    private void equal(
            Map<String, Integer> values,
            String summaryField,
            Map<String, Long> facts,
            String section
    ) {
        require(values.getOrDefault(summaryField, -1).longValue() == facts.getOrDefault(section, 0L),
                "summary " + summaryField + " does not match " + section);
    }

    private void append(SemanticInputStore.Section section, JsonNode item) throws IOException {
        BufferedWriter writer = sectionWriters.get(section);
        writer.write(JSON.writeValueAsString(item));
        writer.newLine();
        sectionCounts.merge(section, 1L, Long::sum);
    }

    private SemanticInputStore.Section section(String wireName) {
        for (SemanticInputStore.Section section : SemanticInputStore.Section.values()) {
            if (section.wireName().equals(wireName)) {
                return section;
            }
        }
        throw new ScanResultContractException("unsupported semantic input section: " + wireName);
    }

    private JsonNode readSmallObject(JsonParser parser, JsonToken token, String field) throws IOException {
        require(token == JsonToken.START_OBJECT, field + " must be an object");
        return JSON.readTree(parser);
    }

    private String requireScalarText(JsonParser parser, JsonToken token, String field) throws IOException {
        require(token == JsonToken.VALUE_STRING, field + " must be a string");
        String value = parser.getValueAsString();
        require(value != null && !value.isBlank(), field + " must not be blank");
        return value;
    }

    private String text(JsonNode parent, String field, boolean required) {
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) {
            require(!required, field + " is required");
            return "";
        }
        require(value.isTextual(), field + " must be a string");
        require(!required || !value.asText().isBlank(), field + " must not be blank");
        return value.asText();
    }

    private Map<String, Integer> summary(JsonNode node) {
        Map<String, Integer> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue().isIntegralNumber() && entry.getValue().canConvertToInt()) {
                result.put(entry.getKey(), entry.getValue().asInt());
            }
        });
        return result;
    }

    private String scopeIdentity(String catalog, String schema) {
        return component(catalog) + component(schema);
    }

    private String component(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe + "|";
    }

    private void closeWriters() throws IOException {
        IOException failure = null;
        for (BufferedWriter writer : sectionWriters.values()) {
            failure = close(writer, failure);
        }
        sectionWriters.clear();
        failure = close(rawFactKeys, failure);
        rawFactKeys = null;
        if (metadata != null) {
            failure = metadata.closeWriters(failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private IOException close(AutoCloseable value, IOException failure) {
        if (value == null) {
            return failure;
        }
        try {
            value.close();
        } catch (Exception error) {
            IOException wrapped = error instanceof IOException io ? io : new IOException(error);
            if (failure == null) {
                return wrapped;
            }
            failure.addSuppressed(wrapped);
        }
        return failure;
    }

    private void closeIndexesAfterFailure(
            SortedTextIndex tables,
            SortedTextIndex columns,
            SortedTextIndex facts,
            Exception failure
    ) {
        for (SortedTextIndex index : new SortedTextIndex[]{tables, columns, facts}) {
            if (index == null) {
                continue;
            }
            try {
                index.close();
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
        }
    }

    private void cleanAfterFailure(Exception failure) {
        try {
            closeWriters();
        } catch (Exception cleanup) {
            failure.addSuppressed(cleanup);
        }
        try {
            SemanticInputStore.deleteRecursively(workspace);
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ScanResultContractException(message);
        }
    }

    private static final class InputState {
        private final Map<String, Long> factCounts = new LinkedHashMap<>();
        private JsonNode database;
        private JsonNode summary;
        private String generatedAt;
        private SemanticInputStore.InventoryDescriptor inventory;
    }

    private record Header(
            String databaseType,
            String catalog,
            String schema,
            String generatedAt,
            List<String> sources,
            Map<String, Integer> summary
    ) {
        private boolean sameDatabase(Header other) {
            return databaseType.equals(other.databaseType)
                    && catalog.equals(other.catalog)
                    && schema.equals(other.schema);
        }
    }
}
