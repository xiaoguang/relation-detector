package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.LineageTransformType;

/**
 * Data Lineage 候选的可解释证据。
 *
 * <p>CN: 记录 transform、分值、来源类型和诊断属性，帮助输出层解释字段血缘为何存在。
 *
 * <p>EN: Explainable evidence for a field-level Data Lineage candidate,
 * including transform, score, source provenance, and diagnostic attributes.
 */
public record DataLineageEvidence(
        LineageTransformType transformType,
        BigDecimal score,
        EvidenceSourceType sourceType,
        String source,
        String detail,
        Map<String, Object> attributes
) {
    public DataLineageEvidence {
        if (transformType == null) {
            throw new IllegalArgumentException("transformType is required");
        }
        if (score == null) {
            throw new IllegalArgumentException("score is required");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("sourceType is required");
        }
        if (attributes == null) {
            attributes = Map.of();
        } else {
            attributes = Map.copyOf(new LinkedHashMap<>(attributes));
        }
    }
}
