package com.relationdetector.core.relation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.model.Endpoint;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.core.scoring.ConfidenceCalculator;

/**
 * relationship 候选合并器。
 *
 * <p>CN: 合并 metadata、DDL、对象 SQL、日志、命名和数据画像产生的候选。列级关系保持精确；
 * 表级共现只在端点表一致时折叠进已有列级关系。这里还负责 repeated observation
 * evidence 汇总和最终 confidence 计算。
 *
 * <p>EN: Merges candidates from metadata, DDL, object SQL, logs, naming, and
 * profiling. Column-level relations stay precise; table-level co-occurrence is
 * folded into column relations only when table endpoints match. It also
 * summarizes repeated observations and calculates final confidence.
 */
public final class RelationshipMerger {
    private final ConfidenceCalculator calculator = new ConfidenceCalculator();

    /**
     * 合并候选并按 minConfidence 过滤输出。
     *
     * <p>EN: Merges candidates and filters final output by minConfidence.
     */
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
        foldColumnCoOccurrencesIntoDirectionalEvidence(merged);
        merged = normalizeDirectionFromEvidence(merged);
        merged.entrySet().removeIf(entry -> isNoOpColumnCoOccurrence(entry.getValue()));

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
     * Folds SQL-only column co-occurrence into an already directional FK-like
     * relationship when DDL/metadata/profile evidence has supplied direction.
     *
     * <p>CN: SQL 谓词本身只说明两列共现，不能靠列名判断 FK 方向；如果同端点已经有
     * DDL/metadata/profile 等方向性候选，则把 SQL 共现 evidence 合入该候选并移除弱关系。
     */
    private void foldColumnCoOccurrencesIntoDirectionalEvidence(Map<String, RelationshipCandidate> merged) {
        Map<String, RelationshipCandidate> directionalByEndpoints = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : merged.values()) {
            if (isDirectionalColumnRelation(candidate)) {
                directionalByEndpoints.put(unorderedColumnEndpointKey(candidate), candidate);
            }
        }
        if (directionalByEndpoints.isEmpty()) {
            return;
        }
        var iterator = merged.entrySet().iterator();
        while (iterator.hasNext()) {
            RelationshipCandidate candidate = iterator.next().getValue();
            if (!isColumnCoOccurrence(candidate)) {
                continue;
            }
            RelationshipCandidate directional = directionalByEndpoints.get(unorderedColumnEndpointKey(candidate));
            if (directional == null) {
                continue;
            }
            directional.evidence().addAll(candidate.evidence());
            directional.warnings().addAll(candidate.warnings());
            iterator.remove();
        }
    }

    private boolean isDirectionalColumnRelation(RelationshipCandidate candidate) {
        return candidate.relationType() == RelationType.FK_LIKE
                && candidate.source().isColumnLevel()
                && candidate.target().isColumnLevel();
    }

    private boolean isColumnCoOccurrence(RelationshipCandidate candidate) {
        return candidate.relationType() == RelationType.CO_OCCURRENCE
                && candidate.relationSubType() == RelationSubType.COLUMN_CO_OCCURRENCE
                && candidate.source().isColumnLevel()
                && candidate.target().isColumnLevel();
    }

    private boolean isNoOpColumnCoOccurrence(RelationshipCandidate candidate) {
        return isColumnCoOccurrence(candidate)
                && candidate.source().normalizedKey().equals(candidate.target().normalizedKey());
    }

    private Map<String, RelationshipCandidate> normalizeDirectionFromEvidence(Map<String, RelationshipCandidate> merged) {
        Map<String, RelationshipCandidate> normalized = new LinkedHashMap<>();
        for (RelationshipCandidate candidate : merged.values()) {
            RelationshipCandidate next = candidate;
            if (isColumnCoOccurrence(candidate)) {
                next = promoteColumnCoOccurrenceWhenDirectionIsKnown(candidate);
            } else if (candidate.relationType() == RelationType.FK_LIKE && !hasDirectionEvidence(candidate)) {
                next = copy(candidate, candidate.source(), candidate.target(),
                        RelationType.CO_OCCURRENCE, RelationSubType.COLUMN_CO_OCCURRENCE);
            }
            putOrMerge(normalized, next);
        }
        return normalized;
    }

    private RelationshipCandidate promoteColumnCoOccurrenceWhenDirectionIsKnown(RelationshipCandidate candidate) {
        if (!hasSqlPredicateEvidence(candidate)) {
            return candidate;
        }
        String uniqueEndpoint = singleUniqueEndpoint(candidate);
        if (uniqueEndpoint.isBlank()) {
            DirectionHint naming = singleNamingDirection(candidate);
            return naming == null
                    ? candidate
                    : copy(candidate, naming.source(), naming.target(),
                            RelationType.FK_LIKE, sqlPredicateSubtype(candidate));
        }
        String sourceKey = candidate.source().normalizedKey();
        String targetKey = candidate.target().normalizedKey();
        if (sourceKey.equals(targetKey)) {
            return candidate;
        }
        if (uniqueEndpoint.equals(sourceKey)) {
            return copy(candidate, candidate.target(), candidate.source(),
                    RelationType.FK_LIKE, sqlPredicateSubtype(candidate));
        }
        if (uniqueEndpoint.equals(targetKey)) {
            return copy(candidate, candidate.source(), candidate.target(),
                    RelationType.FK_LIKE, sqlPredicateSubtype(candidate));
        }
        return candidate;
    }

    private String singleUniqueEndpoint(RelationshipCandidate candidate) {
        String unique = "";
        for (Evidence evidence : candidate.evidence()) {
            if (evidence.type() != EvidenceType.TARGET_UNIQUE) {
                continue;
            }
            String endpoint = endpointFromUniqueEvidence(candidate, evidence);
            if (endpoint.isBlank()) {
                continue;
            }
            if (!unique.isBlank() && !unique.equals(endpoint)) {
                return "";
            }
            unique = endpoint;
        }
        return unique;
    }

    private String endpointFromUniqueEvidence(RelationshipCandidate candidate, Evidence evidence) {
        Object explicit = evidence.attributes().get("uniqueEndpoint");
        if (explicit != null) {
            return normalizeEndpoint(String.valueOf(explicit));
        }
        return candidate.target().normalizedKey();
    }

    private String normalizeEndpoint(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private RelationSubType sqlPredicateSubtype(RelationshipCandidate candidate) {
        return candidate.evidence().stream().anyMatch(e ->
                e.type() == EvidenceType.SQL_LOG_SUBQUERY_IN || e.type() == EvidenceType.SQL_LOG_EXISTS)
                ? RelationSubType.SUBQUERY_INFERRED_FK
                : RelationSubType.INFERRED_JOIN_FK;
    }

    private boolean hasDirectionEvidence(RelationshipCandidate candidate) {
        boolean hasSql = hasSqlPredicateEvidence(candidate);
        for (Evidence evidence : candidate.evidence()) {
            if (evidence.type() == EvidenceType.METADATA_FOREIGN_KEY
                    || evidence.type() == EvidenceType.DDL_FOREIGN_KEY
                    || evidence.type() == EvidenceType.VALUE_CONTAINMENT_HIGH
                    || evidence.type() == EvidenceType.VALUE_OVERLAP_HIGH) {
                return true;
            }
            if (evidence.type() == EvidenceType.TARGET_UNIQUE && hasSql) {
                String endpoint = endpointFromUniqueEvidence(candidate, evidence);
                if (endpoint.equals(candidate.target().normalizedKey())) {
                    return true;
                }
            }
            if (evidence.type() == EvidenceType.NAMING_MATCH && hasSql
                    && namingDirectionMatchesCurrentOrientation(candidate, evidence)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSqlPredicateEvidence(RelationshipCandidate candidate) {
        return candidate.evidence().stream().anyMatch(evidence -> switch (evidence.type()) {
            case SQL_LOG_JOIN, SQL_LOG_SUBQUERY_IN, SQL_LOG_EXISTS,
                    SQL_LOG_COLUMN_CO_OCCURRENCE, VIEW_JOIN, PROCEDURE_JOIN, TRIGGER_REFERENCE -> true;
            default -> false;
        });
    }

    private DirectionHint singleNamingDirection(RelationshipCandidate candidate) {
        DirectionHint direction = null;
        for (Evidence evidence : candidate.evidence()) {
            if (evidence.type() != EvidenceType.NAMING_MATCH) {
                continue;
            }
            DirectionHint next = namingDirection(candidate, evidence);
            if (next == null) {
                continue;
            }
            if (direction != null && (!direction.source().normalizedKey().equals(next.source().normalizedKey())
                    || !direction.target().normalizedKey().equals(next.target().normalizedKey()))) {
                return null;
            }
            direction = next;
        }
        return direction;
    }

    private DirectionHint namingDirection(RelationshipCandidate candidate, Evidence evidence) {
        Object source = evidence.attributes().get("suggestedSourceEndpoint");
        Object target = evidence.attributes().get("suggestedTargetEndpoint");
        if (source == null || target == null || !Boolean.TRUE.equals(evidence.attributes().get("directionHint"))) {
            return null;
        }
        String suggestedSource = normalizeEndpoint(String.valueOf(source));
        String suggestedTarget = normalizeEndpoint(String.valueOf(target));
        String currentSource = candidate.source().normalizedKey();
        String currentTarget = candidate.target().normalizedKey();
        if (suggestedSource.equals(currentSource) && suggestedTarget.equals(currentTarget)) {
            return new DirectionHint(candidate.source(), candidate.target());
        }
        if (suggestedSource.equals(currentTarget) && suggestedTarget.equals(currentSource)) {
            return new DirectionHint(candidate.target(), candidate.source());
        }
        return null;
    }

    private boolean namingDirectionMatchesCurrentOrientation(RelationshipCandidate candidate, Evidence evidence) {
        DirectionHint direction = namingDirection(candidate, evidence);
        return direction != null
                && direction.source().normalizedKey().equals(candidate.source().normalizedKey())
                && direction.target().normalizedKey().equals(candidate.target().normalizedKey());
    }

    private RelationshipCandidate copy(
            RelationshipCandidate candidate,
            com.relationdetector.contracts.model.Endpoint source,
            com.relationdetector.contracts.model.Endpoint target,
            RelationType type,
            RelationSubType subType
    ) {
        RelationshipCandidate copy = new RelationshipCandidate(source, target, type, subType);
        copy.evidence().addAll(candidate.evidence());
        copy.rawEvidence().addAll(candidate.rawEvidence());
        copy.warnings().addAll(candidate.warnings());
        copy.confidence(candidate.confidence());
        return copy;
    }

    private void putOrMerge(Map<String, RelationshipCandidate> merged, RelationshipCandidate candidate) {
        String key = key(candidate);
        RelationshipCandidate existing = merged.get(key);
        if (existing == null) {
            merged.put(key, candidate);
            return;
        }
        existing.evidence().addAll(candidate.evidence());
        existing.rawEvidence().addAll(candidate.rawEvidence());
        existing.warnings().addAll(candidate.warnings());
        existing.relationSubType(dominant(existing.relationSubType(), candidate.relationSubType()));
    }

    private String unorderedColumnEndpointKey(RelationshipCandidate candidate) {
        String left = candidate.source().normalizedKey();
        String right = candidate.target().normalizedKey();
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
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
        List<Evidence> observations = deduplicateNamingReferences(candidate.evidence());
        Map<String, EvidenceAccumulator> grouped = new LinkedHashMap<>();
        for (Evidence evidence : observations) {
            String key = evidenceKey(evidence);
            grouped.computeIfAbsent(key, ignored -> new EvidenceAccumulator(evidence)).add(evidence);
        }
        candidate.rawEvidence().clear();
        candidate.rawEvidence().addAll(observations);
        candidate.evidence().clear();
        for (EvidenceAccumulator accumulator : grouped.values()) {
            candidate.evidence().add(accumulator.toEvidence());
            if (accumulator.count() > 1 && repeatedObservationEligible(accumulator.first().type())) {
                candidate.evidence().add(accumulator.toRepeatedObservationEvidence());
            }
        }
    }

    private List<Evidence> deduplicateNamingReferences(List<Evidence> evidenceItems) {
        List<Evidence> deduplicated = new ArrayList<>();
        java.util.Set<String> namingReferences = new java.util.LinkedHashSet<>();
        for (Evidence evidence : evidenceItems) {
            if (evidence.type() != EvidenceType.NAMING_MATCH) {
                deduplicated.add(evidence);
                continue;
            }
            Object reference = evidence.attributes().get("evidenceRef");
            if (reference == null || String.valueOf(reference).isBlank()
                    || namingReferences.add(String.valueOf(reference))) {
                deduplicated.add(evidence);
            }
        }
        return deduplicated;
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
        if (candidate.relationType() == RelationType.CO_OCCURRENCE) {
            return candidate.source().isColumnLevel() && candidate.target().isColumnLevel()
                    ? RelationSubType.COLUMN_CO_OCCURRENCE
                    : RelationSubType.TABLE_CO_OCCURRENCE;
        }
        RelationSubType current = candidate.relationSubType();
        for (var evidence : candidate.evidence()) {
            current = dominant(current, subtypeFromEvidence(evidence.type()));
        }
        return current;
    }

    private RelationSubType subtypeFromEvidence(com.relationdetector.contracts.Enums.EvidenceType type) {
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

    private record DirectionHint(Endpoint source, Endpoint target) {
    }
}
