package com.relationdetector.semantic.extract;

import java.util.List;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 保存确定性 shard merge draft 与显式冲突；上游是 merger，下游是 reconciliation/final normalizer，禁止把冲突视为成功。
 * EN: Holds a deterministic shard-merge draft and explicit conflicts. It feeds reconciliation and final normalization and never treats unresolved conflicts as success.
 */
public record SemanticShardMergeResult(ObjectNode mergedDocument, List<SemanticShardConflict> conflicts) {
    public SemanticShardMergeResult {
        if (mergedDocument == null) {
            throw new IllegalArgumentException("merged semantic document is required");
        }
        mergedDocument = mergedDocument.deepCopy();
        conflicts = List.copyOf(conflicts == null ? List.of() : conflicts);
    }

    @Override
    public ObjectNode mergedDocument() {
        return mergedDocument.deepCopy();
    }

    ObjectNode trustedMergedDocument() {
        return mergedDocument;
    }

    public void requireConflictFree() {
        if (!conflicts.isEmpty()) {
            throw new SemanticExtractionValidationException("semantic shard merge contains unresolved conflicts");
        }
    }
}
