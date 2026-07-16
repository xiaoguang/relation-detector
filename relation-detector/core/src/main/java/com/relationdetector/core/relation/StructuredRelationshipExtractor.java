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
public final class StructuredRelationshipExtractor extends RelationshipCandidateSupport {

    public StructuredRelationshipExtractor() {
        this(value -> value == null ? "" : value.strip().toLowerCase(Locale.ROOT), NamespaceContext.empty());
    }

    public StructuredRelationshipExtractor(IdentifierRules identifierRules) {
        this(identifierRules, NamespaceContext.empty());
    }

    public StructuredRelationshipExtractor(IdentifierRules identifierRules, NamespaceContext namespace) {
        super(identifierRules, namespace);
    }

    /**
     *
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

    /**
     * CN: 在单一 typed statement scope 内解析别名和投影，并把等值、EXISTS、USING 与 IN 事件转换为关系候选；
     * 本方法不运行命名规则，也不依据列名决定关系方向。
     * EN: Resolves aliases and projections inside one typed statement scope and converts equality, EXISTS, USING,
     * and IN events into candidates; it neither runs naming rules nor infers direction from identifier spelling.
     */
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
                            aliases,
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
                            aliases,
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
                            aliases,
                            event,
                            "typed JOIN USING column equality"));
                }
            } else if (event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                if (isIgnoredRawRowset(event.innerTable(), ignoredRowsets)) {
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
                            aliases,
                            event,
                            "typed IN subquery column co-occurrence"));
                }
            } else if (event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                if (isIgnoredRawRowset(event.innerTable(), ignoredRowsets)) {
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
                                aliases,
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
                + evidenceAttribute(evidence, "joinKind") + "|"
                + (evidence == null ? "" : RelationshipConditionAttributes.identity(evidence.attributes()));
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
        return endpointIdentityKey(candidate.source()) + "->" + endpointIdentityKey(candidate.target());
    }

}
