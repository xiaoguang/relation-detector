package com.relationdetector.core.scan;

import com.relationdetector.core.metadata.MetadataEvidenceEnhancer;
import com.relationdetector.core.relation.NamingEvidenceExtractor;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;

final class EvidenceEnhancementPipeline {
    private final MetadataEvidenceEnhancer metadataEvidenceEnhancer = new MetadataEvidenceEnhancer();
    private final NamingEvidenceExtractor namingEvidenceExtractor = new NamingEvidenceExtractor();
    private final NamingMatchEvidenceEnhancer namingMatchEvidenceEnhancer = new NamingMatchEvidenceEnhancer();

    void enhance(ScanPipelineContext ctx) {
        if (ctx.metadataSnapshot != null) {
            ctx.namingEvidencePool.addAll(namingEvidenceExtractor.extractFromMetadata(ctx.metadataSnapshot));
            metadataEvidenceEnhancer.enhance(ctx.relationshipCandidates, ctx.metadataSnapshot);
        }
        ctx.namingEvidencePool.addAll(namingEvidenceExtractor.extractFromRelationshipCandidates(ctx.relationshipCandidates));
        namingMatchEvidenceEnhancer.enhance(ctx.relationshipCandidates, ctx.namingEvidencePool);
    }
}
