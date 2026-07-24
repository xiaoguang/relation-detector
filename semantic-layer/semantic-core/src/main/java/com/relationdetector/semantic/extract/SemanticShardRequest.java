package com.relationdetector.semantic.extract;

/**
 * CN: 将一个 planned shard 与其完整 prompt 绑定；上游是 extraction service plan，下游是模型执行或 request-only artifact，禁止脱离 shard bundle 重建 prompt。
 * EN: Binds a planned shard to its complete prompt for model execution or request-only artifacts. The prompt must never be rebuilt without the shard bundle.
 */
public record SemanticShardRequest(SemanticShard shard, SemanticExtractionPrompt prompt) {
    public SemanticShardRequest {
        if (shard == null || prompt == null) {
            throw new IllegalArgumentException("semantic shard request is incomplete");
        }
    }
}
