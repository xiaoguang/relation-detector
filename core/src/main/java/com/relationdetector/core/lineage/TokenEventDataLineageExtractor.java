package com.relationdetector.core.lineage;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.DataLineageEvidence;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.core.lineage.model.AssignmentMapping;
import com.relationdetector.core.lineage.model.ExpressionSourceSet;

/**
 * SQL Data Lineage 语义抽取器。
 *
 * <p>CN: 本类消费 token-event 与 full-grammer 共享的写入/projection 结构事件，输出
 * 数据库内部字段血缘。它不做 Parameter Binding，不把参数、literal、JSON path 或局部变量
 * 作为 source。
 *
 * <p>EN: SQL Data Lineage semantic extractor. It consumes write/projection
 * structured events shared by token-event and full-grammer and emits
 * database-internal field lineage. It does not perform parameter binding and
 * does not treat parameters, literals, JSON paths, or local variables as sources.
 */
public final class TokenEventDataLineageExtractor {
    /**
     * 从结构化 SQL events 抽取字段血缘。
     *
     * <p>EN: Extracts field lineage from structured SQL events.
     */
    public List<DataLineageCandidate> extract(SqlStatementRecord statement, StructuredParseResult structured) {
        return extractNative(statement, structured, Set.of());
    }

    public List<DataLineageCandidate> extract(
            SqlStatementRecord statement,
            StructuredParseResult structured,
            Set<TableId> knownPhysicalTables
    ) {
        return extractNative(statement, structured, knownPhysicalTables == null ? Set.of() : knownPhysicalTables);
    }

