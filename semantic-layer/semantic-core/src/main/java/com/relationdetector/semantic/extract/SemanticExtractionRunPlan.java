package com.relationdetector.semantic.extract;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 保存完整 bundle、validated shard plan 和稳定请求序列；由 service 在任何网络调用前产生，供 request-only、Codex session 与 API 执行共用。
 * EN: Carries the complete bundle, validated shard plan, and stable request sequence produced before any network call for request-only, Codex-session, and API execution.
 */
public record SemanticExtractionRunPlan(
        ObjectNode fullBundle,
        SemanticShardPlan shardPlan,
        List<SemanticShardRequest> shardRequests,
        boolean reconcile
) {
    public SemanticExtractionRunPlan {
        if (fullBundle == null || shardPlan == null) {
            throw new IllegalArgumentException("semantic extraction run plan is incomplete");
        }
        fullBundle = fullBundle.deepCopy();
        shardRequests = List.copyOf(shardRequests == null ? List.of() : shardRequests);
        if (shardRequests.size() != shardPlan.shards().size()) {
            throw new IllegalArgumentException("semantic shard request count does not match shard plan");
        }
    }

    @Override
    public ObjectNode fullBundle() {
        return fullBundle.deepCopy();
    }

    ObjectNode trustedFullBundle() {
        return fullBundle;
    }
}
