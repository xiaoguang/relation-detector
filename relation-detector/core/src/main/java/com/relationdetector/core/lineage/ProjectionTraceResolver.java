package com.relationdetector.core.lineage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.identity.AliasSymbolTable;
import com.relationdetector.core.lineage.model.ProjectionTrace;

/**
 * Resolves CTE / derived-table projection aliases from structured parser events.
 */
final class ProjectionTraceResolver {
    private final Map<String, ProjectionTrace> traces;
    private final AliasSymbolTable aliases;
    private final Map<String, TableId> physicalAliases;
    private final Map<String, ProjectionAnchor> wildcardAnchors;
    private final Set<String> declaredProjectionKeys;

    private ProjectionTraceResolver(
            Map<String, ProjectionTrace> traces,
            AliasSymbolTable aliases,
            Map<String, TableId> physicalAliases,
            Map<String, ProjectionAnchor> wildcardAnchors,
            Set<String> declaredProjectionKeys
    ) {
        this.traces = Map.copyOf(traces);
        this.aliases = aliases;
        this.physicalAliases = Map.copyOf(physicalAliases);
        this.wildcardAnchors = Map.copyOf(wildcardAnchors);
        this.declaredProjectionKeys = Set.copyOf(declaredProjectionKeys);
    }

    static ProjectionTraceResolver fromEvents(
            List<StructuredSqlEvent> events,
            AliasSymbolTable aliases,
            Set<String> ignoredRowsets
    ) {
        Map<String, ProjectionTrace> projections = new LinkedHashMap<>();
        Set<String> declaredProjectionKeys = declaredProjectionKeys(events, aliases);
        Map<String, TableId> physicalAliases = physicalAliases(events, aliases, ignoredRowsets);
        Map<String, ProjectionAnchor> wildcardAnchors = wildcardProjectionAnchors(
                events, aliases, physicalAliases, ignoredRowsets);
        boolean changed;
        do {
            changed = false;
            for (StructuredSqlEvent event : events) {
                if (event.type() != StructuredParseEventType.PROJECTION_ITEM) {
                    continue;
                }
                String outputAlias = event.outputAlias();
                String outputColumn = event.outputColumn();
                if (outputAlias.isBlank() || outputColumn.isBlank()) {
                    continue;
                }
                if ("*".equals(outputColumn)) {
                    changed |= copyWildcardProjection(
                            event, outputAlias, projections, ignoredRowsets, aliases);
                    continue;
                }
                List<SourceResolution> resolved = sourceEndpoints(
                        event, aliases, physicalAliases, wildcardAnchors,
                        declaredProjectionKeys, projections, ignoredRowsets);
                for (SourceResolution resolution : resolved) {
                    if (resolution.sources().isEmpty()) {
                        continue;
                    }
                    Endpoint projection = Endpoint.column(column(
                            aliases, aliases.resolveQualified(outputAlias), outputColumn));
                    changed |= putProjection(
                            projections,
                            outputAlias,
                            outputColumn,
                            new ProjectionTrace(
                                    projection,
                                    resolution.sources().stream().distinct().toList(),
                                    effectiveTransform(
                                            event.expression().transformType().name(),
                                            resolution.transforms(), resolution.flowKind()),
                                    resolution.flowKind()),
                            ignoredRowsets,
                            aliases);
                }
            }
            changed |= copyIgnoredRowsetAliases(events, ignoredRowsets, projections, aliases);
        } while (changed);
        return new ProjectionTraceResolver(
                projections, aliases, physicalAliases, wildcardAnchors, declaredProjectionKeys);
    }

    private static boolean copyWildcardProjection(
            StructuredSqlEvent event,
            String outputAlias,
            Map<String, ProjectionTrace> projections,
            Set<String> ignoredRowsets,
            AliasSymbolTable aliases
    ) {
        boolean changed = false;
        int count = Math.min(
                event.expression().sourceAliases().size(),
                event.expression().sourceColumns().size());
        for (int index = 0; index < count; index++) {
            if (!"*".equals(event.expression().sourceColumns().get(index))) {
                continue;
            }
            String sourceAlias = event.expression().sourceAliases().get(index);
            for (Map.Entry<String, ProjectionTrace> entry : List.copyOf(projections.entrySet())) {
                ProjectionKey key = parseProjectionKey(entry.getKey());
                if (key.matches(sourceAlias, aliases)) {
                    changed |= putProjection(
                            projections, outputAlias, key.column(), entry.getValue(),
                            ignoredRowsets, aliases);
                }
            }
        }
        return changed;
    }

