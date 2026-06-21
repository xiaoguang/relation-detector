package com.relationdetector.core.relation;

import com.relationdetector.core.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.api.ColumnRef;
import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Endpoint;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.StructuredSqlEvent;
import com.relationdetector.api.TableId;
import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * Relationship extractor for token-event.
 *
 * <p>The extractor consumes structured token-event records directly and is the
 * production relationship extraction architecture.
 */
public final class TokenEventRelationExtractor {
    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult structured) {
        return extractNative(statement, structured);
    }

    private List<RelationshipCandidate> extractNative(SqlStatementRecord statement, StructuredParseResult structured) {
        Set<String> ignoredRowsets = ignoredRowsets(structured.events());
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
                RelationshipCandidate candidate = fkLikeCandidate(statement, left, right, text(event, "joinKind"));
                if (candidate != null) {
                    candidates.add(candidate);
                } else if (!left.table().equals(right.table())) {
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            text(event, "joinKind"),
                            "ANTLR token-event ambiguous column equality"));
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
                RelationshipCandidate candidate = fkLikeCandidate(
                        statement,
                        left,
                        right,
                        EvidenceType.SQL_LOG_EXISTS,
                        DefaultEvidenceScores.SQL_LOG_EXISTS,
                        "EXISTS",
                        "ANTLR token-event EXISTS predicate");
                if (candidate != null) {
                    candidates.add(candidate);
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
                            "USING_JOIN",
                            "ANTLR token-event JOIN USING column equality"));
                }
            } else if (event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE) {
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
                RelationshipCandidate candidate = subqueryInCandidate(statement, outer, inner,
                        "ANTLR token-event IN subquery predicate");
                if (candidate != null) {
                    candidates.add(candidate);
                }
            } else if (event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE) {
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
                    RelationshipCandidate candidate = subqueryInCandidate(statement, outer, inner,
                            "ANTLR token-event tuple IN subquery predicate position " + (index + 1));
                    if (candidate != null) {
                        candidates.add(candidate);
                    }
                }
            }
        }
        return deduplicate(removeJoinCandidatesCoveredByExists(candidates));
    }

    private List<RelationshipCandidate> removeJoinCandidatesCoveredByExists(List<RelationshipCandidate> candidates) {
        Set<String> existsEndpoints = new HashSet<>();
        for (RelationshipCandidate candidate : candidates) {
            if (firstEvidenceType(candidate) == EvidenceType.SQL_LOG_EXISTS) {
                existsEndpoints.add(endpointKey(candidate));
            }
        }
        if (existsEndpoints.isEmpty()) {
            return candidates;
        }
        List<RelationshipCandidate> filtered = new ArrayList<>();
        for (RelationshipCandidate candidate : candidates) {
            EvidenceType type = firstEvidenceType(candidate);
            if (existsEndpoints.contains(endpointKey(candidate))
                    && (type == EvidenceType.SQL_LOG_JOIN
                    || type == EvidenceType.VIEW_JOIN
                    || type == EvidenceType.PROCEDURE_JOIN)) {
                continue;
            }
            filtered.add(candidate);
        }
        return filtered;
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

    private Set<String> ignoredRowsets(List<StructuredSqlEvent> events) {
        Set<String> ignored = new HashSet<>();
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

    private RelationshipCandidate fkLikeCandidate(
            SqlStatementRecord statement,
            ColumnRef left,
            ColumnRef right,
            String joinKind
    ) {
        return fkLikeCandidate(
                statement,
                left,
                right,
                joinEvidenceType(statement.sourceType()),
                joinScore(statement.sourceType()),
                joinKind.isBlank() ? "WHERE_OR_UNKNOWN" : joinKind,
                "ANTLR token-event equality");
    }

    private RelationshipCandidate fkLikeCandidate(
            SqlStatementRecord statement,
            ColumnRef left,
            ColumnRef right,
            EvidenceType evidenceType,
            double evidenceScore,
            String joinKind,
            String detail
    ) {
        boolean leftLooksSource = looksLikeSource(left, right);
        boolean rightLooksSource = looksLikeSource(right, left);
        if (leftLooksSource == rightLooksSource) {
            return null;
        }
        ColumnRef source = leftLooksSource ? left : right;
        ColumnRef target = leftLooksSource ? right : left;
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(source),
                Endpoint.column(target),
                RelationType.FK_LIKE,
                RelationSubType.INFERRED_JOIN_FK);
        candidate.evidence().add(new Evidence(
                evidenceType,
                BigDecimal.valueOf(evidenceScore),
                evidenceSourceType(statement.sourceType()),
                statement.sourceName(),
                detail,
                Map.of("joinKind", joinKind.isBlank() ? "WHERE_OR_UNKNOWN" : joinKind,
                        "tokenEventNative", true)));
        return candidate;
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

    private RelationshipCandidate subqueryInCandidate(
            SqlStatementRecord statement,
            ColumnRef outer,
            ColumnRef inner,
            String detail
    ) {
        if (outer.table().equals(inner.table())) {
            return null;
        }
        boolean outerLooksSource = looksLikeSource(outer, inner);
        boolean innerLooksSource = looksLikeSource(inner, outer);
        ColumnRef source = innerLooksSource && !outerLooksSource ? inner : outer;
        ColumnRef target = innerLooksSource && !outerLooksSource ? outer : inner;
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(source),
                Endpoint.column(target),
                RelationType.FK_LIKE,
                RelationSubType.SUBQUERY_INFERRED_FK);
        candidate.evidence().add(new Evidence(
                EvidenceType.SQL_LOG_SUBQUERY_IN,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN),
                evidenceSourceType(statement.sourceType()),
                statement.sourceName(),
                detail,
                Map.of("joinKind", "IN_SUBQUERY",
                        "tokenEventNative", true)));
        return candidate;
    }

    private RelationshipCandidate columnCoOccurrenceCandidate(
            SqlStatementRecord statement,
            ColumnRef left,
            ColumnRef right,
            String joinKind,
            String detail
    ) {
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(left),
                Endpoint.column(right),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        candidate.evidence().add(new Evidence(
                EvidenceType.SQL_LOG_COLUMN_CO_OCCURRENCE,
                BigDecimal.valueOf(DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE),
                evidenceSourceType(statement.sourceType()),
                statement.sourceName(),
                detail,
                Map.of("joinKind", joinKind.isBlank() ? "WHERE_OR_UNKNOWN" : joinKind,
                        "tokenEventNative", true)));
        return candidate;
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private boolean looksLikeSource(ColumnRef source, ColumnRef target) {
        String sourceColumn = normalize(source.columnName());
        String targetColumn = normalize(target.columnName());
        String targetTable = normalize(target.table().tableName());
        if (targetColumn.equals("id") && sourceColumn.endsWith("_id")) {
            String singular = targetTable.endsWith("s") ? targetTable.substring(0, targetTable.length() - 1) : targetTable;
            return sourceColumn.equals(singular + "_id")
                    || sourceColumn.endsWith("_" + singular + "_id")
                    || sourceColumn.endsWith("_id");
        }
        return false;
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

    private EvidenceType joinEvidenceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case VIEW, MATERIALIZED_VIEW, RULE -> EvidenceType.VIEW_JOIN;
            case PROCEDURE, FUNCTION, EVENT, PACKAGE, PACKAGE_BODY -> EvidenceType.PROCEDURE_JOIN;
            case TRIGGER -> EvidenceType.TRIGGER_REFERENCE;
            default -> EvidenceType.SQL_LOG_JOIN;
        };
    }

    private double joinScore(StatementSourceType sourceType) {
        return switch (sourceType) {
            case VIEW, MATERIALIZED_VIEW, RULE -> DefaultEvidenceScores.VIEW_JOIN;
            case PROCEDURE, FUNCTION, EVENT, PACKAGE, PACKAGE_BODY -> DefaultEvidenceScores.PROCEDURE_JOIN;
            case TRIGGER -> DefaultEvidenceScores.TRIGGER_REFERENCE;
            default -> DefaultEvidenceScores.SQL_LOG_JOIN;
        };
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
