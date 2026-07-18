package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 * CN: 汇总 Oracle expression 的 physical reads、VALUE/CONTROL roles 和 transform 分类，供 version visitor 发射 lineage events；不包含 generated context 或 mutable state。
 * EN: Summarizes physical reads, VALUE/CONTROL roles, and transform classification for an Oracle expression so version visitors can emit lineage events. It contains no generated context or mutable state.
 */
public record OracleExpressionAnalysis(
        List<OracleColumnRead> sources,
        LineageTransformType transform,
        LineageFlowKind flowKind
) {
    public static OracleExpressionAnalysis empty() {
        return new OracleExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
    }

    public static OracleExpressionAnalysis of(
            OracleColumnRead column,
            LineageTransformType transform,
            LineageFlowKind flowKind
    ) {
        return new OracleExpressionAnalysis(List.of(column), transform, flowKind);
    }

    public static OracleExpressionAnalysis combine(
            LineageTransformType transform,
            LineageFlowKind flowKind,
            OracleExpressionAnalysis left,
            OracleExpressionAnalysis right
    ) {
        List<OracleColumnRead> sources = new ArrayList<>();
        sources.addAll(left.sources());
        sources.addAll(right.sources());
        return new OracleExpressionAnalysis(sources.stream().distinct().toList(),
                LineageTransformClassifier.dominantForFlow(
                        flowKind, transform, left.transform(), right.transform()), flowKind);
    }

    public List<String> aliases() {
        return sources.stream().map(OracleColumnRead::alias).toList();
    }

    public List<String> columns() {
        return sources.stream().map(OracleColumnRead::column).toList();
    }
}
