package com.relationdetector.contracts.parse;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;

/**
 *
 * Typed expression dependency trace attached to projection and write events.
 */
public record ExpressionTrace(
        List<ExpressionSource> sources,
        LineageFlowKind flowKind,
        LineageTransformType transformType,
        boolean directColumn
) {
    public ExpressionTrace {
        sources = sources == null ? List.of() : List.copyOf(sources);
        flowKind = flowKind == null ? LineageFlowKind.VALUE : flowKind;
        transformType = transformType == null
                ? LineageTransformType.UNKNOWN_EXPRESSION
                : transformType;
    }

    public static ExpressionTrace empty() {
        return new ExpressionTrace(List.of(), LineageFlowKind.VALUE,
                LineageTransformType.UNKNOWN_EXPRESSION, false);
    }

    public static ExpressionTrace of(
            List<String> aliases,
            List<String> columns,
            LineageFlowKind flowKind,
            LineageTransformType transformType
    ) {
        List<ExpressionSource> sources = new ArrayList<>();
        int count = Math.min(aliases == null ? 0 : aliases.size(), columns == null ? 0 : columns.size());
        for (int index = 0; index < count; index++) {
            sources.add(new ExpressionSource(aliases.get(index), columns.get(index)));
        }
        return new ExpressionTrace(sources, flowKind, transformType,
                sources.size() == 1 && transformType == LineageTransformType.DIRECT);
    }

    public List<String> sourceAliases() {
        return sources.stream().map(ExpressionSource::alias).toList();
    }

    public List<String> sourceColumns() {
        return sources.stream().map(ExpressionSource::column).toList();
    }
}
