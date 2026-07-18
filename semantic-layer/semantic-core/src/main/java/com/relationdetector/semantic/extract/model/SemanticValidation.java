package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 汇总 semantic normalization 的孤立实体、未解析引用、缺失 evidence 和 reference-closure 状态；只报告验证结果，不修补模型输出。
 * EN: Summarizes isolated entities, unresolved references, missing evidence, and reference-closure status from semantic normalization. It reports validation without repairing model output.
 */
public record SemanticValidation(
        List<SemanticIsolatedEntity> isolatedEntities,
        List<SemanticValidationIssue> unresolvedReferences,
        List<SemanticValidationIssue> missingEvidenceRefs,
        int generatedReviewItemCount,
        boolean isRefClosed
) {
}
