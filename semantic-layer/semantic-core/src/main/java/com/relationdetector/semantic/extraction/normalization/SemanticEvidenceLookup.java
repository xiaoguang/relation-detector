package com.relationdetector.semantic.extraction.normalization;

import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

/**
 * CN: 为正式 semantic 结果校验提供只读 reference/evidence 查询边界；输入来自实时 evidence store 或
 * request package 重建索引，输出仅为存在性和 evidence payload。本接口不拥有事实、不执行语义推断。
 * EN: Provides the read-only reference and evidence lookup boundary used by formal semantic result validation.
 * Implementations may wrap a live evidence store or a reconstructed request package and must not infer facts.
 */
public interface SemanticEvidenceLookup {
    public boolean containsReference(String reference);

    public Optional<JsonNode> findEvidence(String reference);

    public static SemanticEvidenceLookup from(SemanticEvidenceStore store) {
        if (store == null) {
            throw new IllegalArgumentException("semantic evidence store is required");
        }
        return new SemanticEvidenceLookup() {
            @Override
            public boolean containsReference(String reference) {
                return store.containsReference(reference);
            }

            @Override
            public Optional<JsonNode> findEvidence(String reference) {
                return store.find(SemanticEvidenceStore.Section.EVIDENCE, reference);
            }
        };
    }
}
