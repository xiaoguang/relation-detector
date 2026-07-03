package com.relationdetector.core.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                String outputAlias = text(event, "outputAlias");
                String outputColumn = text(event, "outputColumn");
                if (outputAlias.isBlank() || outputColumn.isBlank()) {
                    continue;
                }
                SourceResolution resolved = sourceEndpoints(event, aliases, projections, ignoredRowsets);
                if (!resolved.sources().isEmpty()) {
                    Endpoint projection = Endpoint.column(ColumnRef.of(tableId(outputAlias), outputColumn));
                    changed |= putProjection(
                            projections,
                            outputAlias,
                            outputColumn,
                            new ProjectionTrace(
                                    projection,
                                    resolved.sources().stream().distinct().toList(),
                                    effectiveTransform(text(event, "transformType"), resolved.transforms())),
                            ignoredRowsets);
                }
            }
            changed |= copyIgnoredRowsetAliases(events, ignoredRowsets, projections);
        } while (changed);
        return new ProjectionTraceResolver(projections);
    }

    Optional<ProjectionTrace> resolve(String alias, String column) {
        return Optional.ofNullable(traces.get(projectionKey(alias, column)));
    }

    SourceResolution resolveSources(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Set<String> ignoredRowsets
    ) {
        return sourceEndpoints(event, aliases, traces, ignoredRowsets);
    }

    private static SourceResolution sourceEndpoints(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Map<String, ProjectionTrace> projections,
            Set<String> ignoredRowsets
    ) {
        List<String> sourceAliases = stringList(event.attributes().get("sourceAliases"));
        List<String> sourceColumns = stringList(event.attributes().get("sourceColumns"));
        List<Endpoint> endpoints = new ArrayList<>();
        List<LineageTransformType> transforms = new ArrayList<>();
        int count = Math.min(sourceAliases.size(), sourceColumns.size());
        for (int index = 0; index < count; index++) {
            String sourceAlias = sourceAliases.get(index);
            String sourceColumn = sourceColumns.get(index);
            ProjectionTrace projection = projections.get(projectionKey(sourceAlias, sourceColumn));
            if (projection != null) {
                endpoints.addAll(projection.sources());
                transforms.add(projection.transform());
                continue;
            }
            TableId table = aliases.get(normalize(sourceAlias));
            if (table != null && !sourceColumn.isBlank() && !isIgnoredRowsetTable(table, ignoredRowsets)) {
                endpoints.add(Endpoint.column(ColumnRef.of(table, sourceColumn)));
                transforms.add(LineageTransformType.DIRECT);
            }
        }
        return new SourceResolution(endpoints.stream().distinct().toList(), transforms);
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
            String table = text(event, "table");
            String qualified = text(event, "qualifiedTable");
            String alias = text(event, "alias");
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
        String key = projectionKey(alias, column);
        if (parseProjectionKey(key).alias().isBlank()
                || parseProjectionKey(key).column().isBlank()) {
            return false;
        }
        boolean changed = putProjectionKey(projections, key, projection, ignoredRowsets);
        String baseKey = projectionKey(baseName(alias), column);
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
        if (existing == null || isBetterProjection(projection, existing, ignoredRowsets)) {
            projections.put(key, projection);
            return true;
        }
        return false;
    }

    private static boolean isBetterProjection(
            ProjectionTrace candidate,
            ProjectionTrace existing,
            Set<String> ignoredRowsets
    ) {
        return projectionResolutionScore(candidate, ignoredRowsets) > projectionResolutionScore(existing, ignoredRowsets);
    }

    private static int projectionResolutionScore(ProjectionTrace projection, Set<String> ignoredRowsets) {
        int score = 0;
        for (Endpoint source : projection.sources()) {
            if (source.column() == null) {
                continue;
            }
            score += ignoredRowsets.contains(normalize(source.table().tableName())) ? 1 : 4;
        }
        return score;
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

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : value.toString();
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

    private static String projectionKey(String alias, String column) {
        return normalize(alias) + "." + normalize(column);
    }

    private static ProjectionKey parseProjectionKey(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0) {
            return new ProjectionKey(key, "");
        }
        return new ProjectionKey(key.substring(0, dot), key.substring(dot + 1));
    }

    record SourceResolution(List<Endpoint> sources, List<LineageTransformType> transforms) {
    }

    private record ProjectionKey(String alias, String column) {
        boolean matches(String rowset) {
            String normalized = rowset == null ? "" : rowset.toLowerCase(Locale.ROOT);
            int dot = normalized.lastIndexOf('.');
            String base = dot < 0 ? normalized : normalized.substring(dot + 1);
            return alias.equals(normalized) || alias.equals(base);
        }
    }
}
