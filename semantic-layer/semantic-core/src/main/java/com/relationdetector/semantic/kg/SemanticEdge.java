package com.relationdetector.semantic.kg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CN: KG 中一个有向 materialized edge，保存 source/target、type、confidence 和 evidence refs；缺失 endpoint 明确失败，id 冲突由 builder 防御。
 * EN: One directional materialized KG edge with source, target, type, confidence, and evidence references. Missing endpoints fail and builder checks defend against id conflicts.
 */
public record SemanticEdge(
        String id,
        String type,
        String source,
        String target,
        BigDecimal confidence,
        List<String> evidenceRefs,
        Map<String, Object> attributes
) {
    public SemanticEdge {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("edge id is required");
        }
        if (source == null || source.isBlank() || target == null || target.isBlank()) {
            throw new IllegalArgumentException("edge source and target are required");
        }
        type = type == null || type.isBlank() ? "RELATED_TO" : type;
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
