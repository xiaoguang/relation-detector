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
 *
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
        Set<String> declaredProjectionKeys = ProjectionTraceInventory.declaredKeys(events, aliases);
        Map<String, TableId> physicalAliases = ProjectionTraceInventory.physicalAliases(events, aliases, ignoredRowsets);
        Map<String, ProjectionAnchor> wildcardAnchors = ProjectionTraceInventory.wildcardAnchors(
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
                    Endpoint projection = Endpoint.column(ProjectionTraceInventory.column(
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
                                            resolution.transforms(), resolution.flowKind(),
                                            event.expression().flowKind()),
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
                ProjectionKey key = ProjectionTraceInventory.parseKey(entry.getKey());
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
        ProjectionTrace value = traces.get(ProjectionTraceInventory.key(aliases, alias, column, LineageFlowKind.VALUE));
        if (value != null) {
            return Optional.of(value);
        }
        return Optional.ofNullable(traces.get(ProjectionTraceInventory.key(aliases, alias, column, LineageFlowKind.CONTROL)));
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

    /**
     * CN: 将 typed expression sources 解析为物理 endpoint，并在当前 query scope 内传播 projection、VALUE/CONTROL 与 transform；
     * 无法证明为物理列的参数、局部变量和伪 rowset 不会被补造成 source。
     * EN: Resolves typed expression sources to physical endpoints and propagates projections, VALUE/CONTROL roles,
     * and transforms within the current query scope; unproven parameters, locals, and pseudo-rowsets are not invented.
     */
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
            TableId selfReferenceTable = selfReferenceTable(
                    event, sourceAlias, sourceColumn, aliases, physicalAliases);
            if (selfReferenceTable != null) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(ProjectionTraceInventory.column(aliases, selfReferenceTable, sourceColumn)));
                transforms.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(LineageTransformType.DIRECT);
                continue;
            }
            ProjectionAnchor wildcardAnchor = wildcardAnchors.get(
                    aliases.normalizeIdentifier(sourceAlias));
            if (event.type() != StructuredParseEventType.PROJECTION_ITEM
                    && wildcardAnchor != null
                    && wildcardAnchor.wildcardDerived()
                    && !sourceColumn.isBlank()
                    && !"*".equals(sourceColumn)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(ProjectionTraceInventory.column(aliases, wildcardAnchor.table(), sourceColumn)));
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
            if (declaredProjectionKeys.contains(ProjectionTraceInventory.outputKey(aliases, sourceAlias, sourceColumn))) {
                continue;
            }
            TableId table = physicalAliases.get(aliases.normalizeIdentifier(sourceAlias));
            if (table != null && !sourceColumn.isBlank()
                    && !isIgnoredRowsetTable(table, ignoredRowsets, aliases)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(ProjectionTraceInventory.column(aliases, table, sourceColumn)));
                transforms.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(LineageTransformType.DIRECT);
                continue;
            }
            if (wildcardAnchor != null && !sourceColumn.isBlank() && !"*".equals(sourceColumn)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(ProjectionTraceInventory.column(aliases, wildcardAnchor.table(), sourceColumn)));
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

    private static TableId selfReferenceTable(
            StructuredSqlEvent event,
            String sourceAlias,
            String sourceColumn,
            AliasSymbolTable aliases,
            Map<String, TableId> physicalAliases
    ) {
        if ((event.type() != StructuredParseEventType.UPDATE_ASSIGNMENT
                && event.type() != StructuredParseEventType.MERGE_WRITE_MAPPING)
                || !clean(sourceAlias).isBlank()
                || event.targetColumn().isBlank()
                || !aliases.normalizeIdentifier(sourceColumn)
                .equals(aliases.normalizeIdentifier(event.targetColumn()))) {
            return null;
        }
        TableId table = physicalAliases.get(aliases.normalizeIdentifier(event.targetAlias()));
        if (table != null) {
            return table;
        }
        return physicalAliases.get(aliases.normalizeIdentifier(event.targetTable()));
    }

    private static List<ProjectionTrace> projectionVariants(
            Map<String, ProjectionTrace> projections,
            AliasSymbolTable aliases,
            String alias,
            String column
    ) {
        List<ProjectionTrace> variants = new ArrayList<>(2);
        for (LineageFlowKind flow : LineageFlowKind.values()) {
            ProjectionTrace projection = projections.get(ProjectionTraceInventory.key(aliases, alias, column, flow));
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
                ProjectionKey key = ProjectionTraceInventory.parseKey(entry.getKey());
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
        String key = ProjectionTraceInventory.key(aliases, alias, column, projection.flowKind());
        if (ProjectionTraceInventory.parseKey(key).alias().isBlank()
                || ProjectionTraceInventory.parseKey(key).column().isBlank()) {
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
        return effectiveTransform(eventTransform, sourceTransforms, flowKind, flowKind);
    }

    static LineageTransformType effectiveTransform(
            String eventTransform,
            List<LineageTransformType> sourceTransforms,
            LineageFlowKind resolvedFlowKind,
            LineageFlowKind eventFlowKind
    ) {
        if (resolvedFlowKind == LineageFlowKind.CONTROL) {
            if (eventFlowKind != LineageFlowKind.CONTROL && !sourceTransforms.isEmpty()) {
                return LineageTransformClassifier.dominant(
                        sourceTransforms.toArray(LineageTransformType[]::new));
            }
            return transform(eventTransform);
        }
        return effectiveTransform(eventTransform, sourceTransforms);
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`")) || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    record SourceResolution(
            List<Endpoint> sources,
            List<LineageTransformType> transforms,
            LineageFlowKind flowKind
    ) {
    }

}
