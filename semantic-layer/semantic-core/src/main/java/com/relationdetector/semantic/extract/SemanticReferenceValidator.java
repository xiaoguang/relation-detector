package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.semantic.extract.model.SemanticEntity;
import com.relationdetector.semantic.extract.model.SemanticIsolatedEntity;
import com.relationdetector.semantic.extract.model.SemanticItem;
import com.relationdetector.semantic.extract.model.SemanticValidation;
import com.relationdetector.semantic.extract.model.SemanticValidationIssue;

/** Stateless factory for per-normalization ref-closure validation sessions. */
final class SemanticReferenceValidator {
    Session newSession() {
        return new Session();
    }

    static final class Session {
        private final Map<String, SemanticValidationIssue> unresolvedReferences = new LinkedHashMap<>();
        private final Map<String, SemanticValidationIssue> missingEvidenceRefs = new LinkedHashMap<>();
        private int generatedReviewItemCount;

        void requireEvidence(String section, String id, SemanticItem item) {
            if (!item.evidenceRefs().isEmpty()) {
                return;
            }
            String key = section + ":" + id;
            missingEvidenceRefs.putIfAbsent(key, new SemanticValidationIssue(
                    section, id, null, null, null, "Semantic item has no evidenceRefs."));
        }

        void requireEventCandidateRef(String id, String candidateRef) {
            requireCandidateRef(id, "eventCandidateRef", candidateRef, "eventCandidate",
                    "Event must reference a deterministic eventCandidate.");
        }

        void requireTripletCandidateRef(String id, String candidateRef) {
            requireCandidateRef(id, "candidateRef", candidateRef, "tripletCandidate",
                    "Triplet must reference a deterministic tripletCandidate.");
        }

        void requireResolved(String ownerId, String field, String value, String resolvedRef, String expectedRefKind) {
            if (blank(value) || !blank(resolvedRef)) {
                return;
            }
            String key = ownerId + ":" + field + ":" + value;
            unresolvedReferences.putIfAbsent(key, new SemanticValidationIssue(
                    null,
                    ownerId,
                    field,
                    value,
                    expectedRefKind,
                    "Referenced semantic item could not be resolved to a stable id."));
        }

        void addGeneratedReviewItems(int count) {
            generatedReviewItemCount += count;
        }

        SemanticValidation build(List<SemanticEntity> entities, Set<String> linkedEntities) {
            List<SemanticIsolatedEntity> isolated = new ArrayList<>();
            for (SemanticEntity entity : entities) {
                if (!blank(entity.id) && !linkedEntities.contains(entity.id)) {
                    isolated.add(new SemanticIsolatedEntity(
                            entity.id,
                            text(entity.name),
                            text(entity.physicalName),
                            "Entity has evidence but is not referenced by event, relation, lineage, metric, dimension, or triplet sections."));
                }
            }
            boolean closed = isolated.isEmpty() && unresolvedReferences.isEmpty() && missingEvidenceRefs.isEmpty();
            return new SemanticValidation(
                    List.copyOf(isolated),
                    List.copyOf(unresolvedReferences.values()),
                    List.copyOf(missingEvidenceRefs.values()),
                    generatedReviewItemCount,
                    closed);
        }

        private void requireCandidateRef(String id, String field, String value, String kind, String reason) {
            if (!blank(value)) {
                return;
            }
            String key = field + ":" + id;
            unresolvedReferences.putIfAbsent(key, new SemanticValidationIssue(
                    null, id, field, "", kind, reason));
        }

        private String text(String value) {
            return value == null ? "" : value;
        }

        private boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
