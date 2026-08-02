package com.relationdetector.core.relation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.PredicateJoinKind;
import com.relationdetector.contracts.model.ColumnRef;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.IdentifierRules;
import com.relationdetector.core.identity.NamespaceContext;
import com.relationdetector.core.log.SourceNameNormalizer;
import com.relationdetector.core.provenance.EvidenceProvenanceMapper;

/**
 * CN: 在 typed endpoints 已解析后创建 relationship candidate、方向稳定的 evidence 与 guard attributes；上游是
 * relationship extractor，下游是 relationship merger，本类不解析 SQL、不执行 naming rule，也不合并最终事实。
 * EN: Creates relationship candidates, direction-stable evidence, and guard attributes after typed endpoints have
 * been resolved. It connects the relationship extractor to the merger and never parses SQL, executes naming rules,
 * or merges final facts.
 */
abstract class RelationshipCandidateFactory extends RelationshipAliasResolver {
    protected RelationshipCandidateFactory(IdentifierRules identifierRules, NamespaceContext namespace) {
        super(identifierRules, namespace);
    }

    protected RelationshipCandidate columnCoOccurrenceCandidate(SqlStatementRecord statement,
            ColumnRef left, ColumnRef right, EvidenceType evidenceType, String joinKind,
            String leftAlias, String rightAlias, AliasIndex aliases, StructuredSqlEvent event, String detail) {
        return columnCoOccurrenceCandidate(statement, left, right, evidenceType, joinKind,
                leftAlias, rightAlias, aliases, event, detail, Map.of());
    }

    protected RelationshipCandidate columnCoOccurrenceCandidate(SqlStatementRecord statement,
            ColumnRef left, ColumnRef right, EvidenceType evidenceType, String joinKind,
            String leftAlias, String rightAlias, AliasIndex aliases, StructuredSqlEvent event,
            String detail, Map<String, Object> additionalAttributes) {
        ColumnRef first = left;
        ColumnRef second = right;
        if (outputOrderKey(left).compareTo(outputOrderKey(right)) > 0) {
            first = right;
            second = left;
        }
        RelationshipCandidate candidate = new RelationshipCandidate(
                Endpoint.column(first), Endpoint.column(second),
                RelationType.CO_OCCURRENCE, RelationSubType.COLUMN_CO_OCCURRENCE);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("joinKind", canonicalJoinKind(joinKind));
        EvidenceProvenanceMapper.copy(statement, event, attributes);
        copyPredicateGuardAttributes(event, aliases, attributes);
        attributes.putAll(additionalAttributes);
        if (isExplicitSelfJoinRole(left, right, leftAlias, rightAlias)) {
            attributes.put("selfJoinRole", true);
            attributes.put("leftAlias", clean(leftAlias));
            attributes.put("rightAlias", clean(rightAlias));
        }
        candidate.evidence().add(new Evidence(evidenceType, BigDecimal.valueOf(score(evidenceType)),
                evidenceSourceType(statement.sourceType()), SourceNameNormalizer.normalize(statement.sourceName()),
                detail, attributes));
        return candidate;
    }

    private void copyPredicateGuardAttributes(StructuredSqlEvent event, AliasIndex aliases,
            Map<String, Object> attributes) {
        List<RelationshipCondition> conditions = new ArrayList<>();
        for (com.relationdetector.contracts.parse.PredicateGuard guard : event.predicateGuards()) {
            ColumnRef discriminator = resolve(guard.discriminator().alias(), guard.discriminator().column(),
                    aliases, Map.of(), event.line());
            if (discriminator == null || guard.operator().isBlank()) continue;
            conditions.add(new RelationshipCondition(
                    Endpoint.column(discriminator), guard.operator(), guard.literalValue()));
        }
        if (conditions.isEmpty()) return;
        RelationshipConditionAttributes.write(attributes,
                conditions.stream().map(RelationshipCondition::attributes).toList());
    }

    private String canonicalJoinKind(String raw) {
        PredicateJoinKind kind = PredicateJoinKind.valueOf(raw);
        return switch (kind) {
            case LEFT_JOIN -> "LEFT_JOIN";
            case RIGHT_JOIN -> "RIGHT_JOIN";
            case FULL_JOIN -> "FULL_JOIN";
            case CROSS_JOIN -> "CROSS_JOIN";
            case EXISTS -> "EXISTS";
            case IN_SUBQUERY, TUPLE_IN_SUBQUERY -> "IN_SUBQUERY";
            case MERGE_ON, MERGE_OR_USING -> "MERGE_ON";
            case JOIN, JOIN_ON -> "JOIN_ON";
            case WHERE_OR_UNKNOWN, STRAIGHT_JOIN, USING_JOIN -> "WHERE_OR_UNKNOWN";
        };
    }

    private String outputOrderKey(ColumnRef column) {
        return column.displayName().strip().toLowerCase(Locale.ROOT);
    }

    protected boolean shouldEmitColumnCoOccurrence(ColumnRef left, ColumnRef right,
            String leftAlias, String rightAlias) {
        if (normalize(left.displayName()).equals(normalize(right.displayName()))) {
            return isExplicitSelfJoinRole(left, right, leftAlias, rightAlias);
        }
        return !sameTable(left.table(), right.table())
                || isExplicitSelfJoinColumnEquality(left, right, leftAlias, rightAlias);
    }

    private boolean isExplicitSelfJoinColumnEquality(ColumnRef left, ColumnRef right,
            String leftAlias, String rightAlias) {
        return isExplicitSelfJoinRole(left, right, leftAlias, rightAlias)
                && !normalize(left.columnName()).equals(normalize(right.columnName()));
    }

    private boolean isExplicitSelfJoinRole(ColumnRef left, ColumnRef right,
            String leftAlias, String rightAlias) {
        String normalizedLeftAlias = normalize(leftAlias);
        String normalizedRightAlias = normalize(rightAlias);
        return sameTable(left.table(), right.table())
                && !normalizedLeftAlias.isBlank() && !normalizedRightAlias.isBlank()
                && !normalizedLeftAlias.equals(normalizedRightAlias);
    }

    private double score(EvidenceType type) {
        return switch (type) {
            case VIEW_JOIN -> DefaultEvidenceScores.VIEW_JOIN;
            case TRIGGER_REFERENCE -> DefaultEvidenceScores.TRIGGER_REFERENCE;
            case SQL_LOG_JOIN -> DefaultEvidenceScores.SQL_LOG_JOIN;
            case SQL_LOG_EXISTS -> DefaultEvidenceScores.SQL_LOG_EXISTS;
            case SQL_LOG_SUBQUERY_IN -> DefaultEvidenceScores.SQL_LOG_SUBQUERY_IN;
            default -> DefaultEvidenceScores.SQL_LOG_COLUMN_CO_OCCURRENCE;
        };
    }

    protected EvidenceType relationshipEvidenceType(SqlStatementRecord statement, EvidenceType predicateType) {
        if (statement.sourceType() == StatementSourceType.VIEW
                || statement.sourceType() == StatementSourceType.MATERIALIZED_VIEW) return EvidenceType.VIEW_JOIN;
        if (statement.sourceType() == StatementSourceType.TRIGGER
                || Boolean.TRUE.equals(statement.attributes().get("routineReturnsTrigger"))) {
            return EvidenceType.TRIGGER_REFERENCE;
        }
        return predicateType;
    }
}
