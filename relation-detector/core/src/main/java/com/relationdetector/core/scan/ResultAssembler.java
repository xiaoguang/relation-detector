package com.relationdetector.core.scan;

import com.relationdetector.core.lineage.DataLineageMerger;
import com.relationdetector.core.derived.DerivedPathInferenceService;
import com.relationdetector.core.naming.NamingMatchEvidenceEnhancer;
import com.relationdetector.core.relation.RelationshipMerger;

/**
 * CN: 将一次 scan context 中已提取的 relationship、lineage、naming pool 与 derived inference 按固定顺序装配为
 * 最终 ScanResult；上游是 scan pipeline，下游是 result writer，本类不解析 SQL、不创建直接 evidence，也不修改配置。
 * EN: Assembles extracted relationships, lineage, the naming pool, and derived inference from one scan context into
 * the final ScanResult in a fixed order. It sits between the scan pipeline and result writers, and never parses SQL,
 * creates direct evidence, or mutates configuration.
 */
final class ResultAssembler {
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
