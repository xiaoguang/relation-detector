package com.relationdetector.core.relation;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.TableId;
import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * SQL relationship 语义抽取器。
 *
 * <p>CN: 本类消费 token-event 与 full-grammer 共享的 StructuredSqlEvent，并输出
 * RelationshipCandidate。SQL 谓词只证明列之间共同出现；FK-like 方向只能来自
 * DDL/metadata/data-profile 等结构化方向证据。EXISTS/IN 证据、列级弱共现、JOIN
 * USING 和 self-join 弱关系都在这里统一处理，不放入方言 parser。
 *
 * <p>EN: SQL relationship semantic extractor. It consumes StructuredSqlEvent
 * records shared by token-event and full-grammer and emits RelationshipCandidate
 * instances. SQL predicates prove column co-occurrence only; FK-like direction
 * must come from DDL/metadata/data-profile evidence. EXISTS/IN evidence, column
 * co-occurrence, JOIN USING, and self-join weak relations are handled here
 * rather than in dialect parsers.
 */
public final class TokenEventRelationExtractor {
    /**
     * 从结构化 SQL events 抽取 relationship 候选。
     *
     * <p>EN: Extracts relationship candidates from structured SQL events.
     */
    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult structured) {
        return extractNative(statement, structured);
    }

    private List<RelationshipCandidate> extractNative(SqlStatementRecord statement, StructuredParseResult structured) {
        Set<String> ignoredRowsets = ignoredRowsets(statement, structured.events());
        Map<String, TableId> aliases = rowsetAliases(structured.events(), ignoredRowsets);
        Map<ColumnKey, ColumnRef> projections = projectedColumns(structured.events(), aliases, ignoredRowsets);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : structured.events()) {
            if (event.type() == StructuredParseEventType.PREDICATE_EQUALITY) {
                ColumnRef left = resolve(text(event, "leftAlias"), text(event, "leftColumn"), aliases, projections);
                ColumnRef right = resolve(text(event, "rightAlias"), text(event, "rightColumn"), aliases, projections);
                if (left == null || right == null) {
                    continue;
                }
                if (isIgnored(left.table(), ignoredRowsets) || isIgnored(right.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(left, right, text(event, "leftAlias"), text(event, "rightAlias"))) {
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            EvidenceType.SQL_LOG_JOIN,
                            text(event, "joinKind"),
                            text(event, "leftAlias"),
                            text(event, "rightAlias"),
                            "ANTLR token-event column equality"));
                }
            } else if (event.type() == StructuredParseEventType.EXISTS_PREDICATE) {
                ColumnRef left = resolve(text(event, "leftAlias"), text(event, "leftColumn"), aliases, projections);
                ColumnRef right = resolve(text(event, "rightAlias"), text(event, "rightColumn"), aliases, projections);
                if (left == null || right == null) {
                    continue;
                }
                if (isIgnored(left.table(), ignoredRowsets) || isIgnored(right.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(left, right, text(event, "leftAlias"), text(event, "rightAlias"))) {
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            EvidenceType.SQL_LOG_EXISTS,
                            "EXISTS",
                            text(event, "leftAlias"),
                            text(event, "rightAlias"),
                            "ANTLR token-event EXISTS ambiguous column equality"));
                }
            } else if (event.type() == StructuredParseEventType.JOIN_USING_COLUMNS) {
                String leftAlias = text(event, "leftAlias");
                String rightAlias = text(event, "rightAlias");
                for (String column : stringList(event.attributes().get("usingColumns"))) {
                    ColumnRef left = resolve(leftAlias, column, aliases, projections);
                    ColumnRef right = resolve(rightAlias, column, aliases, projections);
                    if (left == null || right == null) {
                        continue;
                    }
                    if (isIgnored(left.table(), ignoredRowsets) || isIgnored(right.table(), ignoredRowsets)) {
                        continue;
                    }
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            EvidenceType.SQL_LOG_JOIN,
                            "USING_JOIN",
                            leftAlias,
                            rightAlias,
                            "ANTLR token-event JOIN USING column equality"));
                }
            } else if (event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                ColumnRef outer = resolveWithFallbackTable(
                        text(event, "outerAlias"),
                        text(event, "outerColumn"),
                        text(event, "outerTable"),
                        aliases,
                        projections);
                ColumnRef inner = resolveInSubqueryColumn(event, aliases, projections);
                if (outer == null || inner == null) {
                    continue;
                }
                if (isIgnored(outer.table(), ignoredRowsets) || isIgnored(inner.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(outer, inner, text(event, "outerAlias"), text(event, "innerAlias"))) {
                    candidates.add(columnCoOccurrenceCandidate(statement, outer, inner,
                            EvidenceType.SQL_LOG_SUBQUERY_IN,
                            "IN_SUBQUERY",
                            text(event, "outerAlias"),
                            text(event, "innerAlias"),
                            "ANTLR token-event IN subquery column co-occurrence"));
                }
            } else if (event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                List<String> outerAliases = stringList(event.attributes().get("outerAliases"));
                List<String> outerColumns = stringList(event.attributes().get("outerColumns"));
                List<String> innerAliases = stringList(event.attributes().get("innerAliases"));
                List<String> innerColumns = stringList(event.attributes().get("innerColumns"));
                int count = Math.min(Math.min(outerAliases.size(), outerColumns.size()),
                        Math.min(innerAliases.size(), innerColumns.size()));
                for (int index = 0; index < count; index++) {
                    ColumnRef outer = resolve(outerAliases.get(index), outerColumns.get(index), aliases, projections);
                    ColumnRef inner = resolveTupleInSubqueryColumn(
                            event, innerAliases.get(index), innerColumns.get(index), aliases, projections);
                    if (outer == null || inner == null) {
                        continue;
                    }
                    if (isIgnored(outer.table(), ignoredRowsets) || isIgnored(inner.table(), ignoredRowsets)) {
                        continue;
                    }
                    if (shouldEmitColumnCoOccurrence(outer, inner, outerAliases.get(index), innerAliases.get(index))) {
                        candidates.add(columnCoOccurrenceCandidate(statement, outer, inner,
                                EvidenceType.SQL_LOG_SUBQUERY_IN,
                                "TUPLE_IN_SUBQUERY",
                                outerAliases.get(index),
                                innerAliases.get(index),
                                "ANTLR token-event tuple IN subquery column co-occurrence position " + (index + 1)));
                    }
                }
            }
        }
        return deduplicate(candidates);
    }

    private boolean isVerifiedColumnSubquery(StructuredSqlEvent event) {
        return Boolean.TRUE.equals(event.attributes().get("verifiedColumnSubquery"));
    }

    private List<RelationshipCandidate> deduplicate(List<RelationshipCandidate> candidates) {
        Map<String, RelationshipCandidate> unique = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : candidates) {
            unique.putIfAbsent(candidateKey(candidate), candidate);
        }
        return new ArrayList<>(unique.values());
    }

    private String candidateKey(RelationshipCandidate candidate) {
        EvidenceType evidenceType = firstEvidenceType(candidate);
        EvidenceSourceType sourceType = candidate.evidence().isEmpty() ? null : candidate.evidence().get(0).sourceType();
        return candidate.relationType() + "|"
                + candidate.relationSubType() + "|"
                + endpointKey(candidate) + "|"
                + evidenceType + "|"
                + sourceType;
    }

    private EvidenceType firstEvidenceType(RelationshipCandidate candidate) {
        return candidate.evidence().isEmpty() ? null : candidate.evidence().get(0).type();
    }

    private String endpointKey(RelationshipCandidate candidate) {
        return candidate.source().normalizedKey() + "->" + candidate.target().normalizedKey();
    }

    private Map<String, TableId> rowsetAliases(List<StructuredSqlEvent> events, Set<String> ignoredRowsets) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                TableId tableId = tableId(text(event, "targetTable"));
                aliases.put(normalize(text(event, "name")), tableId);
                continue;
            }
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String qualified = text(event, "qualifiedTable");
            String table = text(event, "table");
            String alias = text(event, "alias");
            if (ignoredRowsets.contains(normalize(table))
                    || ignoredRowsets.contains(normalize(qualified))) {
                continue;
            }
            TableId tableId = tableId(qualified.isBlank() ? table : qualified);
            aliases.put(normalize(table), tableId);
            if (!alias.isBlank()) {
                aliases.put(normalize(alias), tableId);
            }
        }
        return aliases;
    }

    private Map<ColumnKey, ColumnRef> projectedColumns(
            List<StructuredSqlEvent> events,
            Map<String, TableId> aliases,
            Set<String> ignoredRowsets
    ) {
        Map<ColumnKey, ColumnRef> projections = new LinkedHashMap<>();
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
                ColumnRef source = firstResolvableSource(event, aliases, projections);
                if (source == null || isIgnored(source.table(), ignoredRowsets)) {
                    continue;
                }
                changed |= putProjection(projections, outputAlias, outputColumn, source);
            }
            changed |= copyIgnoredRowsetAliases(events, ignoredRowsets, projections);
        } while (changed);
        return projections;
    }

    private ColumnRef firstResolvableSource(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        List<String> sourceAliases = stringList(event.attributes().get("sourceAliases"));
        List<String> sourceColumns = stringList(event.attributes().get("sourceColumns"));
        int count = Math.min(sourceAliases.size(), sourceColumns.size());
        for (int index = 0; index < count; index++) {
            ColumnRef source = resolve(sourceAliases.get(index), sourceColumns.get(index), aliases, projections);
            if (source != null) {
                return source;
            }
        }
        return null;
    }

    private boolean copyIgnoredRowsetAliases(
            List<StructuredSqlEvent> events,
            Set<String> ignoredRowsets,
            Map<ColumnKey, ColumnRef> projections
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
            for (Map.Entry<ColumnKey, ColumnRef> entry : List.copyOf(projections.entrySet())) {
                if (entry.getKey().matches(table) || entry.getKey().matches(qualified)) {
                    changed |= putProjection(projections, alias, entry.getKey().column(), entry.getValue());
                }
            }
        }
        return changed;
    }

    private boolean putProjection(
            Map<ColumnKey, ColumnRef> projections,
            String alias,
            String column,
            ColumnRef source
    ) {
        ColumnKey key = ColumnKey.of(alias, column);
        if (key.alias().isBlank() || key.column().isBlank() || projections.containsKey(key)) {
            return false;
        }
        projections.put(key, source);
        projections.put(ColumnKey.of(baseName(key.alias()), column), source);
        return true;
    }

    private Set<String> ignoredRowsets(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> ignored = new HashSet<>();
        for (String table : stringList(statement.attributes().get("localTempTables"))) {
            addIgnored(ignored, table);
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.IGNORED_ROWSET
                    || event.type() == StructuredParseEventType.CTE_DECLARATION
                    || event.type() == StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                addIgnored(ignored, text(event, "name"));
                addIgnored(ignored, text(event, "table"));
                addIgnored(ignored, text(event, "qualifiedTable"));
            }
        }
        return ignored;
    }

    private void addIgnored(Set<String> ignored, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        ignored.add(normalize(raw));
        ignored.add(normalize(baseName(raw)));
    }

    private boolean isIgnored(TableId table, Set<String> ignoredRowsets) {
        return isSystemSchema(table.schema())
                || ignoredRowsets.contains(normalize(table.tableName()))
                || (table.schema() != null && ignoredRowsets.contains(normalize(table.schema() + "." + table.tableName())));
    }

    private boolean isSystemSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return false;
        }
        return Set.of("information_schema", "performance_schema", "mysql", "sys", "pg_catalog", "pg_toast")
                .contains(normalize(schema));
    }

    private ColumnRef resolveInSubqueryColumn(
            StructuredSqlEvent event,
            Map<String, TableId> aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        ColumnRef byAlias = resolve(text(event, "innerAlias"), text(event, "innerColumn"), aliases, projections);
        if (byAlias != null) {
            return byAlias;
        }
        return resolveTupleInSubqueryColumn(
                event, text(event, "innerAlias"), text(event, "innerColumn"), aliases, projections);
    }

    private ColumnRef resolveTupleInSubqueryColumn(
            StructuredSqlEvent event,
            String alias,
            String column,
            Map<String, TableId> aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        ColumnRef byAlias = resolve(alias, column, aliases, projections);
        if (byAlias != null) {
            return byAlias;
        }
        String table = text(event, "innerTable");
        if (table.isBlank() || column.isBlank()) {
            return null;
        }
        return ColumnRef.of(tableId(table), clean(column));
    }

    private RelationshipCandidate columnCoOccurrenceCandidate(
            SqlStatementRecord statement,
            ColumnRef left,
            ColumnRef right,
            EvidenceType evidenceType,
            String joinKind,
            String leftAlias,
            String rightAlias,
            String detail
    ) {
        ColumnRef first = left;
        ColumnRef second = right;
        if (normalize(left.displayName()).compareTo(normalize(right.displayName())) > 0) {
            first = right;
            second = left;
        }
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(first),
                Endpoint.column(second),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("joinKind", joinKind.isBlank() ? "WHERE_OR_UNKNOWN" : joinKind);
        attributes.put("tokenEventNative", true);
        if (isExplicitSelfJoinRole(left, right, leftAlias, rightAlias)) {
            attributes.put("selfJoinRole", true);
            attributes.put("leftAlias", clean(leftAlias));
            attributes.put("rightAlias", clean(rightAlias));
        }
        candidate.evidence().add(new Evidence(
                evidenceType,
                BigDecimal.valueOf(score(evidenceType)),
                evidenceSourceType(statement.sourceType()),
                statement.sourceName(),
                detail,
                attributes));
        return candidate;
    }

    private boolean shouldEmitColumnCoOccurrence(ColumnRef left, ColumnRef right, String leftAlias, String rightAlias) {
        if (normalize(left.displayName()).equals(normalize(right.displayName()))) {
            return isExplicitSelfJoinRole(left, right, leftAlias, rightAlias);
        }
        if (!left.table().equals(right.table())) {
            return true;
        }
        return isExplicitSelfJoinColumnEquality(left, right, leftAlias, rightAlias);
    }

    private boolean isExplicitSelfJoinColumnEquality(ColumnRef left, ColumnRef right, String leftAlias, String rightAlias) {
        return isExplicitSelfJoinRole(left, right, leftAlias, rightAlias)
                && !normalize(left.columnName()).equals(normalize(right.columnName()));
    }

    private boolean isExplicitSelfJoinRole(ColumnRef left, ColumnRef right, String leftAlias, String rightAlias) {
        String normalizedLeftAlias = normalize(leftAlias);
        String normalizedRightAlias = normalize(rightAlias);
        return left.table().equals(right.table())
                && !normalizedLeftAlias.isBlank()
                && !normalizedRightAlias.isBlank()
                && !normalizedLeftAlias.equals(normalizedRightAlias);
    }

    private double score(EvidenceType type) {
        return switch (type) {
            case SQL_LOG_JOIN -> DefaultEvidenceScores.SQL_LOG_JOIN;
            case SQL_LOG_EXISTS -> DefaultEvidenceScores.SQL_LOG_EXISTS;
            case SQL_LOG_SUBQUERY_IN -> DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN;
            default -> DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE;
        };
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private ColumnRef resolve(
            String alias,
            String column,
            Map<String, TableId> aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        if (column.isBlank()) {
            return null;
        }
        ColumnRef projected = projections.get(ColumnKey.of(alias, column));
        if (projected != null) {
            return projected;
        }
        TableId table = aliases.get(normalize(alias));
        if (table == null) {
            return null;
        }
        return ColumnRef.of(table, clean(column));
    }

    private ColumnRef resolveWithFallbackTable(
            String alias,
            String column,
            String fallbackTable,
            Map<String, TableId> aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        ColumnRef resolved = resolve(alias, column, aliases, projections);
        if (resolved != null || fallbackTable.isBlank() || column.isBlank()) {
            return resolved;
        }
        return resolve(fallbackTable, column, aliases, projections);
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

    private EvidenceSourceType evidenceSourceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            default -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private String text(StructuredSqlEvent event, String key) {
        Object value = event.attributes().get(key);
        return value == null ? "" : value.toString();
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

    private record ColumnKey(String alias, String column) {
        static ColumnKey of(String alias, String column) {
            return new ColumnKey(normalizePart(alias), normalizePart(column));
        }

        boolean matches(String rawAlias) {
            return alias.equals(normalizePart(rawAlias));
        }

        private static String normalizePart(String value) {
            String result = value == null ? "" : value.trim();
            if ((result.startsWith("`") && result.endsWith("`"))
                    || (result.startsWith("\"") && result.endsWith("\""))) {
                result = result.substring(1, result.length() - 1);
            }
            return result.toLowerCase(Locale.ROOT);
        }
    }
}
