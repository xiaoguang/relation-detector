package com.relationdetector.core;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import com.relationdetector.api.Evidence;
import com.relationdetector.api.Enums.EvidenceType;

/**
 * Implements the explainable scoring model from Phase 2.
 *
 * <p>Positive evidence is combined as 1 - product(1 - score). Negative evidence
 * multiplies the positive confidence by (1 + negativeScore), so a -0.30
 * mismatch reduces confidence by 30% while preserving the evidence trail.
 */
public final class ConfidenceCalculator {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    public BigDecimal calculate(List<Evidence> evidence) {
        BigDecimal product = BigDecimal.ONE;
        BigDecimal negativeMultiplier = BigDecimal.ONE;
        boolean hasDeclaredFk = false;

        for (Evidence item : evidence) {
            BigDecimal score = item.score();
            if (item.type() == EvidenceType.METADATA_FOREIGN_KEY) {
                hasDeclaredFk = true;
            }
            if (score.signum() >= 0) {
                product = product.multiply(BigDecimal.ONE.subtract(score, MC), MC);
            } else {
                negativeMultiplier = negativeMultiplier.multiply(BigDecimal.ONE.add(score, MC), MC);
            }
        }

        BigDecimal confidence = BigDecimal.ONE.subtract(product, MC).multiply(negativeMultiplier, MC);
        if (hasDeclaredFk && confidence.compareTo(new BigDecimal("0.95")) < 0) {
            confidence = new BigDecimal("0.95");
        }
        if (confidence.compareTo(new BigDecimal("0.99")) > 0) {
            confidence = new BigDecimal("0.99");
        }
        if (confidence.signum() < 0) {
            confidence = BigDecimal.ZERO;
        }
        return confidence.setScale(4, RoundingMode.HALF_UP);
    }
}
