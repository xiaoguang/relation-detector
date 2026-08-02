package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;

/**
 * CN: 以线性 hop 集合表达一条 derived 路径的全部直接支持，并用组合计数保留未展开的证据组合规模；
 * 输入是有序 hops、组合数和路径置信度，输出为不可变 wire 模型。本类型禁止物化笛卡尔积。
 * EN: Represents all direct support for one derived path as linear hops while retaining the unexpanded support
 * cardinality. It is an immutable wire model and must never materialize the Cartesian product.
 */
public record DerivedEvidenceSet(
        List<DerivedEvidenceHop> hops,
        BigInteger combinationCount,
        BigDecimal confidence
) {
    public DerivedEvidenceSet {
        if (hops == null || hops.isEmpty()) {
            throw new IllegalArgumentException("hops must not be empty");
        }
        hops = List.copyOf(hops);
        for (int index = 0; index < hops.size(); index++) {
            if (hops.get(index).ordinal() != index + 1) {
                throw new IllegalArgumentException("hop ordinals must be contiguous from one");
            }
            if (index > 0 && !hops.get(index - 1).target().equals(hops.get(index).source())) {
                throw new IllegalArgumentException("hop endpoints must form a closed path");
            }
        }
        if (combinationCount == null || combinationCount.signum() <= 0) {
            throw new IllegalArgumentException("combinationCount must be positive");
        }
        BigInteger expected = hops.stream()
                .map(hop -> BigInteger.valueOf(hop.evidenceRefs().size()))
                .reduce(BigInteger.ONE, BigInteger::multiply);
        if (!expected.equals(combinationCount)) {
            throw new IllegalArgumentException("combinationCount must equal the hop support product");
        }
        if (confidence == null || confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("confidence must be within [0,1]");
        }
    }
}
