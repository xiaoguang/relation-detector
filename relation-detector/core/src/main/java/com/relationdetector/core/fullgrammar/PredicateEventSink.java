package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.ExpressionSource;

/** Collects typed predicate events from direct-column and subquery traces. */
final class PredicateEventSink {
    private final SourceLocationSupport source;
    private final FullGrammarEventRecorder recorder;
    private final RowsetScopeSink rowsets;
    private final FullGrammarParseTreeAdapter parseTreeAdapter;
    private final DirectColumnTraceSupport directColumns;
    private final SubqueryProjectionTraceSupport subqueries;

    PredicateEventSink(
            SourceLocationSupport source,
            FullGrammarEventRecorder recorder,
            RowsetScopeSink rowsets,
            FullGrammarParseTreeAdapter parseTreeAdapter,
            DirectColumnTraceSupport directColumns,
            SubqueryProjectionTraceSupport subqueries
    ) {
        this.source = source;
        this.recorder = recorder;
        this.rowsets = rowsets;
        this.parseTreeAdapter = parseTreeAdapter;
        this.directColumns = directColumns;
        this.subqueries = subqueries;
    }

    void predicateEqualities(ParserRuleContext ctx, ParseTree predicate, String joinKind) {
        for (ColumnPair pair : equalityPairs(predicate)) {
            predicateEvent(pair.context(), StructuredParseEventType.PREDICATE_EQUALITY,
                    pair.left(), pair.right(), joinKind);
        }
    }

    void predicateEquality(
            ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression, String joinKind
    ) {
        Optional<FullGrammarColumnReference> left =
                directColumns.singlePredicateColumn(leftExpression, rightExpression);
        Optional<FullGrammarColumnReference> right =
                directColumns.singlePredicateColumn(rightExpression, leftExpression);
        if (left.isPresent() && right.isPresent()) {
            predicateEvent(ctx, StructuredParseEventType.PREDICATE_EQUALITY,
                    left.get(), right.get(), joinKind);
        }
    }

    void predicateEqualityColumns(
            ParserRuleContext ctx,
            String leftAlias,
            String leftColumn,
            String rightAlias,
            String rightColumn,
            String joinKind
    ) {
        FullGrammarColumnReference left =
                new FullGrammarColumnReference(source.clean(leftAlias), source.clean(leftColumn));
        FullGrammarColumnReference right =
                new FullGrammarColumnReference(source.clean(rightAlias), source.clean(rightColumn));
        if (!left.column().isBlank() && !right.column().isBlank()) {
            predicateEvent(ctx, StructuredParseEventType.PREDICATE_EQUALITY, left, right, joinKind);
        }
    }

    void existsPredicateEqualities(ParserRuleContext ctx, ParseTree predicate) {
        for (ColumnPair pair : equalityPairs(predicate)) {
            predicateEvent(pair.context(), StructuredParseEventType.EXISTS_PREDICATE,
                    pair.left(), pair.right(), "EXISTS");
        }
    }

    void existsPredicateEquality(ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression) {
        Optional<FullGrammarColumnReference> left =
                directColumns.singlePredicateColumn(leftExpression, rightExpression);
        Optional<FullGrammarColumnReference> right =
                directColumns.singlePredicateColumn(rightExpression, leftExpression);
        if (left.isPresent() && right.isPresent()) {
            predicateEvent(ctx, StructuredParseEventType.EXISTS_PREDICATE,
                    left.get(), right.get(), "EXISTS");
        }
    }

    void existsPredicateEqualityColumns(
            ParserRuleContext ctx,
            String leftAlias,
            String leftColumn,
            String rightAlias,
            String rightColumn
    ) {
        FullGrammarColumnReference left =
                new FullGrammarColumnReference(source.clean(leftAlias), source.clean(leftColumn));
        FullGrammarColumnReference right =
                new FullGrammarColumnReference(source.clean(rightAlias), source.clean(rightColumn));
        if (!left.column().isBlank() && !right.column().isBlank()) {
            predicateEvent(ctx, StructuredParseEventType.EXISTS_PREDICATE, left, right, "EXISTS");
        }
    }

    void inSubqueryPredicate(ParserRuleContext ctx, ParseTree outerExpression, ParseTree subquery) {
        List<FullGrammarColumnReference> outerColumns = directColumns.directColumnList(outerExpression);
        subqueries.selectColumns(subquery).ifPresent(inner -> addInSubqueryEvent(ctx, outerColumns, inner));
    }

