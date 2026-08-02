package com.relationdetector.contracts.model;

import java.util.List;

import com.relationdetector.contracts.Enums.DerivedEvidenceHopKind;

/**
 * CN: 表示一条 derived 结构路径中的单跳及其全部直接支持引用；输入是已规范化端点、类型和引用，
 * 输出为不可变审计值。本类型不展开证据组合，也不计算路径置信度。
 * EN: Represents one structural hop in a derived path together with all direct support references. It is an
 * immutable audit value and neither expands evidence combinations nor computes path confidence.
 */
public record DerivedEvidenceHop(
        int ordinal,
        Endpoint source,
        Endpoint target,
        DerivedEvidenceHopKind kind,
        List<String> evidenceRefs
) {
    public DerivedEvidenceHop {
        if (ordinal <= 0) {
            throw new IllegalArgumentException("ordinal must be positive");
        }
        if (source == null || target == null || kind == null) {
            throw new IllegalArgumentException("source, target, and kind are required");
        }
        if (evidenceRefs == null || evidenceRefs.isEmpty()
                || evidenceRefs.stream().anyMatch(ref -> ref == null || ref.isBlank())) {
            throw new IllegalArgumentException("evidenceRefs must contain non-blank references");
        }
        evidenceRefs = evidenceRefs.stream().distinct().sorted().toList();
    }
}
