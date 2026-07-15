package com.relationdetector.core.lineage;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.identity.AliasSymbolTable;

/** Builds projection anchors and physical alias inventory from typed events. */
final class ProjectionTraceInventory {
    private ProjectionTraceInventory() {
    }

    static Map<String, ProjectionAnchor> wildcardAnchors(
            List<StructuredSqlEvent> events,
            AliasSymbolTable aliases,
            Map<String, TableId> physicalAliases,
            Set<String> ignoredRowsets
    ) {
        Map<String, String> logicalRowsets = logicalRowsets(events, aliases, ignoredRowsets);
        Map<String, ProjectionAnchor> anchors = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        Set<String> wildcardOutputs = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.PROJECTION_ITEM && "*".equals(event.outputColumn())) {
                wildcardOutputs.add(aliases.normalizeIdentifier(event.outputAlias()));
            }
        }
        Map<String, LinkedHashSet<TableId>> seedSources = new LinkedHashMap<>();
        Set<String> invalidSeeds = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.PROJECTION_ITEM || "*".equals(event.outputColumn())) continue;
            String outputKey = aliases.normalizeIdentifier(event.outputAlias());
            if (outputKey.isBlank() || wildcardOutputs.contains(outputKey)) continue;
            int count = Math.min(event.expression().sourceAliases().size(), event.expression().sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String sourceColumn = event.expression().sourceColumns().get(index);
                if (sourceColumn.isBlank() || "*".equals(sourceColumn)) continue;
                TableId physical = physicalAliases.get(
                        aliases.normalizeIdentifier(event.expression().sourceAliases().get(index)));
                if (physical == null) invalidSeeds.add(outputKey);
                else seedSources.computeIfAbsent(outputKey, ignored -> new LinkedHashSet<>()).add(physical);
            }
        }
        for (Map.Entry<String, LinkedHashSet<TableId>> entry : seedSources.entrySet()) {
            if (!invalidSeeds.contains(entry.getKey()) && entry.getValue().size() == 1) {
                bindAnchor(anchors, ambiguous, aliases, entry.getKey(), entry.getValue().iterator().next(), false);
            }
        }
        boolean changed;
        do {
            changed = false;
            for (StructuredSqlEvent event : events) {
                if (event.type() != StructuredParseEventType.PROJECTION_ITEM
                        || !"*".equals(event.outputColumn())) continue;
                int count = Math.min(event.expression().sourceAliases().size(), event.expression().sourceColumns().size());
                for (int index = 0; index < count; index++) {
                    if (!"*".equals(event.expression().sourceColumns().get(index))) continue;
                    String sourceKey = aliases.normalizeIdentifier(event.expression().sourceAliases().get(index));
                    TableId anchor = physicalAliases.get(sourceKey);
                    if (anchor == null && anchors.get(sourceKey) != null) anchor = anchors.get(sourceKey).table();
                    ProjectionAnchor logical = anchors.get(logicalRowsets.get(sourceKey));
                    if (anchor == null && logical != null) anchor = logical.table();
                    if (anchor != null) {
                        changed |= bindAnchor(anchors, ambiguous, aliases, event.outputAlias(), anchor, true);
                    }
                }
            }
        } while (changed);
        return anchors;
    }

    static Map<String, TableId> physicalAliases(
            List<StructuredSqlEvent> events,
            AliasSymbolTable aliases,
            Set<String> ignoredRowsets
    ) {
        Map<String, TableId> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        Map<String, TableId> pseudoTables = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET && !event.targetTable().isBlank()) {
                TableId table = aliases.resolveQualified(event.targetTable());
                pseudoTables.put(aliases.normalizeIdentifier(event.name()), table);
                pseudoTables.put(aliases.normalizeIdentifier(event.alias()), table);
            }
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                String qualified = event.targetTable().isBlank() ? event.qualifiedTable() : event.targetTable();
                if (!qualified.isBlank()) {
                    TableId table = aliases.resolveQualified(qualified);
                    bindAlias(result, ambiguous, aliases, event.name(), table);
                    bindAlias(result, ambiguous, aliases, event.alias(), table);
                }
                continue;
            }
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE
                    && event.type() != StructuredParseEventType.WRITE_TARGET) continue;
            String qualified = event.qualifiedTable().isBlank() ? event.table() : event.qualifiedTable();
            if (qualified.isBlank()) continue;
            TableId table = pseudoTables.get(aliases.normalizeIdentifier(qualified));
            if (table == null && ignored(qualified, ignoredRowsets, aliases)) continue;
            if (table == null) table = aliases.resolveQualified(qualified);
            bindAlias(result, ambiguous, aliases, qualified, table);
            bindAlias(result, ambiguous, aliases, event.table(), table);
            bindAlias(result, ambiguous, aliases, event.alias(), table);
        }
        return result;
    }

    static Set<String> declaredKeys(List<StructuredSqlEvent> events, AliasSymbolTable aliases) {
        Set<String> result = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.PROJECTION_ITEM
                    && !event.outputAlias().isBlank() && !event.outputColumn().isBlank()
                    && !"*".equals(event.outputColumn())) {
                result.add(outputKey(aliases, event.outputAlias(), event.outputColumn()));
            }
        }
        return Set.copyOf(result);
    }

    static String key(AliasSymbolTable aliases, String alias, String column, LineageFlowKind flowKind) {
        return aliases.normalizeIdentifier(clean(alias)) + "."
                + aliases.normalizeIdentifier(clean(column)) + "|" + flowKind.name();
    }

    static String outputKey(AliasSymbolTable aliases, String alias, String column) {
        return aliases.normalizeIdentifier(clean(alias)) + "." + aliases.normalizeIdentifier(clean(column));
    }

    static ProjectionKey parseKey(String key) {
        int separator = key.lastIndexOf('|');
        String endpoint = separator < 0 ? key : key.substring(0, separator);
        LineageFlowKind flow = separator < 0 ? LineageFlowKind.VALUE : flowKind(key.substring(separator + 1));
        int dot = endpoint.lastIndexOf('.');
        return dot < 0 ? new ProjectionKey(endpoint, "", flow)
                : new ProjectionKey(endpoint.substring(0, dot), endpoint.substring(dot + 1), flow);
    }

    static ColumnRef column(AliasSymbolTable aliases, TableId table, String name) {
        String clean = clean(name);
        return new ColumnRef(table, clean, aliases.normalizeIdentifier(clean), null, true);
    }

    private static Map<String, String> logicalRowsets(
            List<StructuredSqlEvent> events, AliasSymbolTable aliases, Set<String> ignoredRowsets) {
        Map<String, String> result = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) continue;
            String qualified = event.qualifiedTable().isBlank() ? event.table() : event.qualifiedTable();
            if (!ignored(qualified, ignoredRowsets, aliases)) continue;
            String tableKey = aliases.normalizeIdentifier(qualified);
            result.put(tableKey, tableKey);
            String aliasKey = aliases.normalizeIdentifier(event.alias());
            if (!aliasKey.isBlank()) result.put(aliasKey, tableKey);
        }
        return result;
    }

    private static boolean bindAnchor(Map<String, ProjectionAnchor> anchors, Set<String> ambiguous,
            AliasSymbolTable aliases, String name, TableId table, boolean wildcardDerived) {
        String key = aliases.normalizeIdentifier(name);
        if (key.isBlank() || ambiguous.contains(key)) return false;
        ProjectionAnchor existing = anchors.get(key);
        if (existing == null) {
            anchors.put(key, new ProjectionAnchor(table, wildcardDerived));
            return true;
        }
        if (!aliases.sameTable(existing.table(), table)) {
            anchors.remove(key);
            ambiguous.add(key);
            return true;
        }
        if (wildcardDerived && !existing.wildcardDerived()) {
            anchors.put(key, new ProjectionAnchor(table, true));
            return true;
        }
        return false;
    }

    private static void bindAlias(Map<String, TableId> values, Set<String> ambiguous,
            AliasSymbolTable aliases, String name, TableId table) {
        String key = aliases.normalizeIdentifier(name);
        if (key.isBlank() || ambiguous.contains(key)) return;
        TableId existing = values.get(key);
        if (existing != null && !aliases.sameTable(existing, table)) {
            values.remove(key);
            ambiguous.add(key);
            return;
        }
        values.put(key, table);
    }

    private static boolean ignored(String rowset, Set<String> ignored, AliasSymbolTable aliases) {
        return ignored.contains(aliases.normalizeIdentifier(rowset));
    }

    private static LineageFlowKind flowKind(String value) {
        try {
            return LineageFlowKind.valueOf(value);
        } catch (RuntimeException ignored) {
            return LineageFlowKind.VALUE;
        }
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`"))
                || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }
}

record ProjectionKey(String alias, String column, LineageFlowKind flowKind) {
    boolean matches(String rowset, AliasSymbolTable aliases) {
        return alias.equals(aliases.normalizeIdentifier(rowset));
    }
}

record ProjectionAnchor(TableId table, boolean wildcardDerived) {
}
