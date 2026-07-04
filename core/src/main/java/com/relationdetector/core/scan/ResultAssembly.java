package com.relationdetector.core.scan;

import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.relation.RelationshipMerger;

final class ResultAssembly {
    private final RelationshipMerger relationshipMerger = new RelationshipMerger();
    private final DataLineageMerger dataLineageMerger = new DataLineageMerger();

    ScanResult assemble(ScanPipelineContext ctx) {
        ctx.result.relationships().addAll(relationshipMerger.merge(
                ctx.relationshipCandidates,
                ctx.config.minConfidence));
        ctx.result.dataLineages().addAll(dataLineageMerger.merge(ctx.lineageCandidates));
        ctx.result.namingEvidence().clear();
        ctx.result.namingEvidence().addAll(ctx.namingEvidencePool.merged());
        return ctx.result;
    }
}
