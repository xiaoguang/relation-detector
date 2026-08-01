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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberKind;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.semantic.StableSemanticId;

/**
 * CN: 流式读取metadata inventory，写入typed section与identity key spool，并通过外排索引验证重复身份和
 * table/column/constraint/index引用闭包。上游loader提供JSON parser与section appender，下游store只接收
 * 已验证的table/column索引。本类不解析普通semantic fact，也不管理最终store生命周期。
 *
 * EN: Streams metadata inventory into typed sections and identity-key spools, then uses external indexes to reject
 * duplicate identities and validate table/column/constraint/index closure. The loader supplies the parser and
 * section appender; the store receives only validated table and column indexes. This class parses no ordinary
 * semantic facts and does not own the final store lifecycle.
 */
final class SemanticMetadataInventoryIndex {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path workspace;
    private final Map<SemanticInputStore.Section, Path> sectionPaths;
    private final JsonRecordAppender appender;
    private BufferedWriter rawTableKeys;
    private BufferedWriter rawColumnKeys;
    private BufferedWriter rawConstraintKeys;
    private BufferedWriter rawIndexKeys;

    SemanticMetadataInventoryIndex(
            Path workspace,
            Map<SemanticInputStore.Section, Path> sectionPaths,
            JsonRecordAppender appender
    ) {
        this.workspace = workspace;
        this.sectionPaths = sectionPaths;
        this.appender = appender;
    }

    void open() throws IOException {
        rawTableKeys = writer("table-keys.raw");
        rawColumnKeys = writer("column-keys.raw");
        rawConstraintKeys = writer("constraint-keys.raw");
        rawIndexKeys = writer("index-keys.raw");
    }

