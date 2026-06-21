package com.relationdetector.core.scoring;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

import com.relationdetector.contracts.model.Evidence;
import com.relationdetector.contracts.Enums.EvidenceType;

/**
 * Phase 2 的可解释 relationship scoring 公式实现。
 *
 * <p>CN: 正向 evidence 按 {@code 1 - product(1 - score)} 合并；负向 evidence 通过
 * {@code (1 + negativeScore)} 乘到正向 confidence 上。Data Lineage confidence 不走
 * 这里。
 *
 * <p>EN: Explainable relationship scoring model from Phase 2. Positive evidence
 * combines as {@code 1 - product(1 - score)}; negative evidence multiplies the
 * positive confidence by {@code (1 + negativeScore)}. Data Lineage confidence is separate.
 */
public final class ConfidenceCalculator {
    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    /**
     * 根据 evidence 列表计算最终 relationship confidence。
     *
     * <p>EN: Calculates final relationship confidence from evidence items.
     */
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
