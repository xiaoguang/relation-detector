package com.relationdetector.contracts.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;

/**
 * 字段级 Data Lineage 候选。
 *
 * <p>CN: 该模型独立于 RelationshipCandidate，用于表达一个目标字段从哪些物理字段
 * 取值或受控。它的 confidence 只解释血缘可信度，不参与 relationship confidence。
 *
 * <p>EN: Field-level Data Lineage candidate independent from RelationshipCandidate.
 * Its confidence explains lineage trust only and never feeds relationship confidence.
 */
public final class DataLineageCandidate {
    private final List<Endpoint> sources;
    private final Endpoint target;
    private final LineageFlowKind flowKind;
    private final LineageTransformType transformType;
    private BigDecimal confidence = BigDecimal.ZERO;
    private final List<DataLineageEvidence> evidence = new ArrayList<>();
    private final List<DataLineageEvidence> rawEvidence = new ArrayList<>();
    private final List<WarningMessage> warnings = new ArrayList<>();
    private final Map<String, Object> attributes = new LinkedHashMap<>();

    public DataLineageCandidate(
            List<Endpoint> sources,
            Endpoint target,
            LineageFlowKind flowKind,
            LineageTransformType transformType
    ) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("sources are required");
        }
        if (target == null || !target.isColumnLevel()) {
            throw new IllegalArgumentException("target column is required");
        }
        if (flowKind == null) {
            throw new IllegalArgumentException("flowKind is required");
        }
        if (transformType == null) {
            throw new IllegalArgumentException("transformType is required");
        }
        this.sources = new ArrayList<>(sources);
        this.target = target;
        this.flowKind = flowKind;
        this.transformType = transformType;
    }

    public List<Endpoint> sources() {
        return sources;
    }

    public Endpoint target() {
        return target;
    }

    public LineageFlowKind flowKind() {
        return flowKind;
    }

    public LineageTransformType transformType() {
        return transformType;
    }

    public BigDecimal confidence() {
        return confidence;
    }

    public void confidence(BigDecimal confidence) {
        this.confidence = confidence;
    }

    public List<DataLineageEvidence> evidence() {
        return evidence;
    }

    public List<DataLineageEvidence> rawEvidence() {
        return rawEvidence;
    }

    public List<WarningMessage> warnings() {
        return warnings;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}
