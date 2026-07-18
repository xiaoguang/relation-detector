package com.relationdetector.core.profile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceSourceType;
import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.scoring.DefaultEvidenceScores;
import com.relationdetector.contracts.spi.ProfileRequest;

/**
 * CN: 将脱敏的有界 profiling metrics 转换为 containment、overlap 或受策略约束的 negative evidence。
 * EN: Converts sanitized bounded profiling metrics into containment, overlap, or policy-restricted negative evidence.
 */
public final class DataProfileEvidenceBuilder {
    private final NegativeProfileEvidencePolicy negativePolicy = new NegativeProfileEvidencePolicy();

    /**
     * CN: 将一次完整 live profile 的计数按请求阈值转换为白名单 evidence；超时、权限失败、
     * 空指标或零 distinct 返回空结果。负向 evidence 还必须通过声明式 FK 策略，且本方法
     * 只返回不可变列表，不修改候选。
     *
     * EN: Converts one complete live-profile metric set into allowlisted evidence
     * under the request thresholds. Timeout, permission failure, absent metrics,
     * or zero distinct input yields no evidence; negative evidence additionally
     * requires the declared-FK policy. The candidate is never mutated.
     */
    public List<Evidence> build(ProfileRequest request, DataProfileMetrics metrics, String sourceName) {
        if (request == null || metrics == null || metrics.queryTimedOut() || metrics.permissionDenied()
                || metrics.sourceDistinctValues() <= 0) {
            return List.of();
        }

        List<Evidence> result = new ArrayList<>();
        double containmentRatio = ratio(metrics.matchedDistinctSourceValues(), metrics.sourceDistinctValues());
        double overlapRatio = ratio(metrics.matchedDistinctSourceValues(),
                denominatorForOverlap(metrics));
        double missingRatio = ratio(metrics.missingDistinctSourceValues(), metrics.sourceDistinctValues());

        if (metrics.sourceDistinctValues() >= request.options().minDistinctValues()
                && containmentRatio >= request.options().minContainmentRatio()) {
            result.add(evidence(EvidenceType.VALUE_CONTAINMENT_HIGH, DefaultEvidenceScores.VALUE_CONTAINMENT_HIGH,
                    sourceName, "source values are highly contained by target values",
                    attributes(request, metrics, containmentRatio, overlapRatio, missingRatio)));
        } else if (metrics.sourceDistinctValues() >= request.options().minDistinctValues()
                && metrics.matchedDistinctSourceValues() > 0
                && overlapRatio >= request.options().minOverlapRatio()) {
            result.add(evidence(EvidenceType.VALUE_OVERLAP_HIGH, DefaultEvidenceScores.VALUE_OVERLAP_HIGH,
                    sourceName, "source and target values have high overlap",
                    attributes(request, metrics, containmentRatio, overlapRatio, missingRatio)));
        }

        if (negativePolicy.allows(request, metrics)
                && metrics.sourceDistinctValues() >= request.options().minDistinctValues()
                && metrics.sourceNonNullRows() >= request.options().minRowsForNegative()
                && missingRatio >= request.options().maxMismatchRatio()) {
            Map<String, Object> negativeAttributes = new LinkedHashMap<>(
                    attributes(request, metrics, containmentRatio, overlapRatio, missingRatio));
            negativeAttributes.put("negativePolicy", "DECLARED_FOREIGN_KEY_ONLY");
            result.add(evidence(EvidenceType.NEGATIVE_VALUE_MISMATCH,
                    DefaultEvidenceScores.NEGATIVE_VALUE_MISMATCH,
                    sourceName,
                    "source values are frequently missing from target values",
                    negativeAttributes));
        }

        return List.copyOf(result);
    }

    private Evidence evidence(
            EvidenceType type,
            double score,
            String source,
            String detail,
            Map<String, Object> attributes
    ) {
        return new Evidence(type, BigDecimal.valueOf(score), EvidenceSourceType.DATA_PROFILE,
                source == null || source.isBlank() ? "data-profile" : source,
                detail,
                attributes);
    }

    private Map<String, Object> attributes(
            ProfileRequest request,
            DataProfileMetrics metrics,
            double containmentRatio,
            double overlapRatio,
            double missingRatio
    ) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("profileMode", metrics.profileMode());
        attributes.put("containmentRatio", ratioText(containmentRatio));
        attributes.put("overlapRatio", ratioText(overlapRatio));
        attributes.put("missingRatio", ratioText(missingRatio));
        attributes.put("matchedDistinctSourceValues", metrics.matchedDistinctSourceValues());
        attributes.put("missingDistinctSourceValues", metrics.missingDistinctSourceValues());
        attributes.put("sourceDistinctValues", metrics.sourceDistinctValues());
        attributes.put("sourceNonNullRows", metrics.sourceNonNullRows());
        attributes.put("targetDistinctValues", metrics.targetDistinctValues());
        attributes.put("minContainmentRatio", request.options().minContainmentRatio());
        attributes.put("minOverlapRatio", request.options().minOverlapRatio());
        attributes.put("maxMismatchRatio", request.options().maxMismatchRatio());
        attributes.put("minDistinctValues", request.options().minDistinctValues());
        attributes.put("minRowsForNegative", request.options().minRowsForNegative());
        attributes.put("queryTimedOut", metrics.queryTimedOut());
        attributes.put("permissionDenied", metrics.permissionDenied());
        return attributes;
    }

    private long denominatorForOverlap(DataProfileMetrics metrics) {
        long target = metrics.targetDistinctValues();
        if (target <= 0) {
            return metrics.sourceDistinctValues();
        }
        return Math.min(metrics.sourceDistinctValues(), target);
    }

    private double ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, (double) numerator / (double) denominator));
    }

    private String ratioText(double value) {
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }
}