    void joinUsing(ParserRuleContext ctx, String leftAlias, String rightAlias, List<String> columns) {
        if (source.clean(leftAlias).isBlank() || source.clean(rightAlias).isBlank() || columns.isEmpty()) {
            return;
        }
        recorder.predicate(ctx, StructuredParseEventType.JOIN_USING_COLUMNS,
                new ExpressionSource(source.clean(leftAlias), ""),
                new ExpressionSource(source.clean(rightAlias), ""),
                List.of(), List.of(), "", "", columns.stream()
                        .map(source::clean).filter(s -> !s.isBlank()).toList(), false);
    }

    private List<ColumnPair> equalityPairs(ParseTree tree) {
        List<ColumnPair> result = new ArrayList<>();
        collectEqualityPairs(tree, result, true);
        return result;
    }

    private void collectEqualityPairs(ParseTree tree, List<ColumnPair> result, boolean root) {
        if (tree == null) {
            return;
        }
        if (!root && parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.QUERY_BOUNDARY)) {
            return;
        }
        for (FullGrammarParseTreeAdapter.EqualityOperands operands
                : parseTreeAdapter.directEqualities(tree)) {
            Optional<FullGrammarColumnReference> left =
                    directColumns.singlePredicateColumn(operands.left(), operands.right());
            Optional<FullGrammarColumnReference> right =
                    directColumns.singlePredicateColumn(operands.right(), operands.left());
            if (left.isPresent() && right.isPresent()) {
                ParserRuleContext context = tree instanceof ParserRuleContext parserContext
                        ? parserContext
                        : null;
                if (context != null) {
                    result.add(new ColumnPair(context, left.get(), right.get()));
                }
                return;
            }
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectEqualityPairs(child, result, false);
        }
    }

    private void predicateEvent(
            ParserRuleContext ctx,
            StructuredParseEventType eventType,
            FullGrammarColumnReference left,
            FullGrammarColumnReference right,
            String joinKind
    ) {
        if (sameQualifier(left.qualifier(), right.qualifier())) {
            return;
        }
        recorder.predicate(ctx, eventType,
                new ExpressionSource(left.qualifier(), left.column()),
                new ExpressionSource(right.qualifier(), right.column()),
                List.of(), List.of(), "", source.blankTo(joinKind, "WHERE_OR_UNKNOWN"), List.of(), false);
    }

    private void addInSubqueryEvent(
            ParserRuleContext ctx,
            List<FullGrammarColumnReference> outerColumns,
            SubqueryProjectionTraceSupport.SelectColumns inner
    ) {
        List<FullGrammarColumnReference> innerColumns = inner.columns();
        if (outerColumns.size() == 1 && innerColumns.size() == 1) {
            recorder.predicate(ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                    expressionSource(outerColumns.get(0)), expressionSource(innerColumns.get(0)),
                    outerColumns.stream().map(this::expressionSource).toList(),
                    innerColumns.stream().map(this::expressionSource).toList(),
                    inner.table().isBlank() ? rowsets.tableFor(innerColumns.get(0).qualifier()) : inner.table(),
                    "", List.of(), true);
        } else if (outerColumns.size() > 1 && outerColumns.size() == innerColumns.size()) {
            recorder.predicate(ctx, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                    ExpressionSource.EMPTY, ExpressionSource.EMPTY,
                    outerColumns.stream().map(this::expressionSource).toList(),
                    innerColumns.stream().map(this::expressionSource).toList(),
                    inner.table().isBlank() ? rowsets.tableFor(innerColumns.get(0).qualifier()) : inner.table(),
                    "", List.of(), true);
        }
    }

    private ExpressionSource expressionSource(FullGrammarColumnReference column) {
        return new ExpressionSource(column.qualifier(), column.column());
    }

    private boolean sameQualifier(String leftQualifier, String rightQualifier) {
        return !source.clean(leftQualifier).isBlank()
                && source.clean(leftQualifier).equalsIgnoreCase(source.clean(rightQualifier));
    }

    private record ColumnPair(
            ParserRuleContext context,
            FullGrammarColumnReference left,
            FullGrammarColumnReference right
    ) {
    }
}
