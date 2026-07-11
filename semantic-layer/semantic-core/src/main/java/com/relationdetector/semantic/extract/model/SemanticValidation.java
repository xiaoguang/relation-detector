package com.relationdetector.semantic.extract.model;

import java.util.List;

public record SemanticValidation(
        List<SemanticIsolatedEntity> isolatedEntities,
        List<SemanticValidationIssue> unresolvedReferences,
        List<SemanticValidationIssue> missingEvidenceRefs,
        int generatedReviewItemCount,
        boolean isRefClosed
) {
}
