package com.relationdetector.semantic.reader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 将一个或多个完整 relation-detector JSON 流式校验到分section磁盘spool，并建立外排table、column和fact
 * identity索引；生产semantic链路按需读取单条记录或单个shard，关闭store会清理全部内部工作文件。
 * EN: Streams one or more complete relation-detector JSON documents into per-section disk spools with external
 * table, column, and fact identity indexes. Production semantic stages read one record or one shard at a time, and
 * closing the store removes all internal work files.
 */
public final class SemanticInputStore implements AutoCloseable {
    public enum Section {
        METADATA_TABLES("metadataTables"),
        METADATA_COLUMNS("metadataColumns"),
        METADATA_CONSTRAINTS("metadataConstraints"),
        METADATA_INDEXES("metadataIndexes"),
        RELATIONSHIPS("relationships"),
        DATA_LINEAGES("dataLineages"),
        DERIVED_RELATIONSHIPS("derivedRelationships"),
        DERIVED_DATA_LINEAGES("derivedDataLineages"),
        NAMING_EVIDENCE("namingEvidence"),
        WARNINGS("warnings");

        private final String wireName;

        Section(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Descriptor descriptor;
    private final Map<Section, Path> sectionPaths;
    private final Map<Section, Long> sectionCounts;
    private final SortedTextIndex tableIndex;
    private final SortedTextIndex columnIndex;
    private final SortedTextIndex factIndex;
    private boolean closed;

    private SemanticInputStore(
            Path workspace,
            Descriptor descriptor,
            Map<Section, Path> sectionPaths,
            Map<Section, Long> sectionCounts,
            SortedTextIndex tableIndex,
            SortedTextIndex columnIndex,
            SortedTextIndex factIndex
    ) {
        this.workspace = workspace;
        this.descriptor = descriptor;
        this.sectionPaths = Map.copyOf(sectionPaths);
        this.sectionCounts = Map.copyOf(sectionCounts);
        this.tableIndex = tableIndex;
        this.columnIndex = columnIndex;
        this.factIndex = factIndex;
    }

    static SemanticInputStore open(List<Path> inputs, Path workspace) {
        if (inputs == null || inputs.isEmpty()) {
            throw new IllegalArgumentException("at least one scan result file is required");
        }
        if (workspace == null) {
            throw new IllegalArgumentException("semantic input workspace is required");
        }
        Builder builder = new Builder(workspace);
        try {
            return builder.build(inputs);
        } catch (RuntimeException failure) {
            builder.closeAfterFailure(failure);
            throw failure;
        }
    }

    public Descriptor descriptor() {
        return descriptor;
    }

    public long count(Section section) {
        return sectionCounts.getOrDefault(section, 0L);
    }

    public boolean containsInventoryTable(String catalog, String schema, String table) {
        return contains(tableIndex, encodeKey(tableIdentity(catalog, schema, table)));
    }

    public boolean containsInventoryColumn(String catalog, String schema, String table, String column) {
        return contains(columnIndex, encodeKey(tableIdentity(catalog, schema, table) + component(column)));
    }

    public boolean containsFact(String factId) {
        return contains(factIndex, encodeKey(factId));
    }

    public void forEach(Section section, Consumer<JsonNode> consumer) {
        ensureOpen();
        if (section == null || consumer == null) {
            throw new IllegalArgumentException("semantic input section and consumer are required");
        }
        try (BufferedReader reader = Files.newBufferedReader(sectionPaths.get(section), StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                consumer.accept(JSON.readTree(line));
            }
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to read semantic input spool", failure);
        }
    }

    public void forEachTable(Consumer<MetadataTableFact> consumer) {
        forEachTyped(Section.METADATA_TABLES, MetadataTableFact.class, consumer);
    }

    public void forEachColumn(Consumer<MetadataColumnFact> consumer) {
        forEachTyped(Section.METADATA_COLUMNS, MetadataColumnFact.class, consumer);
    }

    public void forEachConstraint(Consumer<MetadataConstraintFact> consumer) {
        forEachTyped(Section.METADATA_CONSTRAINTS, MetadataConstraintFact.class, consumer);
    }

    public void forEachIndex(Consumer<MetadataIndexFact> consumer) {
        forEachTyped(Section.METADATA_INDEXES, MetadataIndexFact.class, consumer);
    }

    public void forEachRelationship(boolean derived, Consumer<ScanRelationshipFact> consumer) {
        Section section = derived ? Section.DERIVED_RELATIONSHIPS : Section.RELATIONSHIPS;
        forEach(section, item -> consumer.accept(ScanFactFactory.relationships(List.of(item), derived).get(0)));
    }

    public void forEachLineage(boolean derived, Consumer<ScanLineageFact> consumer) {
        Section section = derived ? Section.DERIVED_DATA_LINEAGES : Section.DATA_LINEAGES;
        forEach(section, item -> consumer.accept(ScanFactFactory.lineages(List.of(item), derived).get(0)));
    }

    public void forEachNamingEvidence(Consumer<ScanNamingEvidenceFact> consumer) {
        forEach(Section.NAMING_EVIDENCE,
                item -> consumer.accept(ScanFactFactory.naming(List.of(item)).get(0)));
    }

    public void forEachDiagnostic(Consumer<ScanDiagnosticFact> consumer) {
        forEach(Section.WARNINGS,
                item -> consumer.accept(ScanFactFactory.diagnostics(List.of(item)).get(0)));
    }

    Path sectionPath(Section section) {
        ensureOpen();
        return sectionPaths.get(section);
    }

    Path workspace() {
        ensureOpen();
        return workspace;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        RuntimeException failure = null;
        for (AutoCloseable value : List.of(tableIndex, columnIndex, factIndex)) {
            try {
                value.close();
            } catch (Exception error) {
                failure = new IllegalStateException("failed to close semantic disk index", error);
            }
        }
        try {
            deleteRecursively(workspace);
        } catch (IOException error) {
            if (failure == null) {
                failure = new IllegalStateException("failed to clean semantic input workspace", error);
            } else {
                failure.addSuppressed(error);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private boolean contains(SortedTextIndex index, String key) {
        ensureOpen();
        try {
            return index.contains(key);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to query semantic disk index", failure);
        }
    }

    private <T> void forEachTyped(Section section, Class<T> type, Consumer<T> consumer) {
        forEach(section, item -> {
            try {
                consumer.accept(JSON.treeToValue(item, type));
            } catch (IOException failure) {
                throw new ScanResultContractException("failed to decode typed semantic input fact", failure);
            }
        });
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("semantic input store is closed");
        }
    }

    private static String tableIdentity(String catalog, String schema, String table) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("metadata table name is required");
        }
        return component(catalog) + component(schema) + component(table);
    }

    private static String component(String value) {
        String safe = value == null ? "" : value;
        return safe.length() + ":" + safe + "|";
    }

    private static String encodeKey(String value) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public record InventoryDescriptor(
            MetadataInventoryStatus status,
            ScanScope scope,
            long tableCount,
            long columnCount,
            long constraintCount,
            long indexCount,
            String fingerprint
    ) {
        public InventoryDescriptor {
            if (status != MetadataInventoryStatus.COMPLETE || scope == null
                    || fingerprint == null || fingerprint.isBlank()) {
                throw new ScanResultContractException("semantic inventory descriptor is incomplete");
            }
        }
    }

    public record Descriptor(
            String databaseType,
            String catalog,
            String schema,
            String generatedAt,
            List<String> sources,
            List<String> inputFiles,
            Map<String, Integer> summary,
            InventoryDescriptor inventory
    ) {
        public Descriptor {
            sources = List.copyOf(sources);
            inputFiles = List.copyOf(inputFiles);
            summary = Map.copyOf(summary);
            if (inventory == null) {
                throw new ScanResultContractException("semantic input inventory is required");
            }
        }
    }

    private static final class Builder {
        private final Path workspace;
        private final Map<Section, Path> sectionPaths = new EnumMap<>(Section.class);
        private final Map<Section, BufferedWriter> sectionWriters = new EnumMap<>(Section.class);
        private final Map<Section, Long> sectionCounts = new EnumMap<>(Section.class);
        private final ScanResultContractValidator validator = new ScanResultContractValidator();
        private BufferedWriter rawTableKeys;
        private BufferedWriter rawColumnKeys;
        private BufferedWriter rawFactKeys;
        private Header firstHeader;
        private InventoryDescriptor firstInventory;
        private final Set<String> sources = new LinkedHashSet<>();
        private final List<String> inputFiles = new ArrayList<>();
        private final Map<String, Integer> summary = new LinkedHashMap<>();

        private Builder(Path workspace) {
            this.workspace = workspace;
        }

        SemanticInputStore build(List<Path> inputs) {
            try {
                requireFreshWorkspace();
                openWriters();
                for (int index = 0; index < inputs.size(); index++) {
                    readInput(inputs.get(index), index == 0);
                }
                closeWriters();
                SortedTextIndex tables = SortedTextIndex.build(
                        workspace.resolve("table-keys.raw"), workspace.resolve("tables.index"),
                        workspace.resolve("table-index-work"), "metadata tables");
                SortedTextIndex columns = SortedTextIndex.build(
                        workspace.resolve("column-keys.raw"), workspace.resolve("columns.index"),
                        workspace.resolve("column-index-work"), "metadata columns");
                SortedTextIndex facts = SortedTextIndex.build(
                        workspace.resolve("fact-keys.raw"), workspace.resolve("facts.index"),
                        workspace.resolve("fact-index-work"), "semantic facts");
                validateMetadataReferences(tables);
                Descriptor descriptor = new Descriptor(
                        firstHeader.databaseType(), firstHeader.catalog(), firstHeader.schema(),
                        firstHeader.generatedAt(), List.copyOf(sources), List.copyOf(inputFiles),
                        Map.copyOf(summary), firstInventory);
                return new SemanticInputStore(
                        workspace, descriptor, sectionPaths, sectionCounts, tables, columns, facts);
            } catch (IOException failure) {
                throw new ScanResultContractException("failed to create semantic input store", failure);
            }
        }

        void closeAfterFailure(RuntimeException failure) {
            try {
                closeWriters();
                deleteRecursively(workspace);
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
        }

        private void requireFreshWorkspace() throws IOException {
            if (Files.exists(workspace)) {
                throw new ScanResultContractException("semantic input workspace already exists");
            }
            Files.createDirectories(workspace);
        }

        private void openWriters() throws IOException {
            for (Section section : Section.values()) {
                Path path = workspace.resolve(section.wireName() + ".jsonl");
                sectionPaths.put(section, path);
                sectionWriters.put(section, Files.newBufferedWriter(
                        path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW));
                sectionCounts.put(section, 0L);
            }
            rawTableKeys = writer("table-keys.raw");
            rawColumnKeys = writer("column-keys.raw");
            rawFactKeys = writer("fact-keys.raw");
        }

        private BufferedWriter writer(String name) throws IOException {
            return Files.newBufferedWriter(
                    workspace.resolve(name), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
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
                        case "generatedAt" -> state.generatedAt = requireScalarText(parser, value, "generatedAt");
                        case "summary" -> state.summary = readSmallObject(parser, value, "summary");
                        case "metadataInventory" -> state.inventory = readInventory(parser, value, first);
                        case "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
                                "namingEvidence", "derivedNamingEvidence", "warnings" ->
                                readFactArray(parser, value, field, state);
                        default -> parser.skipChildren();
                    }
                }
            }
            Header header = validateHeader(state, input);
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

        /**
         * CN: 从流式 scan JSON 读取并校验一次完整 metadata inventory，同时写入 typed section spool 和
         * canonical digest；返回小型 descriptor，任一状态、计数或结构错误都会在 store 发布前失败。
         * EN: Streams and validates one complete metadata inventory while writing typed section spools and a
         * canonical digest. It returns a small descriptor and fails before store publication on any status,
         * count, or structural error.
         */
        private InventoryDescriptor readInventory(JsonParser parser, JsonToken token, boolean persist)
                throws IOException {
            require(token == JsonToken.START_OBJECT, "metadataInventory must be an object");
            MetadataInventoryStatus status = null;
            ScanScope scope = null;
            JsonNode counts = null;
            Map<String, Long> actual = new LinkedHashMap<>();
            MessageDigest digest = sha256();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                switch (field) {
                    case "status" -> {
                        String text = requireScalarText(parser, value, "metadataInventory.status");
                        try {
                            status = MetadataInventoryStatus.valueOf(text);
                        } catch (IllegalArgumentException failure) {
                            throw new ScanResultContractException("metadataInventory.status is unknown");
                        }
                        update(digest, "status", text);
                    }
                    case "scope" -> {
                        JsonNode node = readSmallObject(parser, value, "metadataInventory.scope");
                        scope = scope(node);
                        update(digest, "scope", StableSemanticId.canonicalJson(node));
                    }
                    case "counts" -> counts = readSmallObject(parser, value, "metadataInventory.counts");
                    case "tables" -> actual.put(field, readMetadataArray(
                            parser, value, Section.METADATA_TABLES, MetadataTableFact.class, persist, digest));
                    case "columns" -> actual.put(field, readMetadataArray(
                            parser, value, Section.METADATA_COLUMNS, MetadataColumnFact.class, persist, digest));
                    case "constraints" -> actual.put(field, readMetadataArray(
                            parser, value, Section.METADATA_CONSTRAINTS, MetadataConstraintFact.class, persist, digest));
                    case "indexes" -> actual.put(field, readMetadataArray(
                            parser, value, Section.METADATA_INDEXES, MetadataIndexFact.class, persist, digest));
                    default -> parser.skipChildren();
                }
            }
            require(status == MetadataInventoryStatus.COMPLETE,
                    "metadataInventory.status must be COMPLETE for semantic processing");
            require(scope != null && counts != null, "metadataInventory scope and counts are required");
            long tables = count(counts, actual, "tables");
            long columns = count(counts, actual, "columns");
            long constraints = count(counts, actual, "constraints");
            long indexes = count(counts, actual, "indexes");
            return new InventoryDescriptor(
                    status, scope, tables, columns, constraints, indexes,
                    java.util.HexFormat.of().formatHex(digest.digest()));
        }

        private <T> long readMetadataArray(
                JsonParser parser,
                JsonToken token,
                Section section,
                Class<T> type,
                boolean persist,
                MessageDigest digest
        ) throws IOException {
            require(token == JsonToken.START_ARRAY, section.wireName() + " must be an array");
            long count = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                JsonNode item = JSON.readTree(parser);
                require(item != null && item.isObject(), section.wireName() + " item must be an object");
                T typed;
                try {
                    typed = JSON.treeToValue(item, type);
                } catch (Exception failure) {
                    throw new ScanResultContractException(section.wireName() + " item is invalid");
                }
                validateMetadataFact(section, typed);
                update(digest, section.wireName(), StableSemanticId.canonicalJson(item));
                if (persist) {
                    append(section, item);
                    appendInventoryKey(section, typed);
                }
                count++;
            }
            return count;
        }

