package com.relationdetector.semantic.extraction.normalization;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.relationdetector.semantic.extraction.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extraction.model.SemanticItem;
import com.relationdetector.semantic.extraction.model.SemanticReviewItem;

/**
 * CN: 为已规范化且标记REVIEW_NEEDED的semantic owner生成确定性review记录；输入是typed document，输出追加到
 * review section并返回数量。上游是section normalization，下游是owner/reference validation；本类不批准对象、
 * 不改变业务内容，也不为无grounding对象生成正式身份。
 *
 * <p>EN: Generates deterministic review records for normalized semantic owners marked REVIEW_NEEDED. It appends to
 * the review section and returns the generated count. Section normalization is upstream and owner/reference validation
 * is downstream; it never approves objects, changes business content, or identities ungrounded objects.
 */
public final class SemanticReviewGenerator {
    public int generate(SemanticExtractionDocument document) {
        Set<String> existingTargets = new LinkedHashSet<>();
        for (SemanticReviewItem item : document.reviewItems) {
            String targetRef = SemanticNormalizationSupport.nonBlank(item.targetRef, item.target);
            if (!targetRef.isBlank()) {
                existingTargets.add(targetRef);
            }
        }
        int generated = 0;
        generated += generate(document.reviewItems, existingTargets, document.entities, "entities");
        generated += generate(document.reviewItems, existingTargets, document.events, "events");
        generated += generate(document.reviewItems, existingTargets, document.relations, "relations");
        generated += generate(document.reviewItems, existingTargets, document.lineage, "lineage");
        generated += generate(document.reviewItems, existingTargets, document.metrics, "metrics");
        generated += generate(document.reviewItems, existingTargets, document.dimensions, "dimensions");
        generated += generate(document.reviewItems, existingTargets, document.triplets, "triplets");
        return generated;
    }

    private int generate(
            List<SemanticReviewItem> reviewItems,
            Set<String> existingTargets,
            List<? extends SemanticItem> sectionItems,
            String targetSection
    ) {
        int generated = 0;
        for (SemanticItem item : sectionItems) {
            if (!"REVIEW_NEEDED".equalsIgnoreCase(item.reviewStatus())
                    || item.id() == null
                    || item.id().isBlank()
                    || !existingTargets.add(item.id())) {
                continue;
            }
            SemanticReviewItem review = new SemanticReviewItem();
            review.id = SemanticCanonicalIdentity.review(item.id(), targetSection, "REVIEW_NEEDED");
            review.targetRef = item.id();
            review.targetSection = targetSection;
            review.type = "REVIEW_NEEDED";
            review.reviewStatus = "REVIEW_NEEDED";
            review.severity = SemanticNormalizationSupport.nonBlank(item.severity, "MEDIUM");
            review.reason = "Semantic item is marked REVIEW_NEEDED and requires business or data owner review.";
            review.evidenceRefs = item.evidenceRefs().isEmpty() ? List.of(item.id()) : List.copyOf(item.evidenceRefs());
            reviewItems.add(review);
            generated++;
        }
        return generated;
    }
}
