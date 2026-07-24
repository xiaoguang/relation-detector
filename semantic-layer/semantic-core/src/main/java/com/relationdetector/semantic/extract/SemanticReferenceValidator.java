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

/**
 * CN: 为每次 normalization 创建独立 reference-validation session，检查 owner ids、physical tables/columns、evidence refs 和 semantic refs；factory 无共享状态。
 * EN: Creates an isolated validation session per normalization for owner ids, physical tables and columns, evidence references, and semantic references. The factory has no shared state.
 */
final class SemanticReferenceValidator {
    Session newSession(SemanticReferenceIndex referenceIndex, SemanticPhysicalReferenceIndex physicalIndex) {
        return new Session(referenceIndex, physicalIndex);
    }

    static final class Session {
        private final Map<String, SemanticValidationIssue> unresolvedReferences = new LinkedHashMap<>();
        private final Map<String, SemanticValidationIssue> missingEvidenceRefs = new LinkedHashMap<>();
        private final SemanticReferenceIndex referenceIndex;
        private final SemanticPhysicalReferenceIndex physicalIndex;
        private final SemanticOwnerIdRegistry ownerIds = new SemanticOwnerIdRegistry();
        private int generatedReviewItemCount;

        private Session(SemanticReferenceIndex referenceIndex, SemanticPhysicalReferenceIndex physicalIndex) {
            this.referenceIndex = java.util.Objects.requireNonNull(referenceIndex, "referenceIndex");
            this.physicalIndex = java.util.Objects.requireNonNull(physicalIndex, "physicalIndex");
        }

        void registerOwner(String section, String id) {
            ownerIds.register(section, id);
        }

        void requirePhysicalTable(String section, String ownerId, String field, String table) {
            if (!blank(table) && !physicalIndex.containsTable(table)) {
                throw new SemanticExtractionValidationException(
                        section + " " + ownerId + " references unknown physical table in " + field + ": " + table);
            }
        }

        void requirePhysicalColumn(String section, String ownerId, String field, String column) {
            if (!blank(column) && !physicalIndex.containsColumn(column)) {
                throw new SemanticExtractionValidationException(
                        section + " " + ownerId + " references unknown physical column in " + field + ": " + column);
            }
        }

        void requireEvidence(String section, String id, SemanticItem item) {
            if ("BUSINESS_APPROVED".equalsIgnoreCase(item.reviewStatus())) {
                throw new SemanticExtractionValidationException(
                        "BUSINESS_APPROVED is reserved for the governance workflow: " + id);
            }
            if (item.evidenceRefs().isEmpty()) {
                String key = section + ":" + id;
                missingEvidenceRefs.putIfAbsent(key, new SemanticValidationIssue(
                        section, id, null, null, null, "Semantic item has no evidenceRefs."));
                return;
            }
            for (String reference : item.evidenceRefs()) {
                if (!referenceIndex.contains(reference)) {
                    String key = section + ":" + id + ":evidenceRefs:" + reference;
                    unresolvedReferences.putIfAbsent(key, new SemanticValidationIssue(
                            section, id, "evidenceRefs", reference, "evidence",
                            "Evidence reference does not exist in the supplied bundle."));
                }
            }
            for (String reference : item.ownedGroundingRefs()) {
                if (!referenceIndex.contains(reference)) {
                    String key = section + ":" + id + ":ownedGroundingRefs:" + reference;
                    unresolvedReferences.putIfAbsent(key, new SemanticValidationIssue(
                            section, id, "ownedGroundingRefs", reference, "factOrCandidate",
                            "Owned grounding reference does not exist in the supplied bundle."));
                }
            }
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
            if (!blank(value) && referenceIndex.isCandidate(value, kind)) {
                return;
            }
            String key = field + ":" + id;
            unresolvedReferences.putIfAbsent(key, new SemanticValidationIssue(
                    null, id, field, text(value), kind, reason));
        }

        private String text(String value) {
            return value == null ? "" : value;
        }

        private boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
