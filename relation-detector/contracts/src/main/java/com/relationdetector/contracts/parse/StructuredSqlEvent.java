package com.relationdetector.contracts.parse;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * Strongly typed parser fact shared by token-event and full-grammar frontends.
 */
public sealed interface StructuredSqlEvent permits RowsetEvent, PredicateEvent,
        ProjectionEvent, WriteEvent, DdlEvent, DynamicSqlEvent {

    StructuredParseEventType type();

    SourceProvenance provenance();

    StructuredSqlEvent withProvenance(SourceProvenance provenance);

    default String sourceName() {
        return provenance().sourceName();
    }

    default long line() {
        return provenance().line();
    }

    default String statementScope() {
        return provenance().statementScope();
    }

    default String keyword() { return ""; }
    default String name() { return ""; }
    default String table() { return ""; }
    default String qualifiedTable() { return ""; }
    default String alias() { return ""; }
    default String targetAlias() { return ""; }
    default String targetTable() { return ""; }
    default String targetColumn() { return ""; }
    default String sourceTable() { return ""; }
    default String sourceColumn() { return ""; }
    default String column() { return ""; }
    default String role() { return ""; }
    default String kind() { return ""; }
    default String reason() { return ""; }
    default String mappingKind() { return ""; }
    default String joinKind() { return ""; }
    default String innerTable() { return ""; }
    default String outputAlias() { return ""; }
    default String outputColumn() { return ""; }
    default ExpressionSource left() { return ExpressionSource.EMPTY; }
    default ExpressionSource right() { return ExpressionSource.EMPTY; }
    default List<ExpressionSource> outerSources() { return List.of(); }
    default List<ExpressionSource> innerSources() { return List.of(); }
    default List<String> usingColumns() { return List.of(); }
    default boolean verifiedColumnSubquery() { return false; }
    default List<PredicateGuard> predicateGuards() { return List.of(); }
    default int compositePosition() { return 1; }
    default int compositeSize() { return 1; }
    default ExpressionTrace expression() { return ExpressionTrace.empty(); }

    default String semanticKey() {
        return type().name() + "|" + this;
    }
}
