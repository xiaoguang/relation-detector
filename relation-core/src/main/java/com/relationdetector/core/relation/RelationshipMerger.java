package com.relationdetector.core.relation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.api.DefaultEvidenceScores;
import com.relationdetector.api.Evidence;
import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.RelationSubType;
import com.relationdetector.api.Enums.RelationType;
import com.relationdetector.core.ConfidenceCalculator;

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
            summarizeRepeatedEvidence(candidate);
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

    /**
     * Summarizes repeated observations before confidence calculation.
     *
     * <p>RelationshipMerger receives one RelationshipCandidate per source hit.
     * SQL logs can contain the same join hundreds of times. Keeping every hit as
     * an independent evidence item would make the formula repeatedly apply the
     * same score:
     *
     * <pre>{@code
     * 1 - (1 - 0.55)^100
     * }</pre>
     *
     * That would make frequency alone look like a declared FK. Instead, this
     * method first preserves every original evidence item in rawEvidence, then
     * groups equivalent evidence by type, source category, concrete source, and
     * score. The grouped evidence keeps the original score once and stores
     * count/sample details in attributes for explanation.
     *
     * <p>When a group has repeated observations, the summary also gets one
     * REPEATED_OBSERVATION evidence item. Its score follows a capped diminishing
     * formula: cap * (1 - 1 / count). More occurrences help a little, but the
     * bonus approaches the cap and can never exceed it.
     */
    private void summarizeRepeatedEvidence(RelationshipCandidate candidate) {
        Map<String, EvidenceAccumulator> grouped = new LinkedHashMap<>();
        for (Evidence evidence : candidate.evidence()) {
            String key = evidenceKey(evidence);
            grouped.computeIfAbsent(key, ignored -> new EvidenceAccumulator(evidence)).add(evidence);
        }
        candidate.rawEvidence().clear();
        candidate.rawEvidence().addAll(candidate.evidence());
        candidate.evidence().clear();
        for (EvidenceAccumulator accumulator : grouped.values()) {
            candidate.evidence().add(accumulator.toEvidence());
            if (accumulator.count() > 1 && repeatedObservationEligible(accumulator.first().type())) {
                candidate.evidence().add(accumulator.toRepeatedObservationEvidence());
            }
        }
    }

    private String evidenceKey(Evidence evidence) {
        return evidence.type() + "|"
                + evidence.sourceType() + "|"
                + evidence.source() + "|"
                + evidence.score();
    }

    private boolean repeatedObservationEligible(EvidenceType type) {
        return switch (type) {
            case VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE,
                    SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS,
                    SQL_LOG_COLUMN_CO_OCCURRENCE, SQL_LOG_TABLE_CO_OCCURRENCE -> true;
            default -> false;
        };
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
            case SQL_LOG_COLUMN_CO_OCCURRENCE -> RelationSubType.COLUMN_CO_OCCURRENCE;
            case SQL_LOG_TABLE_CO_OCCURRENCE -> RelationSubType.TABLE_CO_OCCURRENCE;
            case REPEATED_OBSERVATION -> null;
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
            case COLUMN_CO_OCCURRENCE -> 7;
            case TABLE_CO_OCCURRENCE -> 8;
        };
    }

    private static final class EvidenceAccumulator {
        private static final int MAX_SAMPLE_DETAILS = 5;
        private final Evidence first;
        private int count;
        private String lastDetail;
        private final List<String> sampleDetails = new ArrayList<>();

        EvidenceAccumulator(Evidence first) {
            this.first = first;
        }

        void add(Evidence evidence) {
            count++;
            lastDetail = evidence.detail();
            if (sampleDetails.size() < MAX_SAMPLE_DETAILS) {
                sampleDetails.add(evidence.detail());
            }
        }

        Evidence toEvidence() {
            Map<String, Object> attributes = new LinkedHashMap<>(first.attributes());
            attributes.put("count", count);
            if (count > 1) {
                attributes.put("firstDetail", first.detail());
                attributes.put("lastDetail", lastDetail);
                attributes.put("sampleDetails", List.copyOf(sampleDetails));
                attributes.put("sampleTruncated", count > sampleDetails.size());
            }
            return new Evidence(first.type(), first.score(), first.sourceType(), first.source(),
                    first.detail(), attributes);
        }

        Evidence toRepeatedObservationEvidence() {
            double cap = DefaultEvidenceScores.REPEATED_OBSERVATION_MAX;
            double score = cap * (1.0d - (1.0d / count));
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("count", count);
            attributes.put("maxScore", String.format(java.util.Locale.ROOT, "%.2f", cap));
            attributes.put("formula", "maxScore * (1 - 1 / count)");
            attributes.put("baseEvidenceType", first.type().name());
            return new Evidence(EvidenceType.REPEATED_OBSERVATION, BigDecimal.valueOf(score),
                    first.sourceType(), first.source(),
                    "Repeated " + first.type() + " observed " + count + " times",
                    attributes);
        }

        Evidence first() {
            return first;
        }

        int count() {
            return count;
        }
    }
}
