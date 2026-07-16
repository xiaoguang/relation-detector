package com.relationdetector.core.scan;

import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.derived.DerivedPathInferenceService;
import com.relationdetector.core.naming.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.relation.RelationshipMerger;

final class ResultAssembly {
    ScanResult assemble(ScanPipelineContext ctx) {
        RelationshipMerger relationshipMerger = new RelationshipMerger(ctx.endpointKeys);
        DataLineageMerger dataLineageMerger = new DataLineageMerger(ctx.endpointKeys);
        ctx.result.relationships().addAll(relationshipMerger.merge(
                ctx.relationshipCandidates,
                ctx.config.output().minConfidence()));
        ctx.result.dataLineages().addAll(dataLineageMerger.merge(ctx.lineageCandidates));
        ctx.result.namingEvidence().clear();
        ctx.result.namingEvidence().addAll(ctx.namingEvidencePool.merged());
        var derived = new DerivedPathInferenceService(ctx.endpointKeys).infer(
                ctx.result.relationships(),
                ctx.result.dataLineages(),
                ctx.result.namingEvidence(),
                ctx.parserConfig);
        ctx.namingEvidencePool.addAll(derived.derivedNamingEvidence());
        ctx.result.namingEvidence().clear();
        ctx.result.namingEvidence().addAll(ctx.namingEvidencePool.merged());
        ctx.result.derivedRelationships().clear();
        ctx.result.derivedRelationships().addAll(derived.derivedRelationships());
        new NamingMatchEvidenceEnhancer().normalizeReferences(
                ctx.result.relationships(), ctx.result.derivedRelationships(), ctx.namingEvidencePool);
        ctx.result.derivedDataLineages().clear();
        ctx.result.derivedDataLineages().addAll(derived.derivedDataLineages());
        return ctx.result;
    }
}
