package com.relationdetector.core.relation;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.model.RelationshipCandidate;

/** Resolves the strongest relationship subtype from merged evidence. */
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
        return priority(right) < priority(left) ? right : left;
    }

    private int priority(RelationSubType type) {
        return switch (type) {
            case DECLARED_FK -> 1;
            case DDL_DECLARED_FK -> 2;
            case PROFILE_SUPPORTED_FK -> 3;
            case INFERRED_JOIN_FK -> 4;
            case SUBQUERY_INFERRED_FK -> 5;
            case NAMING_SUPPORTED_FK -> 6;
            case COLUMN_CO_OCCURRENCE -> 7;
            case TABLE_CO_OCCURRENCE -> 8;
        };
    }
}
