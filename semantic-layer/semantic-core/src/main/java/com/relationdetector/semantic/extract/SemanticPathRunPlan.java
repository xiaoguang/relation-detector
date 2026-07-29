package com.relationdetector.semantic.extract;

import java.nio.file.Path;
import java.util.List;

/**
 * CN: 保存完整evidence bundle路径、bounded shard路径和小型运行元数据；上游是磁盘planner，下游是顺序模型
 * 执行与artifact事务，本plan不持有完整bundle、全部prompt或模型输出。
 * EN: Carries paths to the complete evidence bundle and bounded shards plus small run metadata. It connects the
 * disk planner to sequential model/artifact execution without retaining the full bundle, all prompts, or outputs.
 */
public record SemanticPathRunPlan(
        Path fullBundlePath,
        String fullBundleHash,
        List<SemanticPathShard> shards,
        boolean reconcile,
        int maxInputTokens,
        Path ownerManifestPath,
        String ownerManifestHash
) {
    public SemanticPathRunPlan {
        if (fullBundlePath == null || fullBundleHash == null || fullBundleHash.isBlank()
                || shards == null || shards.isEmpty() || maxInputTokens <= 0
                || ownerManifestPath == null || ownerManifestHash == null || ownerManifestHash.isBlank()) {
            throw new IllegalArgumentException("semantic path run plan is incomplete");
        }
        fullBundlePath = fullBundlePath.toAbsolutePath().normalize();
        ownerManifestPath = ownerManifestPath.toAbsolutePath().normalize();
        shards = List.copyOf(shards);
    }

    public int ownedFactCount() {
        return shards.stream().mapToInt(SemanticPathShard::ownedFactCount).sum();
    }

    public int ownedCandidateCount() {
        return shards.stream().mapToInt(SemanticPathShard::ownedCandidateCount).sum();
    }
}
