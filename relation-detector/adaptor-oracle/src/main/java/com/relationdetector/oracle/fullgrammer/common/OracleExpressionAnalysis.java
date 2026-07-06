package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;

/**
 * Expression lineage summary shared by Oracle full-grammer version visitors.
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
                dominant(transform, left.transform(), right.transform()), flowKind);
    }

    public static LineageTransformType dominant(LineageTransformType... transforms) {
        LineageTransformType dominant = LineageTransformType.DIRECT;
        for (LineageTransformType transform : transforms) {
            if (priority(transform) > priority(dominant)) {
                dominant = transform;
            }
        }
        return dominant;
    }

    private static int priority(LineageTransformType transform) {
        return switch (transform) {
            case CASE_WHEN -> 8;
            case CUMULATIVE -> 7;
            case AGGREGATE -> 6;
            case WINDOW_DERIVED -> 5;
            case COALESCE -> 4;
            case CONCAT_FORMAT -> 3;
            case ARITHMETIC -> 2;
            case FUNCTION_CALL -> 1;
            default -> 0;
        };
    }

    public List<String> aliases() {
        return sources.stream().map(OracleColumnRead::alias).toList();
    }

    public List<String> columns() {
        return sources.stream().map(OracleColumnRead::column).toList();
    }
}
