package com.relationdetector.core.ddl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.DdlCatalogEvent;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;

/**
 * CN: 聚合一批DDL_CATALOG事件并按当前namespace规范化完整表身份；输出可合并的typed snapshot和覆盖缺口。
 * 上游是DDL runner，下游是scan inventory装配。本类不生成relationship或修补parser缺失声明。
 * EN: Aggregates DDL_CATALOG events and qualifies complete table identity against the active namespace. It exposes a
 * mergeable typed snapshot plus coverage gaps without creating relationships or repairing parser omissions.
 */
public final class DdlCatalogInventory {
    private static final String DECLARATION_PARSE_FAILED = "DECLARATION_PARSE_FAILED";
    private final Map<String, MetadataTableFact> tables = new LinkedHashMap<>();
    private final Map<String, MetadataColumnFact> columns = new LinkedHashMap<>();
    private final Map<String, MetadataConstraintFact> constraints = new LinkedHashMap<>();
    private final Map<String, MetadataIndexFact> indexes = new LinkedHashMap<>();
    private final List<String> gaps = new ArrayList<>();

    public void add(
            DdlCatalogEvent event,
            IdentifierRules identifierRules,
            NamespaceContext namespace
    ) {
        if (event == null) {
            return;
        }
        CanonicalIdentifierResolver identifiers = new CanonicalIdentifierResolver(identifierRules);
        NamespaceContext scope = namespace == null ? NamespaceContext.empty() : namespace;
        event.tables().forEach(fact -> putTable(qualify(fact, identifiers, scope)));
        event.columns().forEach(fact -> putColumn(qualify(fact, identifiers, scope)));
        event.constraints().forEach(fact -> putConstraint(qualify(fact, identifiers, scope)));
        event.indexes().forEach(fact -> putIndex(qualify(fact, identifiers, scope)));
        gaps.addAll(event.gaps());
    }

    public void merge(DdlCatalogInventory other) {
        if (other == null) {
            return;
        }
        other.tables.values().forEach(this::putTable);
        other.columns.values().forEach(this::putColumn);
        other.constraints.values().forEach(this::putConstraint);
        other.indexes.values().forEach(this::putIndex);
        gaps.addAll(other.gaps);
    }

    /**
     * CN: 记录一个属于完整DDL声明范围但未能解析的声明；下游只用该稳定缺口降低inventory完整性，
     * 不在此处暴露异常文本或构造目录事实。
     * EN: Records a declaration inside the complete DDL scope that could not be parsed. Downstream assembly uses
     * this stable gap only to lower inventory completeness without exposing exception text or inventing catalog facts.
     */
    public void recordDeclarationParseFailure() {
        gaps.add(DECLARATION_PARSE_FAILED);
    }

    public MetadataSnapshot snapshot() {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        snapshot.tableFacts().addAll(sorted(tables));
        snapshot.columnFacts().addAll(sorted(columns));
        snapshot.constraintFacts().addAll(sorted(constraints));
        snapshot.indexFacts().addAll(sorted(indexes));
        for (MetadataTableFact fact : snapshot.tableFacts()) {
            snapshot.tables().add(tableId(fact.catalog(), fact.schema(), fact.tableName()));
        }
        return snapshot;
    }

    public List<String> gaps() {
        Set<String> result = new LinkedHashSet<>(gaps);
        columns.values().forEach(column -> requireTable(
                result, column.catalog(), column.schema(), column.tableName(), "COLUMN_OWNER"));
        constraints.values().forEach(constraint -> validateConstraint(result, constraint));
        indexes.values().forEach(index -> validateIndex(result, index));
        return List.copyOf(result);
    }

    public boolean empty() {
        return tables.isEmpty() && columns.isEmpty() && constraints.isEmpty() && indexes.isEmpty();
    }

    private MetadataTableFact qualify(
            MetadataTableFact fact,
            CanonicalIdentifierResolver identifiers,
            NamespaceContext namespace
    ) {
        TableId table = identifiers.resolve(tableId(fact.catalog(), fact.schema(), fact.tableName()), namespace);
        return new MetadataTableFact(
                table.catalog(), table.schema(), table.tableName(),
                fact.tableType(), fact.engine(), fact.comment());
    }

    private MetadataColumnFact qualify(
            MetadataColumnFact fact,
            CanonicalIdentifierResolver identifiers,
            NamespaceContext namespace
    ) {
        TableId table = identifiers.resolve(tableId(fact.catalog(), fact.schema(), fact.tableName()), namespace);
        return new MetadataColumnFact(
                table.catalog(), table.schema(), table.tableName(), fact.columnName(),
                fact.dataType(), fact.columnType(), fact.nullable(), fact.defaultValue(),
                fact.extra(), fact.generationExpression(), fact.ordinalPosition());
    }

