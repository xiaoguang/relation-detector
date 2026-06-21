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

/**
 * Data Lineage extractor for token-event.
 *
 * <p>The extractor consumes token-event records produced by the structured SQL
 * parser and emits database-internal field lineage.
 */
public final class TokenEventDataLineageExtractor {
    public List<DataLineageCandidate> extract(SqlStatementRecord statement, StructuredParseResult structured) {
        return extractNative(statement, structured);
    }

    public List<DataLineageCandidate> extract(
            SqlStatementRecord statement,
            StructuredParseResult structured,
            Set<TableId> knownPhysicalTables
    ) {
        /*
         * Data Lineage v1 only filters local temporary tables when the SQL text
         * explicitly declares them through LOCAL_TEMP_TABLE_DECLARATION events.
         * knownPhysicalTables is intentionally kept as a future extension point
         * for metadata-aware lineage, but it must not reintroduce name-based temp
         * table guessing.
         */
        return extractNative(statement, structured);
    }

    private List<DataLineageCandidate> extractNative(SqlStatementRecord statement, StructuredParseResult structured) {
        Map<String, TableId> aliases = aliases(structured.events());
        Set<String> localTempTables = localTempTables(structured.events());
        Map<String, Projection> projections = projections(structured.events(), aliases);
        List<DataLineageCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : structured.events()) {
            if (event.type() != StructuredParseEventType.UPDATE_ASSIGNMENT
                    && event.type() != StructuredParseEventType.INSERT_SELECT_MAPPING
                    && event.type() != StructuredParseEventType.MERGE_WRITE_MAPPING) {
                continue;
            }
            ColumnRef target = targetColumn(event, aliases);
            SourceResolution sourceResolution = sourceEndpoints(event, aliases, projections);
            List<Endpoint> sources = sourceResolution.sources().stream().distinct().toList();
            if (target == null || sources.isEmpty()) {
                continue;
            }
            if (isLocalTemp(target.table(), localTempTables) || sources.stream()
                    .anyMatch(source -> source.column() != null && isLocalTemp(source.column().table(), localTempTables))) {
                continue;
            }
            LineageTransformType transform = effectiveTransform(text(event, "transformType"), sourceResolution.transforms());
            LineageFlowKind flowKind = flowKind(text(event, "flowKind"));
            DataLineageCandidate candidate = new DataLineageCandidate(
                    sources,
                    Endpoint.column(target),
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
        String targetTable = text(event, "targetTable");
        if (!targetTable.isBlank()) {
            return ColumnRef.of(tableId(targetTable), targetColumn);
        }
        TableId table = aliases.get(normalize(text(event, "targetAlias")));
        if (table == null && aliases.size() == 1) {
            table = aliases.values().iterator().next();
        }
        return table == null ? null : ColumnRef.of(table, targetColumn);
    }

    private SourceResolution sourceEndpoints(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Map<String, Projection> projections
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
            if (table != null && !sourceColumn.isBlank()) {
                endpoints.add(Endpoint.column(ColumnRef.of(table, sourceColumns.get(index))));
                transforms.add(LineageTransformType.DIRECT);
            }
        }
        return new SourceResolution(endpoints.stream().distinct().toList(), transforms);
    }

    private Map<String, Projection> projections(List<StructuredSqlEvent> events, Map<String, TableId> aliases) {
        Map<String, Projection> projections = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.PROJECTION_ITEM) {
                continue;
            }
            String outputAlias = text(event, "outputAlias");
            String outputColumn = text(event, "outputColumn");
            if (outputAlias.isBlank() || outputColumn.isBlank()) {
                continue;
            }
            List<String> sourceAliases = stringList(event.attributes().get("sourceAliases"));
            List<String> sourceColumns = stringList(event.attributes().get("sourceColumns"));
            List<Endpoint> endpoints = new ArrayList<>();
            int count = Math.min(sourceAliases.size(), sourceColumns.size());
            for (int index = 0; index < count; index++) {
                TableId table = aliases.get(normalize(sourceAliases.get(index)));
                if (table != null && !sourceColumns.get(index).isBlank()) {
                    endpoints.add(Endpoint.column(ColumnRef.of(table, sourceColumns.get(index))));
                }
            }
            if (!endpoints.isEmpty()) {
                projections.put(projectionKey(outputAlias, outputColumn),
                        new Projection(endpoints.stream().distinct().toList(), transform(text(event, "transformType"))));
            }
        }
        return projections;
    }

    private Set<String> localTempTables(List<StructuredSqlEvent> events) {
        Set<String> result = new java.util.LinkedHashSet<>();
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
        if (sourceTransforms.contains(LineageTransformType.CUMULATIVE)) {
            return LineageTransformType.CUMULATIVE;
        }
        if (sourceTransforms.contains(LineageTransformType.AGGREGATE)) {
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

    private record Projection(List<Endpoint> sources, LineageTransformType transform) {
    }

    private record SourceResolution(List<Endpoint> sources, List<LineageTransformType> transforms) {
    }
}
