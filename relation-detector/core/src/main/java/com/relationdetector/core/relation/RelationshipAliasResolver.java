package com.relationdetector.core.relation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.CanonicalEndpointKeyProvider;
import com.relationdetector.core.identity.NamespaceContext;

/**
 * CN: 从 typed rowset、projection 与当前 namespace 构建 statement-scope alias/column resolution；上游是
 * structured relationship extractor，下游是 candidate factory，本类不读取 raw SQL、不猜测物理表，也不跨 scope 复用 alias。
 * EN: Resolves statement-scoped rowset and projection aliases from typed events and the active namespace for the
 * relationship extractor and candidate factory. It never reads raw SQL, guesses physical tables, or reuses aliases
 * across scopes.
 */
abstract class RelationshipAliasResolver {
    private final CanonicalIdentifierResolver identifiers;
    private final CanonicalEndpointKeyProvider endpointKeys;
    private final NamespaceContext namespace;

    protected RelationshipAliasResolver(IdentifierRules identifierRules, NamespaceContext namespace) {
        this.identifiers = new CanonicalIdentifierResolver(identifierRules);
        this.namespace = namespace == null ? NamespaceContext.empty() : namespace;
        this.endpointKeys = new CanonicalEndpointKeyProvider(identifierRules, this.namespace);
    }

