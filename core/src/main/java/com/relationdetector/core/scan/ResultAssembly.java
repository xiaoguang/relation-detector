package com.relationdetector.core.scan;

import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.derived.DerivedPathInferenceService;
import com.relationdetector.core.relation.RelationshipMerger;

final class ResultAssembly {
    private final RelationshipMerger relationshipMerger = new RelationshipMerger();
    private final DataLineageMerger dataLineageMerger = new DataLineageMerger();
    private final DerivedPathInferenceService derivedPathInferenceService = new DerivedPathInferenceService();

    ScanResult assemble(ScanPipelineContext ctx) {
        ctx.result.relationships().addAll(relationshipMerger.merge(
                ctx.relationshipCandidates,
                ctx.config.minConfidence));
        ctx.result.dataLineages().addAll(dataLineageMerger.merge(ctx.lineageCandidates));
        ctx.result.namingEvidence().clear();
        ctx.result.namingEvidence().addAll(ctx.namingEvidencePool.merged());
        var derived = derivedPathInferenceService.infer(
                ctx.result.relationships(),
                ctx.result.dataLineages(),
                ctx.result.namingEvidence(),
                ctx.config);
        ctx.result.derivedRelationships().clear();
        ctx.result.derivedRelationships().addAll(derived.derivedRelationships());
        ctx.result.derivedDataLineages().clear();
        ctx.result.derivedDataLineages().addAll(derived.derivedDataLineages());
        return ctx.result;
    }
}
