package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.LineageTransformType;

/** Explainable evidence for one field-level Data Lineage candidate. */
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
