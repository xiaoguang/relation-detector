package com.relationdetector.api;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.api.Enums.EvidenceSourceType;
import com.relationdetector.api.Enums.EvidenceType;

/**
 * One explainable signal supporting or weakening a relationship.
 *
 * <p>Design mapping: Phase 2 Evidence model. The final confidence must be
 * reproducible from evidence scores, so evidence is part of public output.
 */
public record Evidence(
        EvidenceType type,
        BigDecimal score,
        EvidenceSourceType sourceType,
        String source,
        String detail,
        Map<String, Object> attributes
) {
    public Evidence {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
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

    public static Evidence of(EvidenceType type, double score, EvidenceSourceType sourceType, String source, String detail) {
        return new Evidence(type, BigDecimal.valueOf(score), sourceType, source, detail, Map.of());
    }
}
