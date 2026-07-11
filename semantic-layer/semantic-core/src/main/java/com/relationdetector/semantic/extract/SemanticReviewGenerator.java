package com.relationdetector.semantic.extract;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.relationdetector.semantic.extract.model.SemanticExtractionDocument;
import com.relationdetector.semantic.extract.model.SemanticItem;
import com.relationdetector.semantic.extract.model.SemanticReviewItem;

/** Generates deterministic review records for semantic items marked REVIEW_NEEDED. */
final class SemanticReviewGenerator {
    int generate(SemanticExtractionDocument document) {
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
            review.id = "review:auto:" + SemanticNormalizationSupport.slug(item.id());
            review.targetRef = item.id();
            review.targetSection = targetSection;
            review.type = "REVIEW_NEEDED";
            review.severity = SemanticNormalizationSupport.nonBlank(item.severity, "MEDIUM");
            review.reason = "Semantic item is marked REVIEW_NEEDED and requires business or data owner review.";
            review.evidenceRefs = item.evidenceRefs().isEmpty() ? List.of(item.id()) : List.copyOf(item.evidenceRefs());
            reviewItems.add(review);
            generated++;
        }
        return generated;
    }
}
