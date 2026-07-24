package com.relationdetector.semantic.extract;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 保存完整分片执行、deterministic merge、可选 reconciliation 及最终全 bundle 归一化文档；只表示原子成功，不表示部分完成。
 * EN: Holds complete shard executions, deterministic merge, optional reconciliation, and the final full-bundle-normalized document. It represents atomic success only.
 */
public record SemanticExtractionRunResult(
        SemanticExtractionRunPlan plan,
        List<SemanticShardExecution> shardExecutions,
        SemanticShardMergeResult mergeResult,
        SemanticExtractionPrompt reconciliationPrompt,
        SemanticExtractionResult reconciliationResult,
        JsonNode reconciliationPatch,
        ObjectNode mergedDraft,
        ObjectNode finalDocument
) {
    public SemanticExtractionRunResult {
        if (plan == null || mergeResult == null || mergedDraft == null || finalDocument == null) {
            throw new IllegalArgumentException("semantic extraction run result is incomplete");
        }
        shardExecutions = List.copyOf(shardExecutions == null ? List.of() : shardExecutions);
        reconciliationPatch = reconciliationPatch == null ? null : reconciliationPatch.deepCopy();
        mergedDraft = mergedDraft.deepCopy();
        finalDocument = finalDocument.deepCopy();
    }

    @Override
    public JsonNode reconciliationPatch() {
        return reconciliationPatch == null ? null : reconciliationPatch.deepCopy();
    }

    @Override
    public ObjectNode mergedDraft() {
        return mergedDraft.deepCopy();
    }

    @Override
    public ObjectNode finalDocument() {
        return finalDocument.deepCopy();
    }

    JsonNode trustedReconciliationPatch() {
        return reconciliationPatch;
    }

    ObjectNode trustedMergedDraft() {
        return mergedDraft;
    }

    ObjectNode trustedFinalDocument() {
        return finalDocument;
    }
}
