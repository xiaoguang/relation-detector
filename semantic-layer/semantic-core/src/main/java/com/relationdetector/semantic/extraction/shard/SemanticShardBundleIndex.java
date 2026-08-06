package com.relationdetector.semantic.extraction.shard;

import java.util.List;

/**
 * CN: 集中声明有界 semantic shard bundle reader 与 validator 共享的固定 section 名称；只提供不可变分类常量，不读取 bundle、建立索引或承担所有权规划。
 * EN: Declares the fixed section names shared by bounded semantic shard-bundle readers and validators. It exposes immutable classification constants only and does not read bundles, build indexes, or plan ownership.
 */
public final class SemanticShardBundleIndex {
    public static final List<String> FACT_SECTIONS = List.of(
            "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "derivedRelationships", "derivedLineage", "namingEvidence", "diagnostics");
    public static final List<String> CANDIDATE_SECTIONS = List.of(
            "eventCandidates", "reviewItemCandidates", "tripletCandidates");
    public static final List<String> ITEM_SECTIONS = List.of(
            "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "eventCandidates", "derivedRelationships", "derivedLineage",
            "namingEvidence", "reviewItemCandidates", "tripletCandidates", "diagnostics");

    private SemanticShardBundleIndex() {
    }
}