    private List<DataLineageCandidate> extractNative(
            SqlStatementRecord statement,
            StructuredParseResult structured,
            Set<TableId> knownPhysicalTables
    ) {
        Map<String, TableId> aliases = aliases(structured.events());
        Set<String> localTempTables = localTempTables(statement, structured.events());
        Set<String> ignoredRowsets = ignoredRowsets(structured.events());
        Map<String, Projection> projections = projections(structured.events(), aliases);
        List<DataLineageCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : structured.events()) {
            if (event.type() != StructuredParseEventType.UPDATE_ASSIGNMENT
                    && event.type() != StructuredParseEventType.INSERT_SELECT_MAPPING
                    && event.type() != StructuredParseEventType.MERGE_WRITE_MAPPING) {
                continue;
            }
            ColumnRef target = targetColumn(event, aliases);
            SourceResolution sourceResolution = sourceEndpoints(event, aliases, projections, ignoredRowsets);
            if (target == null
                    || isLocalTemp(target.table(), localTempTables)
                    || !isKnownPhysical(target.table(), knownPhysicalTables)) {
                continue;
            }
            List<Endpoint> sources = sourceResolution.sources().stream()
                    .filter(source -> source.column() != null)
                    .filter(source -> !isLocalTemp(source.column().table(), localTempTables))
                    .filter(source -> !isIgnoredRowsetTable(source.column().table(), ignoredRowsets))
                    .filter(source -> isKnownPhysical(source.column().table(), knownPhysicalTables))
                    .distinct()
                    .toList();
            if (sources.isEmpty()) {
                continue;
            }
            LineageTransformType transform = effectiveTransform(text(event, "transformType"), sourceResolution.transforms());
            LineageFlowKind flowKind = flowKind(text(event, "flowKind"));
            AssignmentMapping mapping = assignmentMapping(event, target, sources, transform);
            DataLineageCandidate candidate = new DataLineageCandidate(
                    mapping.expressionSources().sources(),
                    mapping.target(),
                    flowKind,
                    transform);
            BigDecimal score = score(transform, flowKind);
            candidate.confidence(score);
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("tokenEventNative", true);
            attributes.put("mappingKind", text(event, "mappingKind"));
            candidate.attributes().putAll(attributes);
            candidate.evidence().add(new DataLineageEvidence(
                    transform,
                    score,
                    sourceType(statement.sourceType()),
                    statement.sourceName(),
                    "ANTLR token-event write mapping",
                    attributes));
            candidates.add(candidate);
        }
        return new DataLineageMerger().merge(candidates);
    }

    private AssignmentMapping assignmentMapping(
            StructuredSqlEvent event,
            ColumnRef target,
            List<Endpoint> sources,
            LineageTransformType transform
    ) {
        return new AssignmentMapping(
                Endpoint.column(target),
                new ExpressionSourceSet(sources, transform.name()),
                text(event, "mappingKind"));
    }

    private Map<String, TableId> aliases(List<StructuredSqlEvent> events) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.ROWSET_REFERENCE || event.type() == StructuredParseEventType.WRITE_TARGET) {
                String table = text(event, "qualifiedTable");
                if (table.isBlank()) {
                    table = text(event, "table");
                }
                if (table.isBlank()) {
                    continue;
                }
                TableId tableId = tableId(table);
                aliases.put(normalize(text(event, "table")), tableId);
                aliases.put(normalize(baseName(table)), tableId);
                String alias = text(event, "alias");
                if (!alias.isBlank()) {
                    aliases.put(normalize(alias), tableId);
                }
            }
        }
        return aliases;
    }

    private ColumnRef targetColumn(StructuredSqlEvent event, Map<String, TableId> aliases) {
        String targetColumn = text(event, "targetColumn");
        if (targetColumn.isBlank()) {
            return null;
        }
        TableId table = aliases.get(normalize(text(event, "targetAlias")));
        if (table != null) {
            return ColumnRef.of(table, targetColumn);
        }
        String targetTable = text(event, "targetTable");
        if (!targetTable.isBlank()) {
            return ColumnRef.of(tableId(targetTable), targetColumn);
        }
        if (table == null && aliases.size() == 1) {
            table = aliases.values().iterator().next();
        }
        return table == null ? null : ColumnRef.of(table, targetColumn);
    }

    private SourceResolution sourceEndpoints(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Map<String, Projection> projections,
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
            Projection projection = projections.get(projectionKey(sourceAlias, sourceColumn));
            if (projection != null) {
                endpoints.addAll(projection.sources());
                transforms.add(projection.transform());
                continue;
            }
            TableId table = aliases.get(normalize(sourceAlias));
            if (table != null && !sourceColumn.isBlank() && !isIgnoredRowsetTable(table, ignoredRowsets)) {
                endpoints.add(Endpoint.column(ColumnRef.of(table, sourceColumns.get(index))));
                transforms.add(LineageTransformType.DIRECT);
            }
        }
        return new SourceResolution(endpoints.stream().distinct().toList(), transforms);
    }

    private Map<String, Projection> projections(List<StructuredSqlEvent> events, Map<String, TableId> aliases) {
        Map<String, Projection> projections = new LinkedHashMap<>();
        Set<String> ignoredRowsets = ignoredRowsets(events);
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
                    changed |= putProjection(
                            projections,
                            outputAlias,
                            outputColumn,
                            new Projection(
                                    resolved.sources().stream().distinct().toList(),
                                    effectiveTransform(text(event, "transformType"), resolved.transforms())),
                            ignoredRowsets);
                }
            }
            changed |= copyIgnoredRowsetAliases(events, ignoredRowsets, projections);
        } while (changed);
        return projections;
    }

    private boolean copyIgnoredRowsetAliases(
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Map<String, Projection> projections
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
            for (Map.Entry<String, Projection> entry : List.copyOf(projections.entrySet())) {
                ProjectionKey key = parseProjectionKey(entry.getKey());
                if (key.matches(table) || key.matches(qualified)) {
                    changed |= putProjection(projections, alias, key.column(), entry.getValue(), ignoredRowsets);
                }
            }
        }
        return changed;
    }

    private Set<String> ignoredRowsets(List<StructuredSqlEvent> events) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.IGNORED_ROWSET
                    && event.type() != StructuredParseEventType.CTE_DECLARATION
                    && event.type() != StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                continue;
            }
            addIgnored(result, text(event, "name"));
            addIgnored(result, text(event, "table"));
            addIgnored(result, text(event, "qualifiedTable"));
        }
        return result;
    }

    private void addIgnored(Set<String> ignored, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        ignored.add(normalize(raw));
        ignored.add(normalize(baseName(raw)));
    }

    private boolean putProjection(
            Map<String, Projection> projections,
            String alias,
            String column,
            Projection projection,
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

    private boolean putProjectionKey(
            Map<String, Projection> projections,
            String key,
            Projection projection,
            Set<String> ignoredRowsets
    ) {
        Projection existing = projections.get(key);
        if (existing == null || isBetterProjection(projection, existing, ignoredRowsets)) {
            projections.put(key, projection);
            return true;
        }
        return false;
    }

    private boolean isBetterProjection(Projection candidate, Projection existing, Set<String> ignoredRowsets) {
        int candidateScore = projectionResolutionScore(candidate, ignoredRowsets);
        int existingScore = projectionResolutionScore(existing, ignoredRowsets);
        return candidateScore > existingScore;
    }

    private int projectionResolutionScore(Projection projection, Set<String> ignoredRowsets) {
        int score = 0;
        for (Endpoint source : projection.sources()) {
            if (source.column() == null) {
                continue;
            }
            score += ignoredRowsets.contains(normalize(source.table().tableName())) ? 1 : 4;
        }
        return score;
    }

    private Set<String> localTempTables(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (String table : stringList(statement.attributes().get("localTempTables"))) {
            addIgnored(result, table);
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                continue;
            }
            String table = text(event, "qualifiedTable");
            if (table.isBlank()) {
                table = text(event, "table");
            }
            if (!table.isBlank()) {
                result.add(normalize(baseName(table)));
                result.add(normalize(table));
            }
        }
        return result;
    }

    private boolean isLocalTemp(TableId table, Set<String> localTempTables) {
        return localTempTables.contains(normalize(table.tableName()))
                || (table.schema() != null
                && localTempTables.contains(normalize(table.schema() + "." + table.tableName())));
    }

    private boolean isIgnoredRowsetTable(TableId table, Set<String> ignoredRowsets) {
        return ignoredRowsets.contains(normalize(table.tableName()))
                || (table.schema() != null
                && ignoredRowsets.contains(normalize(table.schema() + "." + table.tableName())));
    }

    private boolean isKnownPhysical(TableId table, Set<TableId> knownPhysicalTables) {
        if (knownPhysicalTables == null || knownPhysicalTables.isEmpty()) {
            return true;
        }
        for (TableId known : knownPhysicalTables) {
            if (sameTable(table, known)) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTable(TableId left, TableId right) {
        if (!normalize(left.tableName()).equals(normalize(right.tableName()))) {
            return false;
        }
        String leftSchema = left.schema() == null ? "" : normalize(left.schema());
        String rightSchema = right.schema() == null ? "" : normalize(right.schema());
        return leftSchema.isBlank() || rightSchema.isBlank() || leftSchema.equals(rightSchema);
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private LineageTransformType transform(String value) {
        try {
            return LineageTransformType.valueOf(value);
        } catch (RuntimeException ignored) {
            return LineageTransformType.UNKNOWN_EXPRESSION;
        }
    }

    private LineageTransformType effectiveTransform(String eventTransform, List<LineageTransformType> sourceTransforms) {
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

    private LineageFlowKind flowKind(String value) {
        try {
            return LineageFlowKind.valueOf(value);
        } catch (RuntimeException ignored) {
            return LineageFlowKind.VALUE;
        }
    }

    private BigDecimal score(LineageTransformType transform, LineageFlowKind flowKind) {
        if (flowKind == LineageFlowKind.CONTROL) {
            return BigDecimal.valueOf(0.55d);
        }
        return switch (transform) {
            case DIRECT -> BigDecimal.valueOf(0.90d);
            case AGGREGATE, CUMULATIVE -> BigDecimal.valueOf(0.80d);
            case COALESCE, ARITHMETIC -> BigDecimal.valueOf(0.75d);
            case CONCAT_FORMAT -> BigDecimal.valueOf(0.70d);
            case CASE_WHEN, FUNCTION_CALL -> BigDecimal.valueOf(0.65d);
            case WINDOW_DERIVED -> BigDecimal.valueOf(0.50d);
            case UNKNOWN_EXPRESSION -> BigDecimal.valueOf(0.35d);
        };
    }

    private EvidenceSourceType sourceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            default -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : value.toString();
    }

    private TableId tableId(String qualified) {
        String clean = clean(qualified);
        int dot = clean.lastIndexOf('.');
        if (dot < 0) {
            return TableId.of(null, clean);
        }
        return TableId.of(clean.substring(0, dot), clean.substring(dot + 1));
    }

    private String baseName(String qualified) {
        String clean = clean(qualified);
        int dot = clean.lastIndexOf('.');
        return dot < 0 ? clean : clean.substring(dot + 1);
    }

    private String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`")) || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private String projectionKey(String alias, String column) {
        return normalize(alias) + "." + normalize(column);
    }

    private ProjectionKey parseProjectionKey(String key) {
        int dot = key.lastIndexOf('.');
        if (dot < 0) {
            return new ProjectionKey(key, "");
        }
        return new ProjectionKey(key.substring(0, dot), key.substring(dot + 1));
    }

    private record Projection(List<Endpoint> sources, LineageTransformType transform) {
    }

    private record SourceResolution(List<Endpoint> sources, List<LineageTransformType> transforms) {
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
