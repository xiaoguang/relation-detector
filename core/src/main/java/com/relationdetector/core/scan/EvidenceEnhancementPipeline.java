package com.relationdetector.core.scan;

import com.relationdetector.core.metadata.MetadataEvidenceEnhancer;
import com.relationdetector.core.relation.NamingMatchEvidenceEnhancer;

final class EvidenceEnhancementPipeline {
    private final MetadataEvidenceEnhancer metadataEvidenceEnhancer = new MetadataEvidenceEnhancer();
    private final NamingMatchEvidenceEnhancer namingMatchEvidenceEnhancer = new NamingMatchEvidenceEnhancer();

    void enhance(ScanPipelineContext ctx) {
        if (ctx.metadataSnapshot != null) {
            metadataEvidenceEnhancer.enhance(ctx.relationshipCandidates, ctx.metadataSnapshot);
        }
        namingMatchEvidenceEnhancer.enhance(ctx.relationshipCandidates);
    }
}
