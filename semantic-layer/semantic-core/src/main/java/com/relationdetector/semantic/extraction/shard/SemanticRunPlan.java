package com.relationdetector.semantic.extraction.shard;

import java.nio.file.Path;
import java.util.List;

/**
 * CN: 保存完整evidence bundle、bounded shard、输入预算和两类模型输出预算；上游是磁盘planner，下游是
 * 顺序模型/Codex完成与artifact事务，本plan不持有完整bundle、全部prompt或模型输出。
 * EN: Carries paths to the complete evidence bundle and bounded shards together with input and phase-specific output
 * budgets. It connects the disk planner to sequential model/Codex completion and artifact execution without retaining
 * the full bundle, all prompts, or outputs.
 */
public record SemanticRunPlan(
        Path fullBundlePath,
        String fullBundleHash,
        List<SemanticShardDescriptor> shards,
        boolean reconcile,
        int maxInputTokens,
        int shardMaxOutputTokens,
        int reconciliationMaxOutputTokens,
        Path ownerManifestPath,
        String ownerManifestHash
) {
    public SemanticRunPlan {
        if (fullBundlePath == null || fullBundleHash == null || fullBundleHash.isBlank()
                || shards == null || shards.isEmpty() || maxInputTokens <= 0
                || shardMaxOutputTokens <= 0 || reconciliationMaxOutputTokens <= 0
                || ownerManifestPath == null || ownerManifestHash == null || ownerManifestHash.isBlank()) {
            throw new IllegalArgumentException("semantic path run plan is incomplete");
        }
        fullBundlePath = fullBundlePath.toAbsolutePath().normalize();
        ownerManifestPath = ownerManifestPath.toAbsolutePath().normalize();
        shards = List.copyOf(shards);
    }

    public int ownedFactCount() {
        return shards.stream().mapToInt(SemanticShardDescriptor::ownedFactCount).sum();
    }

    public int ownedCandidateCount() {
        return shards.stream().mapToInt(SemanticShardDescriptor::ownedCandidateCount).sum();
    }
}
