package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * CN: 保存冲突 stable ID 的一个 shard 变体和 canonical hash；仅供 reconciliation 选择，禁止直接覆盖已合并文档。
 * EN: Carries one shard variant and canonical hash for a conflicting stable id. It is only selectable by reconciliation and never overwrites the merged document directly.
 */
public record SemanticShardVariant(String shardId, String hash, JsonNode document) {
    public SemanticShardVariant {
        document = document == null ? null : document.deepCopy();
    }

    @Override
    public JsonNode document() {
        return document == null ? null : document.deepCopy();
    }

    JsonNode trustedDocument() {
        return document;
    }
}
