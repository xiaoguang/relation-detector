package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 *
 * Projection item or standalone expression-source event.
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
