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
 *
 * Builds relationship candidates and evidence after typed endpoints have been resolved.
 */
abstract class RelationshipCandidateSupport extends RelationshipAliasSupport {
    protected RelationshipCandidateSupport(IdentifierRules identifierRules, NamespaceContext namespace) {
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
        String value = raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT).replace("_", "");
        if (value.contains("LEFT")) return "LEFT_JOIN";
        if (value.contains("RIGHT")) return "RIGHT_JOIN";
        if (value.contains("FULL")) return "FULL_JOIN";
        if (value.contains("CROSS")) return "CROSS_JOIN";
        if (value.contains("APPLY")) return value.contains("OUTER") ? "OUTER_APPLY" : "CROSS_APPLY";
        if (value.contains("EXISTS")) return "EXISTS";
        if (value.contains("IN_SUBQUERY") || value.contains("INSUBQUERY")) return "IN_SUBQUERY";
        if (value.contains("MERGE")) return "MERGE_ON";
        if (value.equals("JOIN") || value.equals("JOINON") || value.equals("INNER")
                || value.equals("INNERJOIN")) return "JOIN_ON";
        return "WHERE_OR_UNKNOWN";
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
