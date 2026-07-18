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
import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
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
        List<List<StructuredSqlEvent>> eventGroups = scopedEventGroups(structured.events());
        Set<String> localTempRowsets = localTempRowsets(statement, structured.events());
        LocalRowsetProjectionIndex localProjections = localRowsetProjectionIndex(
                statement, eventGroups, localTempRowsets);
        for (List<StructuredSqlEvent> events : eventGroups) {
            candidates.addAll(extractFromEvents(statement, events, localTempRowsets, localProjections));
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
    private List<RelationshipCandidate> extractFromEvents(SqlStatementRecord statement, List<StructuredSqlEvent> events,
            Set<String> localTempRowsets, LocalRowsetProjectionIndex localProjections) {
        Set<String> ignoredRowsets = ignoredRowsets(statement, events);
        AliasIndex aliases = rowsetAliases(events, ignoredRowsets);
        Map<ColumnKey, ColumnRef> projections = projectedColumns(events, aliases, ignoredRowsets);
        Map<String, String> localAliases = localRowsetAliases(events, localTempRowsets);
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
                ColumnRef outer = resolveWithFallbackTable(
                        event.left().alias(),
                        event.left().column(),
                        "",
                        aliases,
                        projections,
                        event.line());
                String localInnerRowset = localPredicateRowset(
                        event.innerTable(), event.right().alias(), localAliases, localTempRowsets);
                LocalRowsetProjectionIndex.ResolvedSource bridge = null;
                ColumnRef inner;
                if (!localInnerRowset.isBlank()) {
                    bridge = localProjections.resolve(
                            localInnerRowset, event.right().column(), event.line()).orElse(null);
                    inner = bridge == null ? null : bridge.column();
                } else {
                    inner = resolveInSubqueryColumn(event, aliases, projections);
                }
                if (outer == null || inner == null) {
                    continue;
                }
                if (isIgnored(outer.table(), ignoredRowsets) || isIgnored(inner.table(), ignoredRowsets)) {
                    continue;
                }
                if (shouldEmitColumnCoOccurrence(outer, inner, event.left().alias(), event.right().alias())) {
                    Map<String, Object> bridgeAttributes = bridgeAttributes(bridge);
                    candidates.add(columnCoOccurrenceCandidate(statement, outer, inner,
                            relationshipEvidenceType(statement, EvidenceType.SQL_LOG_SUBQUERY_IN),
                            "IN_SUBQUERY",
                            event.left().alias(),
                            event.right().alias(),
                            aliases,
                            event,
                            bridge == null ? "typed IN subquery column co-occurrence"
                                    : "typed IN subquery through direct local-rowset projection",
                            bridgeAttributes));
                }
            } else if (event.type() == StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE) {
                if (!isVerifiedColumnSubquery(event)) {
                    continue;
                }
                String firstInnerAlias = event.innerSources().isEmpty()
                        ? event.right().alias() : event.innerSources().get(0).alias();
                String localInnerRowset = localPredicateRowset(
                        event.innerTable(), firstInnerAlias, localAliases, localTempRowsets);
                if (localInnerRowset.isBlank() && isIgnoredRawRowset(event.innerTable(), ignoredRowsets)) {
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
                    LocalRowsetProjectionIndex.ResolvedSource bridge = !localInnerRowset.isBlank()
                            ? localProjections.resolve(localInnerRowset, innerColumns.get(index), event.line()).orElse(null)
                            : null;
                    ColumnRef inner = !localInnerRowset.isBlank()
                            ? bridge == null ? null : bridge.column()
                            : resolveTupleInSubqueryColumn(
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
                                bridge == null
                                        ? "typed tuple IN subquery column co-occurrence position " + (index + 1)
                                        : "typed tuple IN subquery through direct local-rowset projection position "
                                                + (index + 1),
                                bridgeAttributes(bridge)));
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * CN: 从 routine 内已声明的本地 rowset 及其谓词前 VALUE/DIRECT 写入建立投影索引，
     * 供 IN/tuple-IN 折叠到唯一物理列。歧义、变换、循环和重声明由索引保守拒绝，
     * 本方法不把临时 rowset 发布为物理 endpoint。
     *
     * EN: Builds the routine-local projection index from typed declarations and
     * pre-predicate VALUE/DIRECT writes so IN predicates can fold to one physical
     * column. Ambiguous, transformed, cyclic, or redeclared mappings are rejected;
     * local rowsets are never emitted as physical endpoints.
     */
    private LocalRowsetProjectionIndex localRowsetProjectionIndex(
            SqlStatementRecord statement,
            List<List<StructuredSqlEvent>> eventGroups,
            Set<String> localTempRowsets
    ) {
        LocalRowsetProjectionIndex index = new LocalRowsetProjectionIndex(
                this::normalize, column -> endpointIdentityKey(Endpoint.column(column)));
        for (List<StructuredSqlEvent> events : eventGroups) {
            for (StructuredSqlEvent event : events) {
                if (event.type() == StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION) {
                    String declared = localRowsetIdentity(event);
                    if (!declared.isBlank()) {
                        index.declare(declared, event.line());
                    }
                }
            }
        }
        for (List<StructuredSqlEvent> events : eventGroups) {
            Set<String> ignored = ignoredRowsets(statement, events);
            AliasIndex aliases = rowsetAliases(events, ignored);
            Map<ColumnKey, ColumnRef> projections = projectedColumns(events, aliases, ignored);
            Map<String, String> localAliases = localRowsetAliases(events, localTempRowsets);
            for (StructuredSqlEvent event : events) {
                if (event.type() != StructuredParseEventType.INSERT_SELECT_MAPPING
                        || !isIgnoredRawRowset(event.targetTable(), localTempRowsets)) {
                    continue;
                }
                if (event.expression().flowKind() != LineageFlowKind.VALUE) {
                    continue;
                }
                if (event.expression().transformType() != LineageTransformType.DIRECT
                        || event.expression().sources().size() != 1) {
                    index.block(event.targetTable(), event.targetColumn(), event.line());
                    continue;
                }
                com.relationdetector.contracts.parse.ExpressionSource source = event.expression().sources().get(0);
                ColumnRef physical = resolve(
                        source.alias(), source.column(), aliases, projections, event.line());
                if (physical != null && !isIgnored(physical.table(), ignored)) {
                    index.addPhysicalSource(
                            event.targetTable(), event.targetColumn(), physical, event.line());
                    continue;
                }
                String localSource = localSourceRowset(source.alias(), localAliases);
                if (!localSource.isBlank()) {
                    index.addLocalSource(event.targetTable(), event.targetColumn(),
                            localSource, source.column(), event.line());
                } else {
                    index.block(event.targetTable(), event.targetColumn(), event.line());
                }
            }
        }
        return index;
    }

    private Map<String, String> localRowsetAliases(
            List<StructuredSqlEvent> events,
            Set<String> localTempRowsets
    ) {
        Map<String, String> aliases = new LinkedHashMap<>();
        Set<String> rowsets = new HashSet<>();
        for (StructuredSqlEvent event : events) {
            if (event.type() != StructuredParseEventType.ROWSET_REFERENCE) {
                continue;
            }
            String rowset = event.qualifiedTable().isBlank() ? event.table() : event.qualifiedTable();
            if (!isIgnoredRawRowset(rowset, localTempRowsets)) {
                continue;
            }
            rowsets.add(rowset);
            putLocalAlias(aliases, event.qualifiedTable(), rowset);
            putLocalAlias(aliases, event.table(), rowset);
            putLocalAlias(aliases, event.alias(), rowset);
        }
        if (rowsets.size() == 1) {
            aliases.put("", rowsets.iterator().next());
        }
        return aliases;
    }

    private void putLocalAlias(Map<String, String> aliases, String alias, String rowset) {
        if (alias != null && !alias.isBlank()) {
            aliases.putIfAbsent(normalize(alias), rowset);
        }
    }

    private String localSourceRowset(String alias, Map<String, String> localAliases) {
        return localAliases.getOrDefault(normalize(alias), "");
    }

    private String localPredicateRowset(
            String innerTable,
            String innerAlias,
            Map<String, String> localAliases,
            Set<String> localTempRowsets
    ) {
        if (isIgnoredRawRowset(innerTable, localTempRowsets)) {
            return innerTable;
        }
        return localSourceRowset(innerAlias, localAliases);
    }

    private String localRowsetIdentity(StructuredSqlEvent event) {
        if (!event.qualifiedTable().isBlank()) {
            return event.qualifiedTable();
        }
        if (!event.table().isBlank()) {
            return event.table();
        }
        return event.name();
    }

    private Map<String, Object> bridgeAttributes(LocalRowsetProjectionIndex.ResolvedSource bridge) {
        if (bridge == null) {
            return Map.of();
        }
        return Map.of(
                "localRowsetBridge", true,
                "localRowsetPath", bridge.path(),
                "localRowsetSourceLine", bridge.sourceLine());
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
