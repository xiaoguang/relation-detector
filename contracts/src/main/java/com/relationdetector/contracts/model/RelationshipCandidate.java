package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.RelationSubType;
import com.relationdetector.contracts.Enums.RelationType;

/**
 * relationship 扫描与合并期间的可变候选。
 *
 * <p>CN: 多个来源会在 merge 前追加 evidence、rawEvidence 和 warning，因此该类保持
 * 可变；最终输出由 writer 读取这些字段渲染。
 *
 * <p>EN: Mutable relationship candidate used during scanning and merging.
 * Multiple sources append evidence, rawEvidence, and warnings before final
 * output, so the candidate intentionally remains mutable.
 */
public final class RelationshipCandidate {
    private final Endpoint source;
    private final Endpoint target;
    private RelationType relationType;
    private RelationSubType relationSubType;
    private BigDecimal confidence = BigDecimal.ZERO;
    private final List<Evidence> evidence = new ArrayList<>();
    private final List<Evidence> rawEvidence = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();

    public RelationshipCandidate(Endpoint source, Endpoint target, RelationType relationType, RelationSubType relationSubType) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("source and target are required");
        }
        this.source = source;
        this.target = target;
        this.relationType = relationType;
        this.relationSubType = relationSubType;
    }

    public Endpoint source() {
        return source;
    }

    public Endpoint target() {
        return target;
    }

    public RelationType relationType() {
        return relationType;
    }

    public void relationType(RelationType relationType) {
        this.relationType = relationType;
    }

    public RelationSubType relationSubType() {
        return relationSubType;
    }

    public void relationSubType(RelationSubType relationSubType) {
        this.relationSubType = relationSubType;
    }

    public BigDecimal confidence() {
        return confidence;
    }

    public void confidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public List<Evidence> evidence() {
        return evidence;
    }

    public List<Evidence> rawEvidence() {
        return rawEvidence;
    }

    public List<WarningMessage> warnings() {
        return warnings;
    }
}
