package com.relationdetector.semantic.kg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CN: KG 中一个已 materialize 的 node，保存 stable id、type、confidence、review、evidence 和 attributes；构造器规范默认值并冻结集合。
 * EN: One materialized KG node carrying stable identity, type, confidence, review status, evidence, and attributes. Construction normalizes defaults and freezes collections.
 */
public record SemanticNode(
        String id,
        String type,
        String label,
        BigDecimal confidence,
        String reviewStatus,
        List<String> evidenceRefs,
        Map<String, Object> attributes
) {
    public SemanticNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id is required");
        }
        type = type == null || type.isBlank() ? "Unknown" : type;
        label = label == null || label.isBlank() ? id : label;
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        reviewStatus = reviewStatus == null || reviewStatus.isBlank() ? "EVIDENCE_SUPPORTED" : reviewStatus;
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
