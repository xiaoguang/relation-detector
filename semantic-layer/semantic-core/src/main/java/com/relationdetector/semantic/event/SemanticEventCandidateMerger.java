package com.relationdetector.semantic.event;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CN: 合并由同一完整 typed event identity 产生的磁盘分批贡献，并在全局关联完成后重建 event candidate；
 * 输入是已验证候选及关联引用，输出稳定、去重的候选，不推断 SQL 结构或改变 event identity。
 * EN: Merges disk-batched contributions that share one complete typed event identity and rebuilds the event candidate
 * after global associations are known. It returns a stable deduplicated candidate without inferring SQL structure or
 * changing event identity.
 */
public final class SemanticEventCandidateMerger {
    private final EventReadableNameSuggester nameSuggester = new EventReadableNameSuggester();

    public SemanticEventCandidate merge(
            SemanticEventCandidate left,
            SemanticEventCandidate right
    ) {
        requireSame(left.id(), right.id(), "id");
        requireSame(left.eventKind(), right.eventKind(), "event kind");
        requireSame(left.sourceType(), right.sourceType(), "source type");
        requireSame(left.sourceObject(), right.sourceObject(), "source object");
        requireSame(left.sourceObjectType(), right.sourceObjectType(), "source object type");
        requireSame(left.sourceObjectName(), right.sourceObjectName(), "source object name");
        requireSame(left.sourceFile(), right.sourceFile(), "source file");
        requireSame(left.sourceStatementId(), right.sourceStatementId(), "source statement");
        int leftCount = count(left);
        int rightCount = count(right);
        BigDecimal confidence = left.confidence().multiply(BigDecimal.valueOf(leftCount))
                .add(right.confidence().multiply(BigDecimal.valueOf(rightCount)))
                .divide(BigDecimal.valueOf(leftCount + rightCount), 4, RoundingMode.HALF_UP);
        return rebuild(
                left,
                union(left.operationKinds(), right.operationKinds()),
                union(left.inputEndpoints(), right.inputEndpoints()),
                union(left.outputEndpoints(), right.outputEndpoints()),
                union(left.lineageRefs(), right.lineageRefs()),
                List.of(),
                List.of(),
                confidence,
                leftCount + rightCount);
    }

    public SemanticEventCandidate normalize(SemanticEventCandidate candidate) {
        return rebuild(
                candidate,
                sorted(candidate.operationKinds()),
                sorted(candidate.inputEndpoints()),
                sorted(candidate.outputEndpoints()),
                sorted(candidate.lineageRefs()),
                List.of(),
                List.of(),
                candidate.confidence(),
                count(candidate));
    }

    public SemanticEventCandidate associate(
            SemanticEventCandidate candidate,
            List<String> relationshipRefs,
            List<String> supportingDerivedLineageRefs
    ) {
        List<String> relationships = sorted(relationshipRefs);
        List<String> derived = sorted(supportingDerivedLineageRefs);
        return rebuild(
                candidate,
                sorted(candidate.operationKinds()),
                sorted(candidate.inputEndpoints()),
                sorted(candidate.outputEndpoints()),
                sorted(candidate.lineageRefs()),
                derived,
                relationships,
                candidate.confidence(),
                count(candidate));
    }

    private SemanticEventCandidate rebuild(
            SemanticEventCandidate source,
            List<String> operationKinds,
            List<String> inputEndpoints,
            List<String> outputEndpoints,
            List<String> lineageRefs,
            List<String> supportingDerivedLineageRefs,
            List<String> relationshipRefs,
            BigDecimal confidence,
            int directLineageCount
    ) {
        Set<String> evidenceRefs = new LinkedHashSet<>(lineageRefs);
        evidenceRefs.addAll(supportingDerivedLineageRefs);
        evidenceRefs.addAll(relationshipRefs);
        EventReadableNameSuggester.EventNameSuggestion suggestion = nameSuggester.suggest(
                source.eventKind(),
                source.sourceObject(),
                new LinkedHashSet<>(inputEndpoints),
                new LinkedHashSet<>(outputEndpoints));
        return new SemanticEventCandidate(
                source.id(),
                source.eventKind(),
                source.sourceType(),
                source.sourceObject(),
                source.sourceObjectType(),
                source.sourceObjectName(),
                source.sourceFile(),
                source.sourceStatementId(),
                suggestion.readableNameHint(),
                suggestion.businessActionHint(),
                suggestion.eventNameBasis(),
                operationKinds,
                inputEndpoints,
                outputEndpoints,
                lineageRefs,
                supportingDerivedLineageRefs,
                relationshipRefs,
                List.copyOf(evidenceRefs),
                confidence,
                Map.of(
                        "directLineageCount", directLineageCount,
                        "supportingDerivedLineageCount", supportingDerivedLineageRefs.size()));
    }

    private int count(SemanticEventCandidate candidate) {
        Object value = candidate.attributes().get("directLineageCount");
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : 1;
    }

    private List<String> union(List<String> left, List<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result.stream().sorted().toList();
    }

    private List<String> sorted(List<String> values) {
        return values == null ? List.of() : values.stream().distinct().sorted().toList();
    }

    private void requireSame(String left, String right, String field) {
        if (!java.util.Objects.equals(left, right)) {
            throw new IllegalArgumentException("event contributions disagree on " + field);
        }
    }
}
