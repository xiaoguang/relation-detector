package com.relationdetector.core.relation;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.RelationshipCandidate;

/**
 * CN: 按明确的 evidence 优先级为已合并 relationship 选择最强 subtype，不重新推断结构。
 * EN: Resolves the strongest relationship subtype from merged evidence using explicit priority without re-inferring structure.
 */
final class RelationshipSubtypeResolver {
    RelationSubType resolve(RelationshipCandidate candidate) {
        if (candidate.relationType() == RelationType.CO_OCCURRENCE) {
            return candidate.source().isColumnLevel() && candidate.target().isColumnLevel()
                    ? RelationSubType.COLUMN_CO_OCCURRENCE : RelationSubType.TABLE_CO_OCCURRENCE;
        }
        RelationSubType current = candidate.relationSubType();
        for (var evidence : candidate.evidence()) current = dominant(current, fromEvidence(evidence.type()));
        return current;
    }

    private RelationSubType fromEvidence(EvidenceType type) {
        return switch (type) {
            case METADATA_FOREIGN_KEY -> RelationSubType.DECLARED_FK;
            case DDL_FOREIGN_KEY -> RelationSubType.DDL_DECLARED_FK;
            case VALUE_CONTAINMENT_HIGH, VALUE_OVERLAP_HIGH -> RelationSubType.PROFILE_SUPPORTED_FK;
            case VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE, SQL_LOG_JOIN -> RelationSubType.INFERRED_JOIN_FK;
            case SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS -> RelationSubType.SUBQUERY_INFERRED_FK;
            case NAMING_MATCH -> RelationSubType.NAMING_SUPPORTED_FK;
            case SQL_LOG_COLUMN_CO_OCCURRENCE -> RelationSubType.COLUMN_CO_OCCURRENCE;
            case SQL_LOG_TABLE_CO_OCCURRENCE -> RelationSubType.TABLE_CO_OCCURRENCE;
            case REPEATED_OBSERVATION -> null;
            default -> null;
        };
    }

    RelationSubType dominant(RelationSubType left, RelationSubType right) {
        if (right == null) return left;
        if (left == null) return right;
        return precedence(right).compareTo(precedence(left)) > 0 ? right : left;
    }

    private SubtypePrecedence precedence(RelationSubType type) {
        return switch (type) {
            case TABLE_CO_OCCURRENCE -> SubtypePrecedence.TABLE_CO_OCCURRENCE;
            case COLUMN_CO_OCCURRENCE -> SubtypePrecedence.COLUMN_CO_OCCURRENCE;
            case NAMING_SUPPORTED_FK -> SubtypePrecedence.NAMING_SUPPORTED_FK;
            case SUBQUERY_INFERRED_FK -> SubtypePrecedence.SUBQUERY_INFERRED_FK;
            case INFERRED_JOIN_FK -> SubtypePrecedence.INFERRED_JOIN_FK;
            case PROFILE_SUPPORTED_FK -> SubtypePrecedence.PROFILE_SUPPORTED_FK;
            case DDL_DECLARED_FK -> SubtypePrecedence.DDL_DECLARED_FK;
            case DECLARED_FK -> SubtypePrecedence.DECLARED_FK;
        };
    }

    private enum SubtypePrecedence {
        TABLE_CO_OCCURRENCE,
        COLUMN_CO_OCCURRENCE,
        NAMING_SUPPORTED_FK,
        SUBQUERY_INFERRED_FK,
        INFERRED_JOIN_FK,
        PROFILE_SUPPORTED_FK,
        DDL_DECLARED_FK,
        DECLARED_FK
    }
}
