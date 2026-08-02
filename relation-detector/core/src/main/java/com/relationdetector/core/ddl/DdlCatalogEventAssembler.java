package com.relationdetector.core.ddl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.Enums.DdlIndexKind;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.parse.DdlCatalogEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * CN: 将一个statement的typed DDL column/FK/index事件装配为稳定catalog事实；输入来自已校验parser event，
 * 输出一个不可变DDL_CATALOG事件。本类不读取statement SQL，也不猜测未出现的表列。
 * EN: Assembles validated typed DDL column, foreign-key, and index events for one statement into a stable immutable
 * DDL_CATALOG event. It never reads statement SQL or guesses declarations absent from typed events.
 */
public final class DdlCatalogEventAssembler {
    private static final String GENERATED_NAME_PREFIX = "DDL$";

    /**
     * CN: 输入一个已分帧DDL statement及其已校验typed事件，按statement scope聚合表、列、外键和索引，
     * 返回稳定排序的不可变catalog事件；引用型列不会升级为声明。输入缺失或typed identity冲突时显式失败，
     * 本方法不读取raw SQL、不修改输入事件，也不补猜parser未提供的声明。
     * EN: Consumes one framed DDL statement and its validated typed events, aggregates tables, columns, foreign keys,
     * and indexes within statement scope, and returns a stably ordered immutable catalog event. Reference-only columns
     * are never promoted to declarations. Missing input or conflicting typed identity fails explicitly; the method
     * neither reads raw SQL nor mutates events or invents declarations absent from the parser.
     */
    public DdlCatalogEvent assemble(SqlStatementRecord statement, List<? extends StructuredSqlEvent> input) {
        if (statement == null) {
            throw new IllegalArgumentException("DDL catalog statement is required");
        }
        List<? extends StructuredSqlEvent> events = input == null ? List.of() : List.copyOf(input);
        SourceProvenance provenance = events.isEmpty()
                ? SourceProvenance.source(statement.sourceName(), 1).rebase(statement)
                : events.get(0).provenance();

        Map<TableKey, MetadataTableFact> tables = new LinkedHashMap<>();
        Map<ColumnKey, MetadataColumnFact> columns = new LinkedHashMap<>();
        Map<ForeignKeyGroup, List<StructuredSqlEvent>> foreignKeys = new LinkedHashMap<>();
        Map<IndexGroup, List<StructuredSqlEvent>> indexes = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            switch (event.type()) {
                case DDL_COLUMN -> {
                    if (!"REFERENCE".equals(event.kind())) {
                        addColumn(tables, columns, event.table(), event.column());
                    }
                }
                case DDL_FOREIGN_KEY ->
                        foreignKeys.computeIfAbsent(ForeignKeyGroup.from(event), ignored -> new ArrayList<>()).add(event);
                case DDL_INDEX ->
                        indexes.computeIfAbsent(IndexGroup.from(event), ignored -> new ArrayList<>()).add(event);
                default -> {
                }
            }
        }
        addTypedObjectTable(statement, tables);
        assignColumnOrdinals(columns);

