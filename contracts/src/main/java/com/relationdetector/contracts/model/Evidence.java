package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;

/**
 * relationship 候选的一条可解释证据。
 *
 * <p>CN: 最终 confidence 必须能从 evidence score 复现，所以 Evidence 是公共输出的一部分。
 * parser、metadata 和 data-profile 都通过 Evidence 描述自己贡献了什么信号。
 *
 * <p>EN: One explainable signal supporting a relationship candidate. Final
 * confidence must be reproducible from evidence scores, so Evidence is part of
 * public output and records parser/metadata/profile provenance.
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
