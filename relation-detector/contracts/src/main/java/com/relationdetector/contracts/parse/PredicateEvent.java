package com.relationdetector.contracts.parse;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 *
 * Equality, USING, EXISTS, IN, or tuple-IN predicate event.
 */
public record PredicateEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        ExpressionSource left,
        ExpressionSource right,
        List<ExpressionSource> outerSources,
        List<ExpressionSource> innerSources,
        String innerTable,
        String joinKind,
        List<String> usingColumns,
        boolean verifiedColumnSubquery,
        List<PredicateGuard> predicateGuards
) implements StructuredSqlEvent {
    public PredicateEvent(
            StructuredParseEventType type,
            SourceProvenance provenance,
            ExpressionSource left,
            ExpressionSource right,
            List<ExpressionSource> outerSources,
            List<ExpressionSource> innerSources,
            String innerTable,
            String joinKind,
            List<String> usingColumns,
            boolean verifiedColumnSubquery
    ) {
        this(type, provenance, left, right, outerSources, innerSources, innerTable,
                joinKind, usingColumns, verifiedColumnSubquery, List.of());
    }

    public PredicateEvent {
        left = left == null ? ExpressionSource.EMPTY : left;
        right = right == null ? ExpressionSource.EMPTY : right;
        outerSources = outerSources == null ? List.of() : List.copyOf(outerSources);
        innerSources = innerSources == null ? List.of() : List.copyOf(innerSources);
        innerTable = innerTable == null ? "" : innerTable;
        joinKind = joinKind == null ? "" : joinKind;
        usingColumns = usingColumns == null ? List.of() : List.copyOf(usingColumns);
        predicateGuards = predicateGuards == null ? List.of() : List.copyOf(predicateGuards);
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new PredicateEvent(type, value, left, right, outerSources, innerSources,
                innerTable, joinKind, usingColumns, verifiedColumnSubquery, predicateGuards);
    }
}