        List<MetadataConstraintFact> constraints = new ArrayList<>();
        foreignKeys.forEach((key, values) -> constraints.add(foreignKey(key, values)));
        List<MetadataIndexFact> indexFacts = new ArrayList<>();
        indexes.forEach((key, values) -> {
            MetadataIndexFact index = index(key, values);
            indexFacts.add(index);
            if (index.unique()) {
                constraints.add(uniqueConstraint(index));
            }
        });
        return new DdlCatalogEvent(
                StructuredParseEventType.DDL_CATALOG,
                provenance,
                sorted(tables.values(), Comparator.comparing(this::tableIdentity)),
                sorted(columns.values(), Comparator.comparing(this::columnIdentity)),
                sorted(constraints, Comparator.comparing(this::constraintIdentity)),
                sorted(indexFacts, Comparator.comparing(this::indexIdentity)),
                List.of());
    }

    private void addTypedObjectTable(
            SqlStatementRecord statement,
            Map<TableKey, MetadataTableFact> tables
    ) {
        String type = switch (statement.sourceType()) {
            case VIEW -> "VIEW";
            case MATERIALIZED_VIEW -> "MATERIALIZED_VIEW";
            default -> "";
        };
        if (type.isBlank()) {
            return;
        }
        Object value = statement.attributes().get("sourceObjectName");
        String name = value == null ? "" : String.valueOf(value);
        if (name.isBlank()) {
            return;
        }
        TableKey key = tableKey(name);
        tables.putIfAbsent(key, new MetadataTableFact(
                key.catalog(), key.schema(), key.table(), type, null, null));
    }

    private void addColumn(
            Map<TableKey, MetadataTableFact> tables,
            Map<ColumnKey, MetadataColumnFact> columns,
            String rawTable,
            String rawColumn
    ) {
        TableKey table = tableKey(rawTable);
        String column = cleanIdentifier(rawColumn);
        if (table.table().isBlank() || column.isBlank()) {
            return;
        }
        tables.putIfAbsent(table, new MetadataTableFact(
                table.catalog(), table.schema(), table.table(), "TABLE", null, null));
        ColumnKey key = new ColumnKey(table, column);
        columns.putIfAbsent(key, new MetadataColumnFact(
                table.catalog(), table.schema(), table.table(), column,
                "UNKNOWN", "UNKNOWN", true, null, "", "", 1));
    }

    private void assignColumnOrdinals(Map<ColumnKey, MetadataColumnFact> columns) {
        Map<TableKey, Integer> ordinals = new LinkedHashMap<>();
        columns.replaceAll((key, fact) -> {
            int ordinal = ordinals.merge(key.table(), 1, Integer::sum);
            return new MetadataColumnFact(
                    fact.catalog(), fact.schema(), fact.tableName(), fact.columnName(),
                    fact.dataType(), fact.columnType(), fact.nullable(), fact.defaultValue(),
                    fact.extra(), fact.generationExpression(), ordinal);
        });
    }

    private MetadataConstraintFact foreignKey(
            ForeignKeyGroup group,
            List<StructuredSqlEvent> values
    ) {
        List<StructuredSqlEvent> ordered = ordered(values);
        TableKey source = tableKey(group.sourceTable());
        TableKey target = tableKey(group.targetTable());
        List<String> sourceColumns = ordered.stream().map(StructuredSqlEvent::sourceColumn).toList();
        List<String> targetColumns = ordered.stream().map(StructuredSqlEvent::targetColumn).toList();
        String identity = "FK|" + source + "|" + sourceColumns + "|" + target + "|" + targetColumns;
        return new MetadataConstraintFact(
                source.catalog(), source.schema(), source.table(), generatedName(identity),
                "FOREIGN_KEY", sourceColumns,
                target.catalog(), target.schema(), target.table(), targetColumns,
                null, null);
    }

    private MetadataIndexFact index(IndexGroup group, List<StructuredSqlEvent> values) {
        List<StructuredSqlEvent> ordered = ordered(values).stream()
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(
                                event -> event.compositePosition() + "|" + event.column(),
                                event -> event,
                                (left, right) -> left,
                                LinkedHashMap::new),
                        map -> List.copyOf(map.values())));
        TableKey table = tableKey(group.table());
        boolean unique = ordered.stream().anyMatch(event -> "TARGET_UNIQUE".equals(event.role()));
        DdlIndexKind kind = DdlIndexKind.valueOf(group.kind());
        boolean primary = kind == DdlIndexKind.PRIMARY_KEY || kind == DdlIndexKind.INLINE_PRIMARY_KEY;
        List<MetadataIndexMemberFact> members = new ArrayList<>();
        int ordinal = 1;
        for (StructuredSqlEvent event : ordered) {
            members.add(MetadataIndexMemberFact.fullColumn(ordinal++, event.column()));
        }
        String identity = "INDEX|" + table + "|" + group.kind() + "|"
                + members.stream().map(MetadataIndexMemberFact::columnName).toList();
        return new MetadataIndexFact(
                table.catalog(), table.schema(), table.table(), generatedName(identity),
                unique, primary, "DECLARATION", true, members);
    }

    private MetadataConstraintFact uniqueConstraint(MetadataIndexFact index) {
        String type = index.primary() ? "PRIMARY_KEY" : "UNIQUE";
        return new MetadataConstraintFact(
                index.catalog(), index.schema(), index.tableName(),
                generatedName(type + "|" + index.catalog() + "|" + index.schema() + "|"
                        + index.tableName() + "|" + index.columns()),
                type, index.columns(), null, null, null, List.of(), null, null);
    }

    private List<StructuredSqlEvent> ordered(List<StructuredSqlEvent> values) {
        return values.stream()
                .sorted(Comparator.comparingInt(StructuredSqlEvent::compositePosition)
                        .thenComparing(StructuredSqlEvent::column)
                        .thenComparing(StructuredSqlEvent::sourceColumn))
                .toList();
    }

    private String generatedName(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return GENERATED_NAME_PREFIX + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private TableKey tableKey(String raw) {
        List<String> parts = identifierParts(raw);
        return switch (parts.size()) {
            case 0 -> new TableKey(null, null, "");
            case 1 -> new TableKey(null, null, parts.get(0));
            case 2 -> new TableKey(null, parts.get(0), parts.get(1));
            default -> new TableKey(
                    String.join(".", parts.subList(0, parts.size() - 2)),
                    parts.get(parts.size() - 2),
                    parts.get(parts.size() - 1));
        };
    }

    private List<String> identifierParts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= raw.length(); index++) {
            if (index == raw.length() || raw.charAt(index) == '.') {
                String cleaned = cleanIdentifier(raw.substring(start, index));
                if (!cleaned.isBlank()) {
                    result.add(cleaned);
                }
                start = index + 1;
            }
        }
        return List.copyOf(result);
    }

    private String cleanIdentifier(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip();
        while (cleaned.length() >= 2 && (cleaned.startsWith("`") && cleaned.endsWith("`")
                || cleaned.startsWith("\"") && cleaned.endsWith("\"")
                || cleaned.startsWith("[") && cleaned.endsWith("]"))) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private <T> List<T> sorted(java.util.Collection<T> values, Comparator<T> comparator) {
        return values.stream().sorted(comparator).toList();
    }

    private String tableIdentity(MetadataTableFact fact) {
        return safe(fact.catalog()) + "|" + safe(fact.schema()) + "|" + fact.tableName();
    }

    private String columnIdentity(MetadataColumnFact fact) {
        return tableIdentity(new MetadataTableFact(
                fact.catalog(), fact.schema(), fact.tableName(), "", null, null)) + "|" + fact.columnName();
    }

    private String constraintIdentity(MetadataConstraintFact fact) {
        return tableIdentity(new MetadataTableFact(
                fact.catalog(), fact.schema(), fact.tableName(), "", null, null)) + "|" + fact.constraintName();
    }

    private String indexIdentity(MetadataIndexFact fact) {
        return tableIdentity(new MetadataTableFact(
                fact.catalog(), fact.schema(), fact.tableName(), "", null, null)) + "|" + fact.indexName();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private record TableKey(String catalog, String schema, String table) {
    }

    private record ColumnKey(TableKey table, String column) {
    }

    private record ForeignKeyGroup(
            String sourceTable,
            String targetTable,
            String statementId,
            long line,
            int size
    ) {
        static ForeignKeyGroup from(StructuredSqlEvent event) {
            return new ForeignKeyGroup(
                    event.sourceTable(), event.targetTable(),
                    event.provenance().sourceStatementId(), event.line(), event.compositeSize());
        }
    }

    private record IndexGroup(
            String table,
            String kind,
            String statementId,
            long line,
            int size
    ) {
        static IndexGroup from(StructuredSqlEvent event) {
            return new IndexGroup(
                    event.table(), event.kind(), event.provenance().sourceStatementId(),
                    event.line(), event.compositeSize());
        }
    }
}
