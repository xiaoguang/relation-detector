package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 绑定 shard ID 与已通过该 shard evidence bundle normalization 的文档；上游是模型执行器，下游是 merger，禁止携带未验证 raw output。
 * EN: Binds a shard id to a document normalized against that shard's evidence bundle. It is consumed by the merger and must never carry unvalidated raw output.
 */
public record SemanticShardNormalizedResult(String shardId, ObjectNode document) {
    public SemanticShardNormalizedResult {
        if (shardId == null || shardId.isBlank() || document == null) {
            throw new IllegalArgumentException("normalized shard result is incomplete");
        }
        document = document.deepCopy();
    }

    @Override
    public ObjectNode document() {
        return document.deepCopy();
    }

    ObjectNode trustedDocument() {
        return document;
    }
}