    protected AliasIndex rowsetAliases(List<StructuredSqlEvent> events, Set<String> ignoredRowsets) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        Map<String, List<AliasBinding>> bindings = new LinkedHashMap<>();
        Set<String> ambiguousAliases = new HashSet<>();
        Map<String, TableId> triggerPseudoTables = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET
                    && !event.targetTable().isBlank()) {
                TableId tableId = tableId(event.targetTable());
                triggerPseudoTables.put(normalize(event.name()), tableId);
                triggerPseudoTables.put(normalize(event.alias()), tableId);
            }
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                TableId tableId = tableId(event.targetTable());
                putAlias(aliases, bindings, ambiguousAliases, event.name(), tableId, event.line());
                continue;
            }
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) continue;
            String qualified = event.qualifiedTable();
            String table = event.table();
            String alias = event.alias();
            TableId pseudoTable = triggerPseudoTables.get(normalize(qualified.isBlank() ? table : qualified));
            if (pseudoTable != null) {
                putAlias(aliases, bindings, ambiguousAliases, qualified, pseudoTable, event.line());
                putAlias(aliases, bindings, ambiguousAliases, table, pseudoTable, event.line());
                putAlias(aliases, bindings, ambiguousAliases, alias, pseudoTable, event.line());
                continue;
            }
            String rowsetIdentity = qualified.isBlank() ? table : qualified;
            if (ignoredRowsets.contains(normalize(rowsetIdentity))) continue;
            TableId tableId = tableId(rowsetIdentity);
            putAlias(aliases, bindings, ambiguousAliases, qualified, tableId, event.line());
            putAlias(aliases, bindings, ambiguousAliases, table, tableId, event.line());
            if (!alias.isBlank()) putAlias(aliases, bindings, ambiguousAliases, alias, tableId, event.line());
        }
        return new AliasIndex(aliases, bindings);
    }

    private void putAlias(Map<String, TableId> aliases, Map<String, List<AliasBinding>> bindings,
            Set<String> ambiguousAliases, String alias, TableId tableId, long line) {
        String key = normalize(alias);
        if (key.isBlank()) return;
        bindings.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new AliasBinding(tableId, line));
        if (ambiguousAliases.contains(key)) return;
        TableId existing = aliases.get(key);
        if (existing == null) {
            aliases.put(key, tableId);
            return;
        }
        if (sameTable(existing, tableId)) return;
        aliases.remove(key);
        ambiguousAliases.add(key);
    }

    protected Map<ColumnKey, ColumnRef> projectedColumns(List<StructuredSqlEvent> events,
            AliasIndex aliases, Set<String> ignoredRowsets) {
        Map<ColumnKey, ColumnRef> projections = new LinkedHashMap<>();
        boolean changed;
        do {
            changed = false;
            for (StructuredSqlEvent event : events) {
                if (event.type() != StructuredParseEventType.PROJECTION_ITEM || !isDirectValueProjection(event)) continue;
                String outputAlias = event.outputAlias();
                String outputColumn = event.outputColumn();
                if (outputAlias.isBlank() || outputColumn.isBlank()) continue;
                ColumnRef source = firstResolvableSource(event, aliases, projections);
                if (source == null || isIgnored(source.table(), ignoredRowsets)) continue;
                changed |= putProjection(projections, outputAlias, outputColumn, source);
            }
            changed |= copyIgnoredRowsetAliases(events, ignoredRowsets, projections);
        } while (changed);
        return projections;
    }

    private boolean isDirectValueProjection(StructuredSqlEvent event) {
        return event.expression().transformType() == com.relationdetector.contracts.Enums.LineageTransformType.DIRECT
                && event.expression().flowKind() == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE;
    }

    private ColumnRef firstResolvableSource(StructuredSqlEvent event, AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections) {
        List<String> sourceAliases = event.expression().sourceAliases();
        List<String> sourceColumns = event.expression().sourceColumns();
        int count = Math.min(sourceAliases.size(), sourceColumns.size());
        for (int index = 0; index < count; index++) {
            ColumnRef source = resolve(sourceAliases.get(index), sourceColumns.get(index),
                    aliases, projections, event.line());
            if (source != null) return source;
        }
        return null;
    }

    private boolean copyIgnoredRowsetAliases(List<StructuredSqlEvent> events, Set<String> ignoredRowsets,
            Map<ColumnKey, ColumnRef> projections) {
        boolean changed = false;
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) continue;
            String table = event.table();
            String qualified = event.qualifiedTable();
            String alias = event.alias();
            String rowsetIdentity = qualified.isBlank() ? table : qualified;
            if (alias.isBlank() || !ignoredRowsets.contains(normalize(rowsetIdentity))) {
                continue;
            }
            for (Map.Entry<ColumnKey, ColumnRef> entry : List.copyOf(projections.entrySet())) {
                if (entry.getKey().alias().equals(normalize(table))
                        || entry.getKey().alias().equals(normalize(qualified))) {
                    changed |= putProjection(projections, alias, entry.getKey().column(), entry.getValue());
                }
            }
        }
        return changed;
    }

    private boolean putProjection(Map<ColumnKey, ColumnRef> projections, String alias,
            String column, ColumnRef source) {
        ColumnKey key = columnKey(alias, column);
        if (key.alias().isBlank() || key.column().isBlank() || projections.containsKey(key)) return false;
        projections.put(key, source);
        return true;
    }

    protected Set<String> ignoredRowsets(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> ignored = new HashSet<>(localTempRowsets(statement, events));
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                addIgnored(ignored, event.name());
                continue;
            }
            if (event.type() == StructuredParseEventType.IGNORED_ROWSET
                    || event.type() == StructuredParseEventType.CTE_DECLARATION
                    || event.type() == StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                addIgnored(ignored, event.name());
                addIgnored(ignored, event.table());
                addIgnored(ignored, event.qualifiedTable());
            }
        }
        return ignored;
    }

    protected Set<String> localTempRowsets(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> local = new HashSet<>();
        for (String table : stringList(statement.attributes().get("localTempTables"))) {
            addIgnored(local, table);
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                continue;
            }
            addIgnored(local, event.name());
            addIgnored(local, event.table());
            addIgnored(local, event.qualifiedTable());
        }
        return local;
    }

    private void addIgnored(Set<String> ignored, String raw) {
        if (raw != null && !raw.isBlank()) ignored.add(normalize(raw));
    }

    protected boolean isIgnored(TableId table, Set<String> ignoredRowsets) {
        if (isSystemSchema(table.schema())) return true;
        if ((table.catalog() != null && !table.catalog().isBlank())
                || (table.schema() != null && !table.schema().isBlank())) {
            return ignoredRowsets.contains(normalize(table.displayName()));
        }
        return ignoredRowsets.contains(normalize(table.tableName()));
    }

    protected boolean isIgnoredRawRowset(String rowset, Set<String> ignoredRowsets) {
        return rowset != null && !rowset.isBlank() && ignoredRowsets.contains(normalize(rowset));
    }

    private boolean isSystemSchema(String schema) {
        return schema != null && !schema.isBlank()
                && Set.of("information_schema", "performance_schema", "mysql", "sys", "pg_catalog", "pg_toast")
                .contains(normalize(schema));
    }

    protected ColumnRef resolveInSubqueryColumn(StructuredSqlEvent event, AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections) {
        ColumnRef byAlias = resolve(event.right().alias(), event.right().column(), aliases, projections, event.line());
        return byAlias != null ? byAlias
                : resolveTupleInSubqueryColumn(event, event.right().alias(), event.right().column(), aliases, projections);
    }

    protected ColumnRef resolveTupleInSubqueryColumn(StructuredSqlEvent event, String alias, String column,
            AliasIndex aliases, Map<ColumnKey, ColumnRef> projections) {
        ColumnRef byAlias = resolve(alias, column, aliases, projections, event.line());
        if (byAlias != null) return byAlias;
        String table = event.innerTable();
        return table.isBlank() || column.isBlank() ? null : column(tableId(table), column);
    }

    protected ColumnRef resolve(String alias, String column, AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections, long eventLine) {
        if (column.isBlank()) return null;
        ColumnRef projected = projections.get(columnKey(alias, column));
        if (projected != null) return projected;
        TableId table = aliases.resolve(alias, eventLine);
        return table == null ? null : column(table, column);
    }

    protected ColumnRef resolveWithFallbackTable(String alias, String column, String fallbackTable,
            AliasIndex aliases, Map<ColumnKey, ColumnRef> projections, long eventLine) {
        ColumnRef resolved = resolve(alias, column, aliases, projections, eventLine);
        return resolved != null || fallbackTable.isBlank() || column.isBlank()
                ? resolved : resolve(fallbackTable, column, aliases, projections, eventLine);
    }

    protected TableId tableId(String qualified) {
        return identifiers.resolveQualified(qualified, namespace);
    }

    private ColumnRef column(TableId table, String name) {
        return ColumnRef.of(table, clean(name));
    }

    protected EvidenceSourceType evidenceSourceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            default -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    protected String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`"))
                || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    protected String normalize(String value) {
        return identifiers.normalize(clean(value));
    }

    protected List<String> stringList(Object value) {
        return value instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
    }

    protected record ColumnKey(String alias, String column) {}

    private ColumnKey columnKey(String alias, String column) {
        return new ColumnKey(normalize(alias), normalize(column));
    }

    protected final class AliasIndex {
        private final Map<String, TableId> uniqueAliases;
        private final Map<String, List<AliasBinding>> bindings;

        private AliasIndex(Map<String, TableId> uniqueAliases, Map<String, List<AliasBinding>> bindings) {
            this.uniqueAliases = uniqueAliases;
            this.bindings = bindings;
        }

        private TableId resolve(String alias, long eventLine) {
            String key = normalize(alias);
            TableId unique = uniqueAliases.get(key);
            if (unique != null) return unique;
            List<AliasBinding> candidates = bindings.getOrDefault(key, List.of()).stream()
                    .filter(binding -> binding.line() <= eventLine).toList();
            if (candidates.isEmpty()) {
                List<AliasBinding> allBindings = bindings.getOrDefault(key, List.of());
                return allBindings.size() == 1 ? allBindings.get(0).tableId() : null;
            }
            long nearestLine = candidates.stream().mapToLong(AliasBinding::line).max().orElse(Long.MIN_VALUE);
            Map<String, TableId> nearestByIdentity = new LinkedHashMap<>();
            candidates.stream()
                    .filter(binding -> binding.line() == nearestLine)
                    .map(AliasBinding::tableId)
                    .forEach(table -> nearestByIdentity.putIfAbsent(tableKey(table), table));
            List<TableId> nearestTables = List.copyOf(nearestByIdentity.values());
            return nearestTables.size() == 1 ? nearestTables.get(0) : null;
        }
    }

    protected boolean sameTable(TableId left, TableId right) {
        return left != null && right != null && tableKey(left).equals(tableKey(right));
    }

    protected String endpointIdentityKey(Endpoint endpoint) {
        return endpointKeys.factKey(endpoint);
    }

    private String tableKey(TableId table) {
        return identifiers.tableKey(table, namespace);
    }

    private record AliasBinding(TableId tableId, long line) {}
}
