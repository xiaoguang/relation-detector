package com.relationdetector.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;

/**
 * Merges candidates from metadata, DDL, objects, logs, naming, and profiling.
 *
 * <p>Design mapping: Phase 2 "归并规则". Column-level relations are kept
 * precise; table-level co-occurrence is only folded into an existing column
 * relation when both table endpoints match.
 */
public final class RelationshipMerger {
    private final ConfidenceCalculator calculator = new ConfidenceCalculator();

    public List<RelationshipCandidate> merge(List<RelationshipCandidate> candidates, double minConfidence) {
        Map<String, RelationshipCandidate> merged = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : candidates) {
            String key = key(candidate);
            RelationshipCandidate existing = merged.get(key);
            if (existing == null) {
                merged.put(key, candidate);
            } else {
                existing.evidence().addAll(candidate.evidence());
                existing.warnings().addAll(candidate.warnings());
                existing.relationSubType(dominant(existing.relationSubType(), candidate.relationSubType()));
            }
        }

        for (RelationshipCandidate candidate : merged.values()) {
            candidate.confidence(calculator.calculate(candidate.evidence()));
            candidate.relationSubType(resolveSubtype(candidate));
        }

        List<RelationshipCandidate> output = new ArrayList<>();
        for (RelationshipCandidate candidate : merged.values()) {
            if (candidate.confidence().doubleValue() >= minConfidence) {
                output.add(candidate);
            }
        }
        output.sort(Comparator
                .comparing((RelationshipCandidate candidate) -> candidate.confidence()).reversed()
                .thenComparing(c -> c.source().displayName())
                .thenComparing(c -> c.target().displayName()));
        return output;
    }

    private String key(RelationshipCandidate candidate) {
        if (candidate.relationType() == RelationType.CO_OCCURRENCE
                && !candidate.source().isColumnLevel()
                && !candidate.target().isColumnLevel()) {
            String a = candidate.source().table().normalizedName();
            String b = candidate.target().table().normalizedName();
            return a.compareTo(b) <= 0
                    ? "CO:" + a + ":" + b
                    : "CO:" + b + ":" + a;
        }
        return candidate.relationType() + ":"
                + candidate.source().normalizedKey() + "->"
                + candidate.target().normalizedKey();
    }

    private RelationSubType resolveSubtype(RelationshipCandidate candidate) {
        RelationSubType current = candidate.relationSubType();
        for (var evidence : candidate.evidence()) {
            current = dominant(current, subtypeFromEvidence(evidence.type()));
        }
        return current;
    }

    private RelationSubType subtypeFromEvidence(com.relationdetector.api.Enums.EvidenceType type) {
        return switch (type) {
            case METADATA_FOREIGN_KEY -> RelationSubType.DECLARED_FK;
            case DDL_FOREIGN_KEY -> RelationSubType.DDL_DECLARED_FK;
            case VALUE_CONTAINMENT_HIGH, VALUE_OVERLAP_HIGH -> RelationSubType.PROFILE_SUPPORTED_FK;
            case VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE, SQL_LOG_JOIN -> RelationSubType.INFERRED_JOIN_FK;
            case SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS -> RelationSubType.SUBQUERY_INFERRED_FK;
            case NAMING_MATCH -> RelationSubType.NAMING_SUPPORTED_FK;
            case SQL_LOG_TABLE_CO_OCCURRENCE -> RelationSubType.TABLE_CO_OCCURRENCE;
            default -> null;
        };
    }

    private RelationSubType dominant(RelationSubType left, RelationSubType right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }
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
            case TABLE_CO_OCCURRENCE -> 7;
        };
    }
}
