package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.Map;

/** One resolved evidence observation copied from relation-detector output. */
public record EvidenceReference(
        String id,
        String evidenceType,
        String sourceType,
        BigDecimal score,
        String source,
        String detail,
        Map<String, Object> attributes
) {
    public EvidenceReference {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("evidence reference id is required");
        }
        evidenceType = evidenceType == null || evidenceType.isBlank() ? "UNKNOWN" : evidenceType;
        sourceType = sourceType == null || sourceType.isBlank() ? "UNKNOWN" : sourceType;
        score = score == null ? BigDecimal.ZERO : score;
        source = source == null ? "" : source;
        detail = detail == null ? "" : detail;
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
