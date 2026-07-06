package com.relationdetector.semantic.kg;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Materialized KG edge. */
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
