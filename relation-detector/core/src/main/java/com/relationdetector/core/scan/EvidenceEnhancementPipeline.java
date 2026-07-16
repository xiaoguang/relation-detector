package com.relationdetector.core.scan;

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
        weightAdjustmentService.adjust(
                ctx.relationshipCandidates,
                ctx.namingEvidencePool,
                ctx.adaptor.profiling().evidenceWeightAdjuster(),
                ctx.adaptorContext);
    }
}
