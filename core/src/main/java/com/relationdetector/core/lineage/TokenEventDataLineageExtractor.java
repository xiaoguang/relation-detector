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
        ProjectionTraceResolver projectionTraces = ProjectionTraceResolver.fromEvents(
                structured.events(), aliases, ignoredRowsets);
        List<DataLineageCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : structured.events()) {
            if (event.type() != StructuredParseEventType.UPDATE_ASSIGNMENT
                    && event.type() != StructuredParseEventType.INSERT_SELECT_MAPPING
                    && event.type() != StructuredParseEventType.MERGE_WRITE_MAPPING) {
                continue;
            }
            ColumnRef target = targetColumn(event, aliases);
            if (target == null
                    || isLocalTemp(target.table(), localTempTables)
                    || !isKnownPhysical(target.table(), knownPhysicalTables)) {
                continue;
            }
            ProjectionTraceResolver.SourceResolution sourceResolution =
                    projectionTraces.resolveSources(event, aliases, ignoredRowsets);
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
            LineageTransformType transform = ProjectionTraceResolver.effectiveTransform(
                    text(event, "transformType"), sourceResolution.transforms());
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

    private Set<String> ignoredRowsets(List<StructuredSqlEvent> events) {
        Set<String> result = new java.util.LinkedHashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                addIgnored(result, text(event, "name"));
                continue;
            }
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

}
