package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.ExpressionSource;

/** Collects typed predicate events from direct-column and subquery traces. */
final class PredicateEventSink {
    private final SourceLocationSupport source;
    private final FullGrammerEventRecorder recorder;
    private final RowsetScopeSink rowsets;
    private final FullGrammerParseTreeAdapter parseTreeAdapter;
    private final DirectColumnTraceSupport directColumns;
    private final SubqueryProjectionTraceSupport subqueries;

    PredicateEventSink(
            SourceLocationSupport source,
            FullGrammerEventRecorder recorder,
            RowsetScopeSink rowsets,
            FullGrammerParseTreeAdapter parseTreeAdapter,
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
            predicateEvent(ctx, StructuredParseEventType.PREDICATE_EQUALITY,
                    pair.left(), pair.right(), joinKind);
        }
    }

    void predicateEquality(
            ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression, String joinKind
    ) {
        Optional<FullGrammerColumnReference> left =
                directColumns.singlePredicateColumn(leftExpression, rightExpression);
        Optional<FullGrammerColumnReference> right =
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
        FullGrammerColumnReference left =
                new FullGrammerColumnReference(source.clean(leftAlias), source.clean(leftColumn));
        FullGrammerColumnReference right =
                new FullGrammerColumnReference(source.clean(rightAlias), source.clean(rightColumn));
        if (!left.column().isBlank() && !right.column().isBlank()) {
            predicateEvent(ctx, StructuredParseEventType.PREDICATE_EQUALITY, left, right, joinKind);
        }
    }

    void existsPredicateEqualities(ParserRuleContext ctx, ParseTree predicate) {
        for (ColumnPair pair : equalityPairs(predicate)) {
            predicateEvent(ctx, StructuredParseEventType.EXISTS_PREDICATE,
                    pair.left(), pair.right(), "EXISTS");
        }
    }

    void existsPredicateEquality(ParserRuleContext ctx, ParseTree leftExpression, ParseTree rightExpression) {
        Optional<FullGrammerColumnReference> left =
                directColumns.singlePredicateColumn(leftExpression, rightExpression);
        Optional<FullGrammerColumnReference> right =
                directColumns.singlePredicateColumn(rightExpression, leftExpression);
        if (left.isPresent() && right.isPresent()) {
            predicateEvent(ctx, StructuredParseEventType.EXISTS_PREDICATE,
                    left.get(), right.get(), "EXISTS");
        }
    }

    void inSubqueryPredicate(ParserRuleContext ctx, ParseTree outerExpression, ParseTree subquery) {
        List<FullGrammerColumnReference> outerColumns = directColumns.directColumnList(outerExpression);
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
        if (!root && parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.QUERY_BOUNDARY)) {
            return;
        }
        int equalsIndex = directLeafIndex(tree, "=");
        if (equalsIndex > 0) {
            Optional<FullGrammerColumnReference> left = directColumns.singlePredicateColumnInChildren(
                    tree, 0, equalsIndex, equalsIndex + 1, tree.getChildCount());
            Optional<FullGrammerColumnReference> right = directColumns.singlePredicateColumnInChildren(
                    tree, equalsIndex + 1, tree.getChildCount(), 0, equalsIndex);
            if (left.isPresent() && right.isPresent()) {
                result.add(new ColumnPair(left.get(), right.get()));
                return;
            }
        }
        int nullSafeIndex = directTerminalSequenceStart(tree, List.of("IS", "NOT", "DISTINCT", "FROM"));
        if (nullSafeIndex > 0) {
            int rightStart = nullSafeIndex + 4;
            Optional<FullGrammerColumnReference> left = directColumns.singlePredicateColumnInChildren(
                    tree, 0, nullSafeIndex, rightStart, tree.getChildCount());
            Optional<FullGrammerColumnReference> right = directColumns.singlePredicateColumnInChildren(
                    tree, rightStart, tree.getChildCount(), 0, nullSafeIndex);
            if (left.isPresent() && right.isPresent()) {
                result.add(new ColumnPair(left.get(), right.get()));
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectEqualityPairs(tree.getChild(index), result, false);
        }
    }

    private void predicateEvent(
            ParserRuleContext ctx,
            StructuredParseEventType eventType,
            FullGrammerColumnReference left,
            FullGrammerColumnReference right,
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
            List<FullGrammerColumnReference> outerColumns,
            SubqueryProjectionTraceSupport.SelectColumns inner
    ) {
        List<FullGrammerColumnReference> innerColumns = inner.columns();
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

    private ExpressionSource expressionSource(FullGrammerColumnReference column) {
        return new ExpressionSource(column.qualifier(), column.column());
    }

    private int directLeafIndex(ParseTree tree, String text) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (tree.getChild(index) instanceof TerminalNode terminal && terminal.getText().equals(text)) {
                return index;
            }
        }
        return -1;
    }

    private int directTerminalSequenceStart(ParseTree tree, List<String> texts) {
        if (texts.isEmpty() || tree.getChildCount() < texts.size()) {
            return -1;
        }
        for (int index = 0; index <= tree.getChildCount() - texts.size(); index++) {
            boolean matches = true;
            for (int offset = 0; offset < texts.size(); offset++) {
                ParseTree child = tree.getChild(index + offset);
                if (!(child instanceof TerminalNode terminal)
                        || !terminal.getText().equalsIgnoreCase(texts.get(offset))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return index;
            }
        }
        return -1;
    }

    private boolean sameQualifier(String leftQualifier, String rightQualifier) {
        return !source.clean(leftQualifier).isBlank()
                && source.clean(leftQualifier).equalsIgnoreCase(source.clean(rightQualifier));
    }

    private record ColumnPair(FullGrammerColumnReference left, FullGrammerColumnReference right) {
    }
}
