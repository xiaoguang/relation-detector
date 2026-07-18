package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.Map;

/**
 * CN: 从 relation-detector fact 中解析的一条稳定 evidence observation，保存类型、来源、得分、detail 和 attributes；不会改变原始 evidence 分类。
 * EN: One stable evidence observation resolved from relation-detector output with type, source, score, detail, and attributes. It never changes the original evidence classification.
 */
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
