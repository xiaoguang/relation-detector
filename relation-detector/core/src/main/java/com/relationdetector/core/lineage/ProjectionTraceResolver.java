package com.relationdetector.core.lineage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
import com.relationdetector.core.lineage.model.ProjectionTrace;

/**
 * Resolves CTE / derived-table projection aliases from structured parser events.
 */
final class ProjectionTraceResolver {
    private final Map<String, ProjectionTrace> traces;

    private ProjectionTraceResolver(Map<String, ProjectionTrace> traces) {
        this.traces = Map.copyOf(traces);
    }

    static ProjectionTraceResolver fromEvents(
            List<StructuredSqlEvent> events,
            Map<String, TableId> aliases,
            Set<String> ignoredRowsets
    ) {
        Map<String, ProjectionTrace> projections = new LinkedHashMap<>();
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
                List<SourceResolution> resolved = sourceEndpoints(event, aliases, projections, ignoredRowsets);
                for (SourceResolution resolution : resolved) {
                    if (resolution.sources().isEmpty()) {
                        continue;
                    }
                    Endpoint projection = Endpoint.column(ColumnRef.of(tableId(outputAlias), outputColumn));
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
                            ignoredRowsets);
                }
            }
            changed |= copyIgnoredRowsetAliases(events, ignoredRowsets, projections);
        } while (changed);
        return new ProjectionTraceResolver(projections);
    }

    Optional<ProjectionTrace> resolve(String alias, String column) {
        ProjectionTrace value = traces.get(projectionKey(alias, column, LineageFlowKind.VALUE));
        if (value != null) {
            return Optional.of(value);
        }
        return Optional.ofNullable(traces.get(projectionKey(alias, column, LineageFlowKind.CONTROL)));
    }

    List<SourceResolution> resolveSources(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Set<String> ignoredRowsets
    ) {
        return sourceEndpoints(event, aliases, traces, ignoredRowsets);
    }

    private static List<SourceResolution> sourceEndpoints(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
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
            List<ProjectionTrace> variants = projectionVariants(projections, sourceAlias, sourceColumn);
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
            TableId table = aliases.get(normalize(sourceAlias));
            if (table != null && !sourceColumn.isBlank() && !isIgnoredRowsetTable(table, ignoredRowsets)) {
                endpoints.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(Endpoint.column(ColumnRef.of(table, sourceColumn)));
                transforms.computeIfAbsent(eventFlow, ignored -> new ArrayList<>())
                        .add(LineageTransformType.DIRECT);
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
            String alias,
            String column
    ) {
        List<ProjectionTrace> variants = new ArrayList<>(2);
        for (LineageFlowKind flow : LineageFlowKind.values()) {
            ProjectionTrace projection = projections.get(projectionKey(alias, column, flow));
            if (projection != null) {
                variants.add(projection);
            }
        }
        return variants;
    }

    private static boolean copyIgnoredRowsetAliases(
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Map<String, ProjectionTrace> projections
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
                    || (!ignoredRowsets.contains(normalize(table)) && !ignoredRowsets.contains(normalize(qualified)))) {
                continue;
            }
            for (Map.Entry<String, ProjectionTrace> entry : List.copyOf(projections.entrySet())) {
                ProjectionKey key = parseProjectionKey(entry.getKey());
                if (key.matches(table) || key.matches(qualified)) {
                    changed |= putProjection(projections, alias, key.column(), entry.getValue(), ignoredRowsets);
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
            Set<String> ignoredRowsets
    ) {
        String key = projectionKey(alias, column, projection.flowKind());
        if (parseProjectionKey(key).alias().isBlank()
                || parseProjectionKey(key).column().isBlank()) {
            return false;
        }
        boolean changed = putProjectionKey(projections, key, projection, ignoredRowsets);
        String baseKey = projectionKey(baseName(alias), column, projection.flowKind());
        if (!baseKey.equals(key)) {
            changed |= putProjectionKey(projections, baseKey, projection, ignoredRowsets);
        }
        return changed;
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

    private static boolean isIgnoredRowsetTable(TableId table, Set<String> ignoredRowsets) {
        return ignoredRowsets.contains(normalize(table.tableName()))
                || (table.schema() != null
                && ignoredRowsets.contains(normalize(table.schema() + "." + table.tableName())));
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

    private static TableId tableId(String qualified) {
        String clean = clean(qualified);
        int dot = clean.lastIndexOf('.');
        if (dot < 0) {
            return TableId.of(null, clean);
        }
        return TableId.of(clean.substring(0, dot), clean.substring(dot + 1));
    }

    private static String baseName(String qualified) {
        String clean = clean(qualified);
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? clean : clean.substring(dot + 1);
    }

    private static String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`")) || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private static String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private static String projectionKey(String alias, String column, LineageFlowKind flowKind) {
        return normalize(alias) + "." + normalize(column) + "|" + flowKind.name();
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

    record SourceResolution(
            List<Endpoint> sources,
            List<LineageTransformType> transforms,
            LineageFlowKind flowKind
    ) {
    }

    private record ProjectionKey(String alias, String column, LineageFlowKind flowKind) {
        boolean matches(String rowset) {
            String normalized = rowset == null ? "" : rowset.toLowerCase(Locale.ROOT);
            int dot = normalized.lastIndexOf('.');
            String base = dot < 0 ? normalized : normalized.substring(dot + 1);
            return alias.equals(normalized) || alias.equals(base);
        }
    }
}