    /**
     * CN: 从当前位置流式消费一个完整inventory对象，逐条校验并可选写入首个输入的typed spool与identity
     * key，同时计算可比较摘要；返回小型descriptor。状态、计数、成员shape或JSON结构不合法时立即失败，
     * 不发布任何可读取store。
     *
     * EN: Consumes one complete inventory object from the current parser position, validates each record, optionally
     * writes the first input to typed spools and identity-key files, and computes its comparable digest. It returns a
     * small descriptor and fails before store publication on invalid status, counts, member shape, or JSON structure.
     */
    SemanticInputStore.InventoryDescriptor read(JsonParser parser, JsonToken token, boolean persist)
            throws IOException {
        require(token == JsonToken.START_OBJECT, "metadataInventory must be an object");
        MetadataInventoryStatus status = null;
        MetadataInventoryBasis basis = null;
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
                case "basis" -> {
                    String text = requireScalarText(parser, value, "metadataInventory.basis");
                    try {
                        basis = MetadataInventoryBasis.valueOf(text);
                    } catch (IllegalArgumentException failure) {
                        throw new ScanResultContractException("metadataInventory.basis is unknown");
                    }
                    update(digest, "basis", text);
                }
                case "scope" -> {
                    JsonNode node = readSmallObject(parser, value, "metadataInventory.scope");
                    scope = scope(node);
                    update(digest, "scope", StableSemanticId.canonicalJson(node));
                }
                case "counts" -> counts = readSmallObject(parser, value, "metadataInventory.counts");
                case "tables" -> actual.put(field, readMetadataArray(
                        parser, value, SemanticInputStore.Section.METADATA_TABLES,
                        MetadataTableFact.class, persist, digest));
                case "columns" -> actual.put(field, readMetadataArray(
                        parser, value, SemanticInputStore.Section.METADATA_COLUMNS,
                        MetadataColumnFact.class, persist, digest));
                case "constraints" -> actual.put(field, readMetadataArray(
                        parser, value, SemanticInputStore.Section.METADATA_CONSTRAINTS,
                        MetadataConstraintFact.class, persist, digest));
                case "indexes" -> actual.put(field, readMetadataArray(
                        parser, value, SemanticInputStore.Section.METADATA_INDEXES,
                        MetadataIndexFact.class, persist, digest));
                default -> parser.skipChildren();
            }
        }
        require(status == MetadataInventoryStatus.COMPLETE,
                "metadataInventory.status must be COMPLETE for semantic processing");
        require(basis != null && basis != MetadataInventoryBasis.NONE,
                "metadataInventory.basis must identify an evidence-backed inventory");
        require(scope != null && counts != null, "metadataInventory scope and counts are required");
        return new SemanticInputStore.InventoryDescriptor(
                status,
                basis,
                scope,
                count(counts, actual, "tables"),
                count(counts, actual, "columns"),
                count(counts, actual, "constraints"),
                count(counts, actual, "indexes"),
                java.util.HexFormat.of().formatHex(digest.digest()));
    }

    InventoryIndexes buildIndexes() throws IOException {
        SortedTextIndex tables = null;
        SortedTextIndex columns = null;
        try {
            tables = build("table-keys.raw", "tables.index", "table-index-work", "metadata tables");
            columns = build("column-keys.raw", "columns.index", "column-index-work", "metadata columns");
            try (SortedTextIndex ignoredConstraints = build(
                         "constraint-keys.raw", "constraints.index",
                         "constraint-index-work", "metadata constraints");
                 SortedTextIndex ignoredIndexes = build(
                         "index-keys.raw", "indexes.index", "index-index-work", "metadata indexes")) {
                return new InventoryIndexes(tables, columns);
            }
        } catch (IOException | RuntimeException failure) {
            closeAfterFailure(tables, failure);
            closeAfterFailure(columns, failure);
            throw failure;
        }
    }

    void validateReferences(SortedTextIndex tables, SortedTextIndex columns) throws IOException {
        validateTableReferences(SemanticInputStore.Section.METADATA_COLUMNS, tables);
        validateConstraintReferences(tables, columns);
        validateIndexReferences(tables, columns);
    }

    IOException closeWriters(IOException failure) {
        failure = close(rawTableKeys, failure);
        failure = close(rawColumnKeys, failure);
        failure = close(rawConstraintKeys, failure);
        failure = close(rawIndexKeys, failure);
        rawTableKeys = null;
        rawColumnKeys = null;
        rawConstraintKeys = null;
        rawIndexKeys = null;
        return failure;
    }

    private <T> long readMetadataArray(
            JsonParser parser,
            JsonToken token,
            SemanticInputStore.Section section,
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
                appender.append(section, item);
                appendInventoryKey(typed);
            }
            count++;
        }
        return count;
    }

    private void validateTableReferences(
            SemanticInputStore.Section section,
            SortedTextIndex tables
    ) throws IOException {
        try (BufferedReader reader = reader(section)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonNode item = JSON.readTree(line);
                String table = MetadataInventoryClosureRules.tableIdentity(
                        nullable(item.get("catalog")), nullable(item.get("schema")),
                        item.path("tableName").asText(""));
                require(tables.contains(SemanticInputStore.encodeKey(table)),
                        section.wireName() + " references a table outside metadata inventory");
            }
        }
    }

    private void validateConstraintReferences(SortedTextIndex tables, SortedTextIndex columns)
            throws IOException {
        try (BufferedReader reader = reader(SemanticInputStore.Section.METADATA_CONSTRAINTS)) {
            String line;
            while ((line = reader.readLine()) != null) {
                MetadataConstraintFact constraint = decode(
                        line, MetadataConstraintFact.class, "metadata constraint");
                require(tables.contains(key(MetadataInventoryClosureRules.tableIdentity(
                                constraint.catalog(), constraint.schema(), constraint.tableName()))),
                        "metadata constraint references a table outside metadata inventory");
                for (String column : constraint.columns()) {
                    require(columns.contains(key(MetadataInventoryClosureRules.columnIdentity(
                                    constraint.catalog(), constraint.schema(),
                                    constraint.tableName(), column))),
                            "metadata constraint references a column outside metadata inventory");
                }
                if (!MetadataInventoryClosureRules.isForeignKey(constraint)) {
                    continue;
                }
                require(tables.contains(key(MetadataInventoryClosureRules.tableIdentity(
                                constraint.referencedCatalog(), constraint.referencedSchema(),
                                constraint.referencedTable()))),
                        "metadata foreign key references a table outside metadata inventory");
                for (String column : constraint.referencedColumns()) {
                    require(columns.contains(key(MetadataInventoryClosureRules.columnIdentity(
                                    constraint.referencedCatalog(), constraint.referencedSchema(),
                                    constraint.referencedTable(), column))),
                            "metadata foreign key references a column outside metadata inventory");
                }
            }
        }
    }

    private void validateIndexReferences(SortedTextIndex tables, SortedTextIndex columns)
            throws IOException {
        try (BufferedReader reader = reader(SemanticInputStore.Section.METADATA_INDEXES)) {
            String line;
            while ((line = reader.readLine()) != null) {
                MetadataIndexFact index = decode(line, MetadataIndexFact.class, "metadata index");
                require(tables.contains(key(MetadataInventoryClosureRules.tableIdentity(
                                index.catalog(), index.schema(), index.tableName()))),
                        "metadata index references a table outside metadata inventory");
                for (var member : index.members()) {
                    if (member.kind() == MetadataIndexMemberKind.EXPRESSION) {
                        continue;
                    }
                    require(columns.contains(key(MetadataInventoryClosureRules.columnIdentity(
                                    index.catalog(), index.schema(), index.tableName(), member.columnName()))),
                            "metadata index references a column outside metadata inventory");
                }
            }
        }
    }

    private void appendInventoryKey(Object value) throws IOException {
        if (value instanceof MetadataTableFact table) {
            writeKey(rawTableKeys, MetadataInventoryClosureRules.tableIdentity(
                    table.catalog(), table.schema(), table.tableName()));
        } else if (value instanceof MetadataColumnFact column) {
            writeKey(rawColumnKeys, MetadataInventoryClosureRules.columnIdentity(
                    column.catalog(), column.schema(), column.tableName(), column.columnName()));
        } else if (value instanceof MetadataConstraintFact constraint) {
            writeKey(rawConstraintKeys, MetadataInventoryClosureRules.constraintIdentity(constraint));
        } else if (value instanceof MetadataIndexFact index) {
            writeKey(rawIndexKeys, MetadataInventoryClosureRules.indexIdentity(index));
        }
    }

    private void validateMetadataFact(SemanticInputStore.Section section, Object value) {
        if (value instanceof MetadataTableFact table) {
            MetadataInventoryClosureRules.validateTable(table);
        } else if (value instanceof MetadataColumnFact column) {
            MetadataInventoryClosureRules.validateColumn(column);
        } else if (value instanceof MetadataConstraintFact constraint) {
            MetadataInventoryClosureRules.validateConstraintShape(constraint);
        } else if (value instanceof MetadataIndexFact index) {
            MetadataInventoryClosureRules.validateIndexShape(index);
        } else {
            throw new ScanResultContractException(
                    section.wireName() + " contains unsupported metadata fact");
        }
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

    private long count(JsonNode counts, Map<String, Long> actual, String field) {
        JsonNode value = counts.path(field);
        require(value.isIntegralNumber() && value.canConvertToLong() && value.asLong() >= 0,
                "metadataInventory.counts." + field + " must be non-negative");
        long count = actual.getOrDefault(field, -1L);
        require(value.asLong() == count,
                "metadataInventory.counts." + field + " does not match inventory array");
        return count;
    }

    private SortedTextIndex build(String raw, String index, String work, String label) throws IOException {
        return SortedTextIndex.build(
                workspace.resolve(raw), workspace.resolve(index), workspace.resolve(work), label);
    }

    private BufferedReader reader(SemanticInputStore.Section section) throws IOException {
        return Files.newBufferedReader(sectionPaths.get(section), StandardCharsets.UTF_8);
    }

    private BufferedWriter writer(String name) throws IOException {
        return Files.newBufferedWriter(
                workspace.resolve(name), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
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

    private <T> T decode(String line, Class<T> type, String label) {
        try {
            return JSON.readValue(line, type);
        } catch (IOException failure) {
            throw new ScanResultContractException(label + " spool contains an invalid fact", failure);
        }
    }

    private void writeKey(BufferedWriter writer, String value) throws IOException {
        writer.write(key(value));
        writer.newLine();
    }

    private String key(String value) {
        return SemanticInputStore.encodeKey(value);
    }

    private String nullable(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
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

    private void closeAfterFailure(SortedTextIndex index, Exception failure) {
        if (index == null) {
            return;
        }
        try {
            index.close();
        } catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new ScanResultContractException(message);
        }
    }

    @FunctionalInterface
    interface JsonRecordAppender {
        void append(SemanticInputStore.Section section, JsonNode item) throws IOException;
    }

    record InventoryIndexes(SortedTextIndex tables, SortedTextIndex columns) {
    }
}
