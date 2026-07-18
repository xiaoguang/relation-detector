package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * CN: 表示 projection 输出列及其 typed expression trace，也可承载独立表达式来源。
 * EN: Represents a projection output and its typed expression trace, or a standalone expression-source event.
 */
public record ProjectionEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        String outputAlias,
        String outputColumn,
        ExpressionTrace expression
) implements StructuredSqlEvent {
    public ProjectionEvent {
        outputAlias = outputAlias == null ? "" : outputAlias;
        outputColumn = outputColumn == null ? "" : outputColumn;
        expression = expression == null ? ExpressionTrace.empty() : expression;
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new ProjectionEvent(type, value, outputAlias, outputColumn, expression);
    }
}
