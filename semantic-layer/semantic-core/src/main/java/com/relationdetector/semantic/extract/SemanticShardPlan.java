package com.relationdetector.semantic.extract;

import java.util.List;
import java.util.Map;

/**
 * CN: 保存完整 bundle hash、稳定 shard 顺序以及 fact/candidate 的唯一 owner；上游是 planner，下游是覆盖校验和执行器，禁止表示部分成功。
 * EN: Holds the full-bundle hash, stable shard order, and unique fact/candidate owners. It is consumed by coverage validation and execution and cannot represent partial success.
 */
public record SemanticShardPlan(
        String fullBundleHash,
        List<SemanticShard> shards,
        Map<String, String> factOwners,
        Map<String, String> candidateOwners
) {
    public SemanticShardPlan {
        if (fullBundleHash == null || fullBundleHash.isBlank()) {
            throw new IllegalArgumentException("full bundle hash is required");
        }
        shards = List.copyOf(shards == null ? List.of() : shards);
        factOwners = Map.copyOf(factOwners == null ? Map.of() : factOwners);
        candidateOwners = Map.copyOf(candidateOwners == null ? Map.of() : candidateOwners);
    }
}