    Optional<ProjectionTrace> resolve(String alias, String column) {
        ProjectionTrace value = traces.get(projectionKey(aliases, alias, column, LineageFlowKind.VALUE));
        if (value != null) {
            return Optional.of(value);
        }
        return Optional.ofNullable(traces.get(projectionKey(aliases, alias, column, LineageFlowKind.CONTROL)));
    }

    List<SourceResolution> resolveSources(
            StructuredSqlEvent event,
            AliasSymbolTable aliases,
            Set<String> ignoredRowsets
    ) {
        return sourceEndpoints(
                event, aliases, physicalAliases, wildcardAnchors,
                declaredProjectionKeys, traces, ignoredRowsets);
    }

    private static List<SourceResolution> sourceEndpoints(
            StructuredSqlEvent event,
            AliasSymbolTable aliases,
            Map<String, TableId> physicalAliases,
            Map<String, ProjectionAnchor> wildcardAnchors,
            Set<String> declaredProjectionKeys,
            Map<String, ProjectionTrace> projections,
            Set<String> ignoredRowsets
    ) {
        List<String> sourceAliases = event.expression().sourceAliases();
        List<String> sourceColumns = event.expression().sourceColumns();
        Map<LineageFlowKind, List<Endpoint>> endpoints = new LinkedHashMap<>();
        Map<LineageFlowKind, List<LineageTransformType>> transforms = new LinkedHashMap<>();
        LineageFlowKind eventFlow = event.expression().flowKind();
        int count = Math.min(sourceAliases.size(), sourceColumns.size());
        for (int index = 0; index < count; index++) {
            String sourceAlias = sourceAliases.get(index);
            String sourceColumn = sourceColumns.get(index);
            ProjectionAnchor wildcardAnchor = wildcardAnchors.get(
                    aliases.normalizeIdentifier(sourceAlias));
            if (event.type() != StructuredParseEventType.PROJECTION_ITEM
                    && wildcardAnchor != null
                    && wildcardAnchor.wildcardDerived()
                    && !sourceColumn.isBlank()
                    && !"*".equals(sourceColumn)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(column(aliases, wildcardAnchor.table(), sourceColumn)));
                transforms.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(LineageTransformType.DIRECT);
                continue;
            }
            List<ProjectionTrace> variants = projectionVariants(projections, aliases, sourceAlias, sourceColumn);
            if (!variants.isEmpty()) {
                for (ProjectionTrace projection : variants) {
                    LineageFlowKind resolvedFlow = eventFlow == LineageFlowKind.CONTROL
                            ? LineageFlowKind.CONTROL
                            : projection.flowKind();
                    endpoints.computeIfAbsent(resolvedFlow, ignored -> new ArrayList<>())
                            .addAll(projection.sources());
                    transforms.computeIfAbsent(resolvedFlow, ignored -> new ArrayList<>())
                            .add(projection.transform());
                }
                continue;
            }
            if (declaredProjectionKeys.contains(projectionOutputKey(aliases, sourceAlias, sourceColumn))) {
                continue;
            }
            TableId table = physicalAliases.get(aliases.normalizeIdentifier(sourceAlias));
            if (table != null && !sourceColumn.isBlank()
                    && !isIgnoredRowsetTable(table, ignoredRowsets, aliases)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(column(aliases, table, sourceColumn)));
                transforms.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(LineageTransformType.DIRECT);
                continue;
            }
            if (wildcardAnchor != null && !sourceColumn.isBlank() && !"*".equals(sourceColumn)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(column(aliases, wildcardAnchor.table(), sourceColumn)));
                transforms.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(LineageTransformType.DIRECT);
                continue;
            }
        }
        List<SourceResolution> result = new ArrayList<>();
        for (LineageFlowKind flow : LineageFlowKind.values()) {
            List<Endpoint> flowEndpoints = endpoints.getOrDefault(flow, List.of()).stream().distinct().toList();
            if (!flowEndpoints.isEmpty()) {
                result.add(new SourceResolution(flowEndpoints, transforms.getOrDefault(flow, List.of()), flow));
            }
        }
        return List.copyOf(result);
    }

    private static List<ProjectionTrace> projectionVariants(
            Map<String, ProjectionTrace> projections,
            AliasSymbolTable aliases,
            String alias,
            String column
    ) {
        List<ProjectionTrace> variants = new ArrayList<>(2);
        for (LineageFlowKind flow : LineageFlowKind.values()) {
            ProjectionTrace projection = projections.get(projectionKey(aliases, alias, column, flow));
            if (projection != null) {
                variants.add(projection);
            }
        }
        return variants;
    }

    private static boolean copyIgnoredRowsetAliases(
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Map<String, ProjectionTrace> projections,
            AliasSymbolTable aliases
    ) {
        boolean changed = false;
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String table = event.table();
            String qualified = event.qualifiedTable();
            String alias = event.alias();
            if (alias.isBlank()
                    || (!ignoredRowsets.contains(aliases.normalizeIdentifier(table))
                    && !ignoredRowsets.contains(aliases.normalizeIdentifier(qualified)))) {
                continue;
            }
            for (Map.Entry<String, ProjectionTrace> entry : List.copyOf(projections.entrySet())) {
                ProjectionKey key = parseProjectionKey(entry.getKey());
                if (key.matches(table, aliases) || key.matches(qualified, aliases)) {
                    changed |= putProjection(
                            projections, alias, key.column(), entry.getValue(), ignoredRowsets, aliases);
                }
            }
        }
        return changed;
    }

    private static boolean putProjection(
            Map<String, ProjectionTrace> projections,
            String alias,
            String column,
            ProjectionTrace projection,
            Set<String> ignoredRowsets,
            AliasSymbolTable aliases
    ) {
        String key = projectionKey(aliases, alias, column, projection.flowKind());
        if (parseProjectionKey(key).alias().isBlank()
                || parseProjectionKey(key).column().isBlank()) {
            return false;
        }
        return putProjectionKey(projections, key, projection, ignoredRowsets);
    }

    private static boolean putProjectionKey(
            Map<String, ProjectionTrace> projections,
            String key,
            ProjectionTrace projection,
            Set<String> ignoredRowsets
    ) {
        ProjectionTrace existing = projections.get(key);
        if (existing == null) {
            projections.put(key, projection);
            return true;
        }
        ProjectionTrace merged = mergeProjection(existing, projection);
        if (!merged.equals(existing)) {
            projections.put(key, merged);
            return true;
        }
        return false;
    }

    /**
     * A projection key may be emitted once for every UNION/UNION ALL branch.
     * Preserve every branch source instead of selecting the first or best one.
     */
    private static ProjectionTrace mergeProjection(ProjectionTrace existing, ProjectionTrace candidate) {
        LinkedHashSet<Endpoint> sources = new LinkedHashSet<>();
        sources.addAll(existing.sources());
        sources.addAll(candidate.sources());
        List<Endpoint> orderedSources = sources.stream()
                .sorted(Comparator.comparing(Endpoint::normalizedKey))
                .toList();
        return new ProjectionTrace(
                existing.projection(),
                orderedSources,
                LineageTransformClassifier.dominant(existing.transform(), candidate.transform()),
                existing.flowKind());
    }

    private static boolean isIgnoredRowsetTable(
            TableId table,
            Set<String> ignoredRowsets,
            AliasSymbolTable aliases
    ) {
        if (table.schema() != null && !table.schema().isBlank()) {
            return ignoredRowsets.contains(aliases.normalizeIdentifier(
                    table.schema() + "." + table.tableName()));
        }
        return ignoredRowsets.contains(aliases.normalizeIdentifier(table.tableName()));
    }

    private static LineageTransformType transform(String value) {
        try {
            return LineageTransformType.valueOf(value);
        } catch (RuntimeException ignored) {
            return LineageTransformType.UNKNOWN_EXPRESSION;
        }
    }

    static LineageTransformType effectiveTransform(
            String eventTransform,
            List<LineageTransformType> sourceTransforms
    ) {
        LineageTransformType transform = transform(eventTransform);
        if (sourceTransforms.contains(LineageTransformType.CUMULATIVE)
                && (transform == LineageTransformType.DIRECT || transform == LineageTransformType.UNKNOWN_EXPRESSION)) {
            return LineageTransformType.CUMULATIVE;
        }
        if (sourceTransforms.contains(LineageTransformType.AGGREGATE)
                && (transform == LineageTransformType.DIRECT
                || transform == LineageTransformType.UNKNOWN_EXPRESSION
                || transform == LineageTransformType.COALESCE
                || transform == LineageTransformType.CASE_WHEN
                || transform == LineageTransformType.ARITHMETIC)) {
            return LineageTransformType.AGGREGATE;
        }
        if (sourceTransforms.contains(LineageTransformType.WINDOW_DERIVED)
                && transform == LineageTransformType.DIRECT) {
            return LineageTransformType.WINDOW_DERIVED;
        }
        return transform;
    }

    static LineageTransformType effectiveTransform(
            String eventTransform,
            List<LineageTransformType> sourceTransforms,
            LineageFlowKind flowKind
    ) {
        if (flowKind == LineageFlowKind.CONTROL) {
            return LineageTransformType.CASE_WHEN;
        }
        LineageTransformType effective = effectiveTransform(eventTransform, sourceTransforms);
        return effective;
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`")) || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static Map<String, ProjectionAnchor> wildcardProjectionAnchors(
            List<StructuredSqlEvent> events,
            AliasSymbolTable aliases,
            Map<String, TableId> physicalAliases,
            Set<String> ignoredRowsets
    ) {
        Map<String, String> logicalRowsets = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String qualified = event.qualifiedTable().isBlank()
                    ? event.table() : event.qualifiedTable();
            if (!isIgnoredRowsetName(qualified, ignoredRowsets, aliases)) {
                continue;
            }
            String tableKey = aliases.normalizeIdentifier(qualified);
            logicalRowsets.put(tableKey, tableKey);
            String aliasKey = aliases.normalizeIdentifier(event.alias());
            if (!aliasKey.isBlank()) {
                logicalRowsets.put(aliasKey, tableKey);
            }
        }

        Map<String, ProjectionAnchor> anchors = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        Set<String> wildcardOutputs = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.PROJECTION_ITEM
                    && "*".equals(event.outputColumn())) {
                wildcardOutputs.add(aliases.normalizeIdentifier(event.outputAlias()));
            }
        }
        Map<String, LinkedHashSet<TableId>> seedSources = new LinkedHashMap<>();
        Set<String> invalidSeeds = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.PROJECTION_ITEM
                    || "*".equals(event.outputColumn())) {
                continue;
            }
            String outputKey = aliases.normalizeIdentifier(event.outputAlias());
            if (outputKey.isBlank() || wildcardOutputs.contains(outputKey)) {
                continue;
            }
            int count = Math.min(
                    event.expression().sourceAliases().size(),
                    event.expression().sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String sourceColumn = event.expression().sourceColumns().get(index);
                if (sourceColumn.isBlank() || "*".equals(sourceColumn)) {
                    continue;
                }
                TableId physical = physicalAliases.get(aliases.normalizeIdentifier(
                        event.expression().sourceAliases().get(index)));
                if (physical == null) {
                    invalidSeeds.add(outputKey);
                } else {
                    seedSources.computeIfAbsent(outputKey, ignored -> new LinkedHashSet<>())
                            .add(physical);
                }
            }
        }
        for (Map.Entry<String, LinkedHashSet<TableId>> entry : seedSources.entrySet()) {
            if (!invalidSeeds.contains(entry.getKey()) && entry.getValue().size() == 1) {
                bindAnchor(anchors, ambiguous, aliases, entry.getKey(),
                        entry.getValue().iterator().next(), false);
            }
        }

        boolean changed;
        do {
            changed = false;
            for (StructuredSqlEvent event : events) {
                if (event.type() != StructuredParseEventType.PROJECTION_ITEM
                        || !"*".equals(event.outputColumn())) {
                    continue;
                }
                int count = Math.min(
                        event.expression().sourceAliases().size(),
                        event.expression().sourceColumns().size());
                for (int index = 0; index < count; index++) {
                    if (!"*".equals(event.expression().sourceColumns().get(index))) {
                        continue;
                    }
                    String sourceKey = aliases.normalizeIdentifier(
                            event.expression().sourceAliases().get(index));
                    TableId anchor = physicalAliases.get(sourceKey);
                    if (anchor == null && anchors.get(sourceKey) != null) {
                        anchor = anchors.get(sourceKey).table();
                    }
                    ProjectionAnchor logicalAnchor = anchors.get(logicalRowsets.get(sourceKey));
                    if (anchor == null && logicalAnchor != null) {
                        anchor = logicalAnchor.table();
                    }
                    if (anchor != null) {
                        changed |= bindAnchor(
                                anchors, ambiguous, aliases, event.outputAlias(), anchor, true);
                    }
                }
            }
        } while (changed);
        return anchors;
    }

    private static boolean bindAnchor(
            Map<String, ProjectionAnchor> anchors,
            Set<String> ambiguous,
            AliasSymbolTable aliases,
            String name,
            TableId table,
            boolean wildcardDerived
    ) {
        String key = aliases.normalizeIdentifier(name);
        if (key.isBlank() || ambiguous.contains(key)) {
            return false;
        }
        ProjectionAnchor existing = anchors.get(key);
        if (existing == null) {
            anchors.put(key, new ProjectionAnchor(table, wildcardDerived));
            return true;
        }
        if (!existing.table().normalizedName().equals(table.normalizedName())) {
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

    private static Map<String, TableId> physicalAliases(
            List<StructuredSqlEvent> events,
            AliasSymbolTable aliases,
            Set<String> ignoredRowsets
    ) {
        Map<String, TableId> result = new LinkedHashMap<>();
        Set<String> ambiguous = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE
                    && event.type() != StructuredParseEventType.WRITE_TARGET) {
                continue;
            }
            String qualified = event.qualifiedTable().isBlank()
                    ? event.table() : event.qualifiedTable();
            if (qualified.isBlank() || isIgnoredRowsetName(qualified, ignoredRowsets, aliases)) {
                continue;
            }
            TableId table = aliases.resolveQualified(qualified);
            bindPhysicalAlias(result, ambiguous, aliases, qualified, table);
            bindPhysicalAlias(result, ambiguous, aliases, event.table(), table);
            bindPhysicalAlias(result, ambiguous, aliases, event.alias(), table);
        }
        return result;
    }

    private static void bindPhysicalAlias(
            Map<String, TableId> aliasesByName,
            Set<String> ambiguous,
            AliasSymbolTable aliases,
            String name,
            TableId table
    ) {
        String key = aliases.normalizeIdentifier(name);
        if (key.isBlank() || ambiguous.contains(key)) {
            return;
        }
        TableId existing = aliasesByName.get(key);
        if (existing != null && !existing.normalizedName().equals(table.normalizedName())) {
            aliasesByName.remove(key);
            ambiguous.add(key);
            return;
        }
        aliasesByName.put(key, table);
    }

    private static boolean isIgnoredRowsetName(
            String rowset,
            Set<String> ignoredRowsets,
            AliasSymbolTable aliases
    ) {
        return ignoredRowsets.contains(aliases.normalizeIdentifier(rowset));
    }

    private static String projectionKey(
            AliasSymbolTable aliases,
            String alias,
            String column,
            LineageFlowKind flowKind
    ) {
        return aliases.normalizeIdentifier(clean(alias)) + "."
                + aliases.normalizeIdentifier(clean(column)) + "|" + flowKind.name();
    }

    private static Set<String> declaredProjectionKeys(
            List<StructuredSqlEvent> events,
            AliasSymbolTable aliases
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.PROJECTION_ITEM
                    || event.outputAlias().isBlank()
                    || event.outputColumn().isBlank()
                    || "*".equals(event.outputColumn())) {
                continue;
            }
            result.add(projectionOutputKey(aliases, event.outputAlias(), event.outputColumn()));
        }
        return Set.copyOf(result);
    }

    private static String projectionOutputKey(
            AliasSymbolTable aliases,
            String alias,
            String column
    ) {
        return aliases.normalizeIdentifier(clean(alias)) + "."
                + aliases.normalizeIdentifier(clean(column));
    }

    private static ProjectionKey parseProjectionKey(String key) {
        int separator = key.lastIndexOf('|');
        String endpoint = separator < 0 ? key : key.substring(0, separator);
        LineageFlowKind flowKind = separator < 0
                ? LineageFlowKind.VALUE
                : flowKind(key.substring(separator + 1));
        int dot = endpoint.lastIndexOf('.');
        if (dot < 0) {
            return new ProjectionKey(endpoint, "", flowKind);
        }
        return new ProjectionKey(endpoint.substring(0, dot), endpoint.substring(dot + 1), flowKind);
    }

    private static LineageFlowKind flowKind(String value) {
        try {
            return LineageFlowKind.valueOf(value);
        } catch (RuntimeException ignored) {
            return LineageFlowKind.VALUE;
        }
    }

    private static ColumnRef column(AliasSymbolTable aliases, TableId table, String name) {
        String clean = clean(name);
        return new ColumnRef(table, clean, aliases.normalizeIdentifier(clean), null, true);
    }

    record SourceResolution(
            List<Endpoint> sources,
            List<LineageTransformType> transforms,
            LineageFlowKind flowKind
    ) {
    }

    private record ProjectionKey(String alias, String column, LineageFlowKind flowKind) {
        boolean matches(String rowset, AliasSymbolTable aliases) {
            return alias.equals(aliases.normalizeIdentifier(clean(rowset)));
        }
    }

    private record ProjectionAnchor(TableId table, boolean wildcardDerived) {
    }
}
