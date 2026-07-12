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
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.CanonicalIdentifierResolver;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.provenance.EvidenceProvenanceMapper;

/**
 * SQL relationship 语义抽取器。
 *
 * <p>CN: 本类消费 token-event 与 full-grammar 共享的 StructuredSqlEvent，并输出
 * RelationshipCandidate。SQL 谓词只证明列之间共同出现；FK-like 方向只能来自
 * DDL/metadata/data-profile 等结构化方向证据。EXISTS/IN 证据、列级弱共现、JOIN
 * USING 和 self-join 弱关系都在这里统一处理，不放入方言 parser。
 *
 * <p>EN: SQL relationship semantic extractor. It consumes StructuredSqlEvent
 * records shared by token-event and full-grammar and emits RelationshipCandidate
 * instances. SQL predicates prove column co-occurrence only; FK-like direction
 * must come from DDL/metadata/data-profile evidence. EXISTS/IN evidence, column
 * co-occurrence, JOIN USING, and self-join weak relations are handled here
 * rather than in dialect parsers.
 */
public final class StructuredRelationshipExtractor {
    private final CanonicalIdentifierResolver identifiers;

    public StructuredRelationshipExtractor() {
        this(value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT), NamespaceContext.empty());
    }

    public StructuredRelationshipExtractor(IdentifierRules identifierRules) {
        this(identifierRules, NamespaceContext.empty());
    }

    public StructuredRelationshipExtractor(IdentifierRules identifierRules, NamespaceContext namespace) {
        this.identifiers = new CanonicalIdentifierResolver(identifierRules);
    }

    /**
     * 从结构化 SQL events 抽取 relationship 候选。
     *
     * <p>EN: Extracts relationship candidates from structured SQL events.
     */
    public List<RelationshipCandidate> extract(SqlStatementRecord statement, StructuredParseResult structured) {
        return extractNative(statement, structured);
    }

    private List<RelationshipCandidate> extractNative(SqlStatementRecord statement, StructuredParseResult structured) {
        List<RelationshipCandidate> candidates = new ArrayList<>();
        for (List<StructuredSqlEvent> events : scopedEventGroups(structured.events())) {
            candidates.addAll(extractFromEvents(statement, events));
        }
        // Ambient events are intentionally visible in every statement scope so aliases and
        // physical rowsets can be resolved. Deduplicate only after all scopes are assembled;
        // otherwise the same ambient predicate becomes one observation per scope.
        return deduplicate(candidates);
    }

    private List<RelationshipCandidate> extractFromEvents(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> ignoredRowsets = ignoredRowsets(statement, events);
        AliasIndex aliases = rowsetAliases(events, ignoredRowsets);
        Map<ColumnKey, ColumnRef> projections = projectedColumns(events, aliases, ignoredRowsets);
        List<RelationshipCandidate> candidates = new ArrayList<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.PREDICATE_EQUALITY) {
                ColumnRef left = resolve(event.left().alias(), event.left().column(), aliases, projections, event.line());
                ColumnRef right = resolve(event.right().alias(), event.right().column(), aliases, projections, event.line());
                if (left == null || right == null) {
                    continue;
                }
                if (isIgnored(left.table(), ignoredRowsets) || isIgnored(right.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(left, right, event.left().alias(), event.right().alias())) {
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            relationshipEvidenceType(statement, EvidenceType.SQL_LOG_JOIN),
                            event.joinKind(),
                            event.left().alias(),
                            event.right().alias(),
                            event,
                            "typed column equality"));
                }
            } else if (event.type() == StructuredParseEventType.EXISTS_PREDICATE) {
                ColumnRef left = resolve(event.left().alias(), event.left().column(), aliases, projections, event.line());
                ColumnRef right = resolve(event.right().alias(), event.right().column(), aliases, projections, event.line());
                if (left == null || right == null) {
                    continue;
                }
                if (isIgnored(left.table(), ignoredRowsets) || isIgnored(right.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(left, right, event.left().alias(), event.right().alias())) {
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            relationshipEvidenceType(statement, EvidenceType.SQL_LOG_EXISTS),
                            "EXISTS",
                            event.left().alias(),
                            event.right().alias(),
                            event,
                            "typed EXISTS column equality"));
                }
            } else if (event.type() == StructuredParseEventType.JOIN_USING_COLUMNS) {
                String leftAlias = event.left().alias();
                String rightAlias = event.right().alias();
                for (String column : event.usingColumns()) {
                    ColumnRef left = resolve(leftAlias, column, aliases, projections, event.line());
                    ColumnRef right = resolve(rightAlias, column, aliases, projections, event.line());
                    if (left == null || right == null) {
                        continue;
                    }
                    if (isIgnored(left.table(), ignoredRowsets) || isIgnored(right.table(), ignoredRowsets)) {
                        continue;
                    }
                    candidates.add(columnCoOccurrenceCandidate(statement, left, right,
                            relationshipEvidenceType(statement, EvidenceType.SQL_LOG_JOIN),
                            "USING_JOIN",
                            leftAlias,
                            rightAlias,
                            event,
                            "typed JOIN USING column equality"));
                }
            } else if (event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                ColumnRef outer = resolveWithFallbackTable(
                        event.left().alias(),
                        event.left().column(),
                        "",
                        aliases,
                        projections,
                        event.line());
                ColumnRef inner = resolveInSubqueryColumn(event, aliases, projections);
                if (outer == null || inner == null) {
                    continue;
                }
                if (isIgnored(outer.table(), ignoredRowsets) || isIgnored(inner.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(outer, inner, event.left().alias(), event.right().alias())) {
                    candidates.add(columnCoOccurrenceCandidate(statement, outer, inner,
                            relationshipEvidenceType(statement, EvidenceType.SQL_LOG_SUBQUERY_IN),
                            "IN_SUBQUERY",
                            event.left().alias(),
                            event.right().alias(),
                            event,
                            "typed IN subquery column co-occurrence"));
                }
            } else if (event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                List<String> outerAliases = event.outerSources().stream()
                        .map(com.relationdetector.contracts.parse.ExpressionSource::alias).toList();
                List<String> outerColumns = event.outerSources().stream()
                        .map(com.relationdetector.contracts.parse.ExpressionSource::column).toList();
                List<String> innerAliases = event.innerSources().stream()
                        .map(com.relationdetector.contracts.parse.ExpressionSource::alias).toList();
                List<String> innerColumns = event.innerSources().stream()
                        .map(com.relationdetector.contracts.parse.ExpressionSource::column).toList();
                int count = Math.min(Math.min(outerAliases.size(), outerColumns.size()),
                        Math.min(innerAliases.size(), innerColumns.size()));
                for (int index = 0; index < count; index++) {
                    ColumnRef outer = resolve(outerAliases.get(index), outerColumns.get(index), aliases, projections, event.line());
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
                                relationshipEvidenceType(statement, EvidenceType.SQL_LOG_SUBQUERY_IN),
                                "TUPLE_IN_SUBQUERY",
                                outerAliases.get(index),
                                innerAliases.get(index),
                                event,
                                "typed tuple IN subquery column co-occurrence position " + (index + 1)));
                    }
                }
            }
        }
        return candidates;
    }

    private List<List<StructuredSqlEvent>> scopedEventGroups(List<StructuredSqlEvent> events) {
        Map<String, List<StructuredSqlEvent>> scoped = new LinkedHashMap<>();
        List<StructuredSqlEvent> ambient = new ArrayList<>();
        for (StructuredSqlEvent event : events) {
            String scope = event.statementScope();
            if (scope.isBlank()) {
                ambient.add(event);
            } else {
                scoped.computeIfAbsent(scope, ignored -> new ArrayList<>()).add(event);
            }
        }
        if (scoped.isEmpty()) {
            return List.of(events);
        }
        List<List<StructuredSqlEvent>> groups = new ArrayList<>();
        for (List<StructuredSqlEvent> scopeEvents : scoped.values()) {
            List<StructuredSqlEvent> group = new ArrayList<>(ambient.size() + scopeEvents.size());
            group.addAll(ambient);
            group.addAll(scopeEvents);
            groups.add(group);
        }
        return groups;
    }

    private boolean isVerifiedColumnSubquery(StructuredSqlEvent event) {
        return event.verifiedColumnSubquery();
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
        Evidence evidence = candidate.evidence().isEmpty() ? null : candidate.evidence().get(0);
        EvidenceSourceType sourceType = evidence == null ? null : evidence.sourceType();
        return candidate.relationType() + "|"
                + candidate.relationSubType() + "|"
                + endpointKey(candidate) + "|"
                + evidenceType + "|"
                + sourceType + "|"
                + evidenceAttribute(evidence, "sourceFile") + "|"
                + evidenceAttribute(evidence, "sourceStatementId") + "|"
                + evidenceAttribute(evidence, "sourceBlockId") + "|"
                + evidenceAttribute(evidence, "sourceLine") + "|"
                + evidenceAttribute(evidence, "joinKind");
    }

    private String evidenceAttribute(Evidence evidence, String key) {
        if (evidence == null) {
            return "";
        }
        Object value = evidence.attributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private EvidenceType firstEvidenceType(RelationshipCandidate candidate) {
        return candidate.evidence().isEmpty() ? null : candidate.evidence().get(0).type();
    }

    private String endpointKey(RelationshipCandidate candidate) {
        return candidate.source().normalizedKey() + "->" + candidate.target().normalizedKey();
    }

    private AliasIndex rowsetAliases(List<StructuredSqlEvent> events, Set<String> ignoredRowsets) {
        Map<String, TableId> aliases = new LinkedHashMap<>();
        Map<String, List<AliasBinding>> bindings = new LinkedHashMap<>();
        Set<String> ambiguousAliases = new HashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                TableId tableId = tableId(event.targetTable());
                putAlias(aliases, bindings, ambiguousAliases, event.name(), tableId, event.line());
                continue;
            }
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String qualified = event.qualifiedTable();
            String table = event.table();
            String alias = event.alias();
            if (ignoredRowsets.contains(normalize(table))
                    || ignoredRowsets.contains(normalize(qualified))) {
                continue;
            }
            TableId tableId = tableId(qualified.isBlank() ? table : qualified);
            putAlias(aliases, bindings, ambiguousAliases, qualified, tableId, event.line());
            putAlias(aliases, bindings, ambiguousAliases, table, tableId, event.line());
            if (!alias.isBlank()) {
                putAlias(aliases, bindings, ambiguousAliases, alias, tableId, event.line());
            }
        }
        return new AliasIndex(aliases, bindings);
    }

    private void putAlias(
            Map<String, TableId> aliases,
            Map<String, List<AliasBinding>> bindings,
            Set<String> ambiguousAliases,
            String alias,
            TableId tableId,
            long line
    ) {
        String key = normalize(alias);
        if (key.isBlank()) {
            return;
        }
        bindings.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new AliasBinding(tableId, line));
        if (ambiguousAliases.contains(key)) {
            return;
        }
        TableId existing = aliases.get(key);
        if (existing == null || existing.equals(tableId)) {
            aliases.put(key, tableId);
            return;
        }
        aliases.remove(key);
        ambiguousAliases.add(key);
    }

    private Map<ColumnKey, ColumnRef> projectedColumns(
            List<StructuredSqlEvent> events,
            AliasIndex aliases,
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
                if (!isDirectValueProjection(event)) {
                    continue;
                }
                String outputAlias = event.outputAlias();
                String outputColumn = event.outputColumn();
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

    private boolean isDirectValueProjection(StructuredSqlEvent event) {
        return event.expression().transformType() == com.relationdetector.contracts.Enums.LineageTransformType.DIRECT
                && event.expression().flowKind() == com.relationdetector.contracts.Enums.LineageFlowKind.VALUE;
    }

    private ColumnRef firstResolvableSource(
            StructuredSqlEvent event,
            AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        List<String> sourceAliases = event.expression().sourceAliases();
        List<String> sourceColumns = event.expression().sourceColumns();
        int count = Math.min(sourceAliases.size(), sourceColumns.size());
        for (int index = 0; index < count; index++) {
            ColumnRef source = resolve(sourceAliases.get(index), sourceColumns.get(index), aliases, projections, event.line());
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
            String table = event.table();
            String qualified = event.qualifiedTable();
            String alias = event.alias();
            if (alias.isBlank()
                    || (!ignoredRowsets.contains(normalize(table)) && !ignoredRowsets.contains(normalize(qualified)))) {
                continue;
            }
            for (Map.Entry<ColumnKey, ColumnRef> entry : List.copyOf(projections.entrySet())) {
                if (entry.getKey().alias().equals(normalize(table))
                        || entry.getKey().alias().equals(normalize(qualified))) {
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
        ColumnKey key = columnKey(alias, column);
        if (key.alias().isBlank() || key.column().isBlank() || projections.containsKey(key)) {
            return false;
        }
        projections.put(key, source);
        return true;
    }

    private Set<String> ignoredRowsets(SqlStatementRecord statement, List<StructuredSqlEvent> events) {
        Set<String> ignored = new HashSet<>();
        for (String table : stringList(statement.attributes().get("localTempTables"))) {
            addIgnored(ignored, table);
        }
        for (StructuredSqlEvent event : events) {
            if (event.type() == StructuredParseEventType.TRIGGER_PSEUDO_ROWSET) {
                addIgnored(ignored, event.name());
                continue;
            }
            if (event.type() == StructuredParseEventType.IGNORED_ROWSET
                    || event.type() == StructuredParseEventType.CTE_DECLARATION
                    || event.type() == StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                addIgnored(ignored, event.name());
                addIgnored(ignored, event.table());
                addIgnored(ignored, event.qualifiedTable());
            }
        }
        return ignored;
    }

    private void addIgnored(Set<String> ignored, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        ignored.add(normalize(raw));
    }

    private boolean isIgnored(TableId table, Set<String> ignoredRowsets) {
        if (isSystemSchema(table.schema())) {
            return true;
        }
        if (table.schema() != null && !table.schema().isBlank()) {
            return ignoredRowsets.contains(normalize(table.schema() + "." + table.tableName()));
        }
        return ignoredRowsets.contains(normalize(table.tableName()));
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
            AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        ColumnRef byAlias = resolve(event.right().alias(), event.right().column(), aliases, projections, event.line());
        if (byAlias != null) {
            return byAlias;
        }
        return resolveTupleInSubqueryColumn(
                event, event.right().alias(), event.right().column(), aliases, projections);
    }

    private ColumnRef resolveTupleInSubqueryColumn(
            StructuredSqlEvent event,
            String alias,
            String column,
            AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections
    ) {
        ColumnRef byAlias = resolve(alias, column, aliases, projections, event.line());
        if (byAlias != null) {
            return byAlias;
        }
        String table = event.innerTable();
        if (table.isBlank() || column.isBlank()) {
            return null;
        }
        return column(tableId(table), column);
    }

    private RelationshipCandidate columnCoOccurrenceCandidate(
            SqlStatementRecord statement,
            ColumnRef left,
            ColumnRef right,
            EvidenceType evidenceType,
            String joinKind,
            String leftAlias,
            String rightAlias,
            StructuredSqlEvent event,
            String detail
    ) {
        ColumnRef first = left;
        ColumnRef second = right;
        if (outputOrderKey(left).compareTo(outputOrderKey(right)) > 0) {
            first = right;
            second = left;
        }
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(first),
                Endpoint.column(second),
                RelationType.CO_OCCURRENCE,
                RelationSubType.COLUMN_CO_OCCURRENCE);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("joinKind", canonicalJoinKind(joinKind));
        EvidenceProvenanceMapper.copy(statement, event, attributes);
        if (isExplicitSelfJoinRole(left, right, leftAlias, rightAlias)) {
            attributes.put("selfJoinRole", true);
            attributes.put("leftAlias", clean(leftAlias));
            attributes.put("rightAlias", clean(rightAlias));
        }
        candidate.evidence().add(new Evidence(
                evidenceType,
                BigDecimal.valueOf(score(evidenceType)),
                evidenceSourceType(statement.sourceType()),
                SourceNameNormalizer.normalize(statement.sourceName()),
                detail,
                attributes));
        return candidate;
    }

    private String canonicalJoinKind(String raw) {
        String value = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT).replace("_", "");
        if (value.contains("LEFT")) {
            return "LEFT_JOIN";
        }
        if (value.contains("RIGHT")) {
            return "RIGHT_JOIN";
        }
        if (value.contains("FULL")) {
            return "FULL_JOIN";
        }
        if (value.contains("CROSS")) {
            return "CROSS_JOIN";
        }
        if (value.contains("APPLY")) {
            return value.contains("OUTER") ? "OUTER_APPLY" : "CROSS_APPLY";
        }
        if (value.contains("EXISTS")) {
            return "EXISTS";
        }
        if (value.contains("IN_SUBQUERY") || value.contains("INSUBQUERY")) {
            return "IN_SUBQUERY";
        }
        if (value.contains("MERGE")) {
            return "MERGE_ON";
        }
        if (value.equals("JOIN") || value.equals("JOINON") || value.equals("INNER")
                || value.equals("INNERJOIN")) {
            return "JOIN_ON";
        }
        return "WHERE_OR_UNKNOWN";
    }

    private String outputOrderKey(ColumnRef column) {
        return column.displayName().strip().toLowerCase(Locale.ROOT);
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
            case VIEW_JOIN -> DefaultEvidenceScores.VIEW_JOIN;
            case SQL_LOG_JOIN -> DefaultEvidenceScores.SQL_LOG_JOIN;
            case SQL_LOG_EXISTS -> DefaultEvidenceScores.SQL_LOG_EXISTS;
            case SQL_LOG_SUBQUERY_IN -> DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN;
            default -> DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE;
        };
    }

    private EvidenceType relationshipEvidenceType(SqlStatementRecord statement, EvidenceType predicateType) {
        return statement.sourceType() == com.relationdetector.contracts.Enums.StatementSourceType.VIEW
                || statement.sourceType() == com.relationdetector.contracts.Enums.StatementSourceType.MATERIALIZED_VIEW
                ? EvidenceType.VIEW_JOIN : predicateType;
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
            AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections,
            long eventLine
    ) {
        if (column.isBlank()) {
            return null;
        }
        ColumnRef projected = projections.get(columnKey(alias, column));
        if (projected != null) {
            return projected;
        }
        TableId table = aliases.resolve(alias, eventLine);
        if (table == null) {
            return null;
        }
        return column(table, column);
    }

    private ColumnRef resolveWithFallbackTable(
            String alias,
            String column,
            String fallbackTable,
            AliasIndex aliases,
            Map<ColumnKey, ColumnRef> projections,
            long eventLine
    ) {
        ColumnRef resolved = resolve(alias, column, aliases, projections, eventLine);
        if (resolved != null || fallbackTable.isBlank() || column.isBlank()) {
            return resolved;
        }
        return resolve(fallbackTable, column, aliases, projections, eventLine);
    }

    private TableId tableId(String qualified) {
        // Parser facts preserve only the namespace explicitly present in SQL.
        return identifiers.resolveQualified(qualified, NamespaceContext.empty());
    }

    private ColumnRef column(TableId table, String name) {
        String clean = clean(name);
        return ColumnRef.of(table, clean);
    }

    private EvidenceSourceType evidenceSourceType(StatementSourceType sourceType) {
        return switch (sourceType) {
            case PROCEDURE, FUNCTION, VIEW, MATERIALIZED_VIEW, TRIGGER, EVENT, RULE, PACKAGE, PACKAGE_BODY ->
                    EvidenceSourceType.DATABASE_OBJECT;
            case NATIVE_LOG -> EvidenceSourceType.NATIVE_LOG;
            default -> EvidenceSourceType.PLAIN_SQL;
        };
    }

    private String clean(String value) {
        String result = value == null ? "" : value.trim();
        if ((result.startsWith("`") && result.endsWith("`")) || (result.startsWith("\"") && result.endsWith("\""))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }

    private String normalize(String value) {
        return identifiers.normalize(clean(value));
    }

    private record ColumnKey(String alias, String column) {
    }

    private ColumnKey columnKey(String alias, String column) {
        return new ColumnKey(normalize(alias), normalize(column));
    }

    private final class AliasIndex {
        private final Map<String, TableId> uniqueAliases;
        private final Map<String, List<AliasBinding>> bindings;

        private AliasIndex(Map<String, TableId> uniqueAliases, Map<String, List<AliasBinding>> bindings) {
            this.uniqueAliases = uniqueAliases;
            this.bindings = bindings;
        }

        private TableId resolve(String alias, long eventLine) {
            String key = normalize(alias);
            TableId unique = uniqueAliases.get(key);
            if (unique != null) {
                return unique;
            }
            List<AliasBinding> candidates = bindings.getOrDefault(key, List.of()).stream()
                    .filter(binding -> binding.line() <= eventLine)
                    .toList();
            if (candidates.isEmpty()) {
                List<AliasBinding> allBindings = bindings.getOrDefault(key, List.of());
                return allBindings.size() == 1 ? allBindings.get(0).tableId() : null;
            }
            long nearestLine = candidates.stream()
                    .mapToLong(AliasBinding::line)
                    .max()
                    .orElse(Long.MIN_VALUE);
            List<TableId> nearestTables = candidates.stream()
                    .filter(binding -> binding.line() == nearestLine)
                    .map(AliasBinding::tableId)
                    .distinct()
                    .toList();
            return nearestTables.size() == 1 ? nearestTables.get(0) : null;
        }
    }

    private record AliasBinding(TableId tableId, long line) {
    }
}