        private void readFactArray(JsonParser parser, JsonToken token, String field, InputState state)
                throws IOException {
            require(token == JsonToken.START_ARRAY, field + " must be an array");
            long count = 0;
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                JsonNode item = JSON.readTree(parser);
                validator.validateSectionItem(field, item);
                if (!"derivedNamingEvidence".equals(field)) {
                    Section section = section(field);
                    append(section, item);
                rawFactKeys.write(encodeKey(factId(section, item)));
                    rawFactKeys.newLine();
                }
                count++;
            }
            state.factCounts.put(field, count);
        }

        private Header validateHeader(InputState state, Path input) {
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

        private void validateFactCounts(Map<String, Integer> summary, Map<String, Long> facts) {
            equal(summary, "directRelationshipCount", facts, "relationships");
            equal(summary, "derivedRelationshipCount", facts, "derivedRelationships");
            equal(summary, "directDataLineageCount", facts, "dataLineages");
            equal(summary, "derivedDataLineageCount", facts, "derivedDataLineages");
            equal(summary, "warningCount", facts, "warnings");
            long naming = facts.getOrDefault("namingEvidence", 0L);
            require(summary.getOrDefault("totalNamingEvidenceCount", -1) == naming,
                    "summary totalNamingEvidenceCount does not match namingEvidence");
            equal(summary, "derivedNamingEvidenceCount", facts, "derivedNamingEvidence");
        }

        private void equal(
                Map<String, Integer> summary,
                String summaryField,
                Map<String, Long> facts,
                String section
        ) {
            require(summary.getOrDefault(summaryField, -1).longValue() == facts.getOrDefault(section, 0L),
                    "summary " + summaryField + " does not match " + section);
        }

        private void validateMetadataReferences(SortedTextIndex tables) throws IOException {
            validateTableReferences(Section.METADATA_COLUMNS, tables);
            validateTableReferences(Section.METADATA_CONSTRAINTS, tables);
            validateTableReferences(Section.METADATA_INDEXES, tables);
        }

        private void validateTableReferences(Section section, SortedTextIndex tables) throws IOException {
            try (BufferedReader reader = Files.newBufferedReader(sectionPaths.get(section), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    JsonNode item = JSON.readTree(line);
                    String table = tableIdentity(
                            nullable(item.get("catalog")), nullable(item.get("schema")),
                            item.path("tableName").asText(""));
                    require(tables.contains(encodeKey(table)),
                            section.wireName() + " references a table outside metadata inventory");
                }
            }
        }

        private void appendInventoryKey(Section section, Object value) throws IOException {
            if (value instanceof MetadataTableFact table) {
                rawTableKeys.write(encodeKey(tableIdentity(
                        table.catalog(), table.schema(), table.tableName())));
                rawTableKeys.newLine();
            } else if (value instanceof MetadataColumnFact column) {
                String table = tableIdentity(column.catalog(), column.schema(), column.tableName());
                rawColumnKeys.write(encodeKey(table + component(column.columnName())));
                rawColumnKeys.newLine();
            }
        }

        private void validateMetadataFact(Section section, Object value) {
            if (value instanceof MetadataTableFact table) {
                require(nonBlank(table.tableName()) && nonBlank(table.tableType()),
                        "metadata table name and type are required");
            } else if (value instanceof MetadataColumnFact column) {
                require(nonBlank(column.tableName()) && nonBlank(column.columnName())
                                && nonBlank(column.dataType()) && nonBlank(column.columnType())
                                && column.ordinalPosition() > 0,
                        "metadata column identity, type, and ordinal are required");
            } else if (value instanceof MetadataConstraintFact constraint) {
                require(nonBlank(constraint.tableName()) && nonBlank(constraint.constraintName())
                                && nonBlank(constraint.constraintType()),
                        "metadata constraint identity and type are required");
            } else if (value instanceof MetadataIndexFact index) {
                require(nonBlank(index.tableName()) && nonBlank(index.indexName()),
                        "metadata index identity is required");
                index.seqInIndex().forEach(ordinal ->
                        require(ordinal != null && ordinal > 0, "metadata index ordinals must be positive"));
            } else {
                throw new ScanResultContractException(section.wireName() + " contains unsupported metadata fact");
            }
        }

        private String factId(Section section, JsonNode item) {
            return switch (section) {
                case RELATIONSHIPS -> ScanFactFactory.relationships(List.of(item), false).get(0).id();
                case DATA_LINEAGES -> ScanFactFactory.lineages(List.of(item), false).get(0).id();
                case DERIVED_RELATIONSHIPS -> ScanFactFactory.relationships(List.of(item), true).get(0).id();
                case DERIVED_DATA_LINEAGES -> ScanFactFactory.lineages(List.of(item), true).get(0).id();
                case NAMING_EVIDENCE -> ScanFactFactory.naming(List.of(item)).get(0).id();
                case WARNINGS -> ScanFactFactory.diagnostics(List.of(item)).get(0).id();
                default -> throw new ScanResultContractException("metadata section cannot produce semantic fact id");
            };
        }

        private void append(Section section, JsonNode item) throws IOException {
            BufferedWriter writer = sectionWriters.get(section);
            writer.write(JSON.writeValueAsString(item));
            writer.newLine();
            sectionCounts.merge(section, 1L, Long::sum);
        }

        private void closeWriters() throws IOException {
            IOException failure = null;
            for (BufferedWriter writer : sectionWriters.values()) {
                failure = close(writer, failure);
            }
            sectionWriters.clear();
            failure = close(rawTableKeys, failure);
            failure = close(rawColumnKeys, failure);
            failure = close(rawFactKeys, failure);
            rawTableKeys = null;
            rawColumnKeys = null;
            rawFactKeys = null;
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

        private Section section(String wireName) {
            for (Section section : Section.values()) {
                if (section.wireName().equals(wireName)) {
                    return section;
                }
            }
            throw new ScanResultContractException("unsupported semantic input section: " + wireName);
        }

        private ScanScope scope(JsonNode node) {
            require(node.isObject(), "metadataInventory.scope must be an object");
            return new ScanScope(
                    nullable(node.get("catalog")),
                    nullable(node.get("schema")),
                    textList(node.path("includeTables"), "includeTables"),
                    textList(node.path("excludeTables"), "excludeTables"));
        }

        private List<String> textList(JsonNode node, String field) {
            require(node.isArray(), "metadataInventory.scope." + field + " must be an array");
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                require(item.isTextual(), "metadataInventory.scope." + field + " entries must be strings");
                values.add(item.asText());
            });
            return List.copyOf(values);
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

        private long count(JsonNode counts, Map<String, Long> actual, String field) {
            JsonNode value = counts.path(field);
            require(value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0,
                    "metadataInventory.counts." + field + " must be non-negative");
            long count = actual.getOrDefault(field, -1L);
            require(value.asLong() == count,
                    "metadataInventory.counts." + field + " does not match inventory array");
            return count;
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

        private String nullable(JsonNode value) {
            return value == null || value.isNull() ? null : value.asText();
        }

        private String tableIdentity(String catalog, String schema, String table) {
            require(nonBlank(table), "metadata table name is required");
            return component(catalog) + component(schema) + component(table);
        }

        private String component(String value) {
            String safe = value == null ? "" : value;
            return safe.length() + ":" + safe + "|";
        }

        private String scopeIdentity(String catalog, String schema) {
            return component(catalog) + component(schema);
        }

        private boolean nonBlank(String value) {
            return value != null && !value.isBlank();
        }

        private MessageDigest sha256() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException failure) {
                throw new IllegalStateException("SHA-256 is unavailable", failure);
            }
        }

        private void update(MessageDigest digest, String field, String value) {
            digest.update(field.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(value.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }

        private void require(boolean condition, String message) {
            if (!condition) {
                throw new ScanResultContractException(message);
            }
        }

        private static final class InputState {
            private final Map<String, Long> factCounts;
            private JsonNode database;
            private JsonNode summary;
            private String generatedAt;
            private InventoryDescriptor inventory;

            private InputState() {
                this.factCounts = new LinkedHashMap<>();
            }
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
}
