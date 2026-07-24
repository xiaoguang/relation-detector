package com.relationdetector.semantic.extract;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 保存单片 request、成功 provider response 与按片 bundle 归一化的文档；供 artifact 和 merge 使用，失败片不构造该值。
 * EN: Carries one shard request, successful provider response, and shard-normalized document for artifacts and merging. Failed shards never construct this value.
 */
public record SemanticShardExecution(
        SemanticShardRequest request,
        SemanticExtractionResult result,
        ObjectNode normalizedDocument
) {
    public SemanticShardExecution {
        if (request == null || result == null || normalizedDocument == null) {
            throw new IllegalArgumentException("semantic shard execution is incomplete");
        }
        normalizedDocument = normalizedDocument.deepCopy();
    }

    @Override
    public ObjectNode normalizedDocument() {
        return normalizedDocument.deepCopy();
    }

    ObjectNode trustedNormalizedDocument() {
        return normalizedDocument;
    }
}
