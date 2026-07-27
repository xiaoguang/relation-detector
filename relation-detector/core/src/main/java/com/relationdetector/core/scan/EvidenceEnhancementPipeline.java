package com.relationdetector.core.scan;

/**
 * CN: 编排 DDL inventory、scan-level enhancement 与 adaptor weight adjustment；只有 adaptor 明确声明
 * 权重能力时才调用其 hook，identity hook 本身不构成 capability。
 * EN: Orchestrates DDL inventory, scan-level enhancement, and adaptor weight adjustment. It invokes the weight hook
 * only when the adaptor explicitly declares that capability; an identity hook alone does not constitute support.
 */
final class EvidenceEnhancementPipeline {
    private final EvidenceEnhancementService enhancementService = new EvidenceEnhancementService();
    private final com.relationdetector.core.evidence.EvidenceWeightAdjustmentService weightAdjustmentService =
            new com.relationdetector.core.evidence.EvidenceWeightAdjustmentService();

    void enhance(ScanPipelineContext ctx) {
        ctx.ddlEvidenceInventory.enhance(ctx.relationshipCandidates);
        enhancementService.enhance(ctx.relationshipCandidates, ctx.namingEvidencePool, ctx.metadataSnapshot,
                ctx.parserConfig, ctx.adaptor.identifierRules(),
                new com.relationdetector.core.identity.NamespaceContext(
                        ctx.scope.catalog(), ctx.scope.schema(), java.util.List.of()));
    }

    void enhanceProfiledCandidates(
            ScanPipelineContext ctx,
            java.util.List<com.relationdetector.contracts.model.RelationshipCandidate> candidates
    ) {
        enhancementService.enhanceProfiledCandidates(
                candidates,
                ctx.namingEvidencePool,
                ctx.parserConfig,
                ctx.adaptor.identifierRules(),
                new com.relationdetector.core.identity.NamespaceContext(
                        ctx.scope.catalog(), ctx.scope.schema(), java.util.List.of()));
    }

    void adjustWeights(ScanPipelineContext ctx) {
        if (!ctx.adaptor.capabilities().contains(
                com.relationdetector.contracts.Enums.AdaptorCapability.EVIDENCE_WEIGHT_ADJUSTMENT)) {
            return;
        }
        weightAdjustmentService.adjust(
                ctx.relationshipCandidates,
                ctx.namingEvidencePool,
                ctx.adaptor.profiling().evidenceWeightAdjuster(),
                ctx.adaptorContext);
    }
}
