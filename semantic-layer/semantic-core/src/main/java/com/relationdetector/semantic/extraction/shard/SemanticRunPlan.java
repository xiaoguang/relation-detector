package com.relationdetector.semantic.extraction.shard;

import com.relationdetector.semantic.extraction.artifact.SemanticArtifactRef;

import java.util.List;

/**
 * CN: 保存完整 bundle、owner manifest 与 bounded shard 的强摘要引用及模型预算。
 * EN: Carries strong file references for the complete bundle, owner manifest, and bounded shards together with the
 * model budgets. It never retains full bundle, prompt, or model-output content.
 */
public record SemanticRunPlan(
        SemanticArtifactRef fullBundle,
        List<SemanticShardDescriptor> shards,
        boolean reconcile,
        int maxInputTokens,
        int shardMaxOutputTokens,
        int reconciliationMaxOutputTokens,
        SemanticArtifactRef ownerManifest
) {
    public SemanticRunPlan {
        if (fullBundle == null || shards == null || shards.isEmpty() || maxInputTokens <= 0
                || shardMaxOutputTokens <= 0 || reconciliationMaxOutputTokens <= 0
                || ownerManifest == null) {
            throw new IllegalArgumentException("semantic path run plan is incomplete");
        }
        shards = List.copyOf(shards);
    }

    public int ownedFactCount() {
        return shards.stream().mapToInt(SemanticShardDescriptor::ownedFactCount).sum();
    }

    public int ownedCandidateCount() {
        return shards.stream().mapToInt(SemanticShardDescriptor::ownedCandidateCount).sum();
    }
}
