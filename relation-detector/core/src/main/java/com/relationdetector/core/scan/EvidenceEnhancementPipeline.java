package com.relationdetector.core.scan;

final class EvidenceEnhancementPipeline {
    private final EvidenceEnhancementService enhancementService = new EvidenceEnhancementService();

    void enhance(ScanPipelineContext ctx) {
        enhancementService.enhance(ctx.relationshipCandidates, ctx.namingEvidencePool, ctx.metadataSnapshot,
                ctx.parserConfig);
    }

    void enhanceProfiledCandidates(
            ScanPipelineContext ctx,
            java.util.List<com.relationdetector.contracts.model.RelationshipCandidate> candidates
    ) {
        enhancementService.enhanceProfiledCandidates(candidates, ctx.namingEvidencePool, ctx.parserConfig);
    }
}