    private MetadataConstraintFact qualify(
            MetadataConstraintFact fact,
            CanonicalIdentifierResolver identifiers,
            NamespaceContext namespace
    ) {
        TableId table = identifiers.resolve(tableId(fact.catalog(), fact.schema(), fact.tableName()), namespace);
        TableId referenced = fact.referencedTable() == null || fact.referencedTable().isBlank()
                ? null
                : identifiers.resolve(tableId(
                        fact.referencedCatalog(), fact.referencedSchema(), fact.referencedTable()), namespace);
        return new MetadataConstraintFact(
                table.catalog(), table.schema(), table.tableName(),
                fact.constraintName(), fact.constraintType(), fact.columns(),
                referenced == null ? null : referenced.catalog(),
                referenced == null ? null : referenced.schema(),
                referenced == null ? null : referenced.tableName(),
                fact.referencedColumns(), fact.updateRule(), fact.deleteRule());
    }

    private MetadataIndexFact qualify(
            MetadataIndexFact fact,
            CanonicalIdentifierResolver identifiers,
            NamespaceContext namespace
    ) {
        TableId table = identifiers.resolve(tableId(fact.catalog(), fact.schema(), fact.tableName()), namespace);
        List<MetadataIndexMemberFact> members = fact.members().stream()
                .map(member -> new MetadataIndexMemberFact(
                        member.ordinal(), member.kind(), member.columnName(),
                        member.expression(), member.prefixLength()))
                .toList();
        return new MetadataIndexFact(
                table.catalog(), table.schema(), table.tableName(), fact.indexName(),
                fact.unique(), fact.primary(), fact.indexType(), fact.visible(), members);
    }

    private void putTable(MetadataTableFact fact) {
        put(tables, tableKey(fact.catalog(), fact.schema(), fact.tableName()), fact, "table");
    }

    private void putColumn(MetadataColumnFact fact) {
        put(columns, tableKey(fact.catalog(), fact.schema(), fact.tableName()) + "|" + fact.columnName(),
                fact, "column");
    }

    private void putConstraint(MetadataConstraintFact fact) {
        put(constraints, tableKey(fact.catalog(), fact.schema(), fact.tableName()) + "|" + fact.constraintName(),
                fact, "constraint");
    }

    private void putIndex(MetadataIndexFact fact) {
        put(indexes, tableKey(fact.catalog(), fact.schema(), fact.tableName()) + "|" + fact.indexName(),
                fact, "index");
    }

    private void validateConstraint(Set<String> result, MetadataConstraintFact constraint) {
        requireTable(result, constraint.catalog(), constraint.schema(), constraint.tableName(), "CONSTRAINT_OWNER");
        for (String column : constraint.columns()) {
            requireColumn(result, constraint.catalog(), constraint.schema(),
                    constraint.tableName(), column, "CONSTRAINT_COLUMN");
        }
        if (constraint.referencedTable() == null || constraint.referencedTable().isBlank()) {
            return;
        }
        requireTable(result, constraint.referencedCatalog(), constraint.referencedSchema(),
                constraint.referencedTable(), "FOREIGN_KEY_TARGET");
        for (String column : constraint.referencedColumns()) {
            requireColumn(result, constraint.referencedCatalog(), constraint.referencedSchema(),
                    constraint.referencedTable(), column, "FOREIGN_KEY_TARGET_COLUMN");
        }
    }

    private void validateIndex(Set<String> result, MetadataIndexFact index) {
        requireTable(result, index.catalog(), index.schema(), index.tableName(), "INDEX_OWNER");
        for (MetadataIndexMemberFact member : index.members()) {
            if (member.columnName() == null || member.columnName().isBlank()) {
                continue;
            }
            requireColumn(result, index.catalog(), index.schema(),
                    index.tableName(), member.columnName(), "INDEX_COLUMN");
        }
    }

    private void requireTable(
            Set<String> result,
            String catalog,
            String schema,
            String table,
            String kind
    ) {
        String key = tableKey(catalog, schema, table);
        if (!tables.containsKey(key)) {
            result.add("MISSING_" + kind + "|" + key);
        }
    }

    private void requireColumn(
            Set<String> result,
            String catalog,
            String schema,
            String table,
            String column,
            String kind
    ) {
        String key = tableKey(catalog, schema, table) + "|" + column;
        if (!columns.containsKey(key)) {
            result.add("MISSING_" + kind + "|" + key);
        }
    }

    private <T> void put(Map<String, T> target, String key, T value, String kind) {
        T existing = target.putIfAbsent(key, value);
        if (existing != null && !existing.equals(value)) {
            gaps.add("CONFLICTING_" + kind.toUpperCase(java.util.Locale.ROOT) + "|" + key);
        }
    }

    private <T> List<T> sorted(Map<String, T> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .map(Map.Entry::getValue)
                .toList();
    }

    private TableId tableId(String catalog, String schema, String table) {
        return new TableId(catalog, schema, table, table);
    }

    private String tableKey(String catalog, String schema, String table) {
        return safe(catalog) + "|" + safe(schema) + "|" + safe(table);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
