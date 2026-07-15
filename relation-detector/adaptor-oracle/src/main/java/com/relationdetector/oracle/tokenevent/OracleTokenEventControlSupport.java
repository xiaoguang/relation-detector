package com.relationdetector.oracle.tokenevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** VALUE/CONTROL role splitting and scalar-subquery context for Oracle token-event SQL. */
abstract class OracleTokenEventControlSupport extends OracleTokenEventExpressionSupport {
    OracleTokenEventControlSupport(SqlStatementRecord statement) {
        super(statement);
    }

    protected List<OracleExpressionAnalysis> writeAnalyses(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.FunctionExpressionContext function
                && function.windowClause() != null) {
            OracleExpressionAnalysis value = valueOnlyAnalysis(expression);
            OracleExpressionAnalysis control = windowControl(function.windowClause());
            List<OracleExpressionAnalysis> result = new ArrayList<>(2);
            if (!value.sources().isEmpty()) {
                result.add(value);
            }
            if (!control.sources().isEmpty()) {
                result.add(control);
            }
            return List.copyOf(result);
        }
        if (!containsRoleBoundary(expression)) {
            OracleExpressionAnalysis analysis = analyze(expression);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        OracleExpressionAnalysis value = valueOnlyAnalysis(expression);
        OracleExpressionAnalysis control = controlOnlyAnalysis(expression);
        List<OracleExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            result.add(new OracleExpressionAnalysis(value.sources(), value.transform(), LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) {
            result.add(new OracleExpressionAnalysis(control.sources(), control.transform(),
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    private OracleExpressionAnalysis windowControl(OracleRelationSqlParser.WindowClauseContext window) {
        OracleExpressionAnalysis control = OracleExpressionAnalysis.empty();
        if (window.windowPartitionClause() != null) {
            for (OracleRelationSqlParser.ExpressionContext expression
                    : window.windowPartitionClause().expressionList().expression()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.WINDOW_DERIVED,
                        LineageFlowKind.CONTROL, control, analyze(expression));
            }
        }
        if (window.orderByClause() != null) {
            for (OracleRelationSqlParser.OrderByItemContext item : window.orderByClause().orderByItem()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.WINDOW_DERIVED,
                        LineageFlowKind.CONTROL, control, analyze(item.expression()));
            }
        }
        return new OracleExpressionAnalysis(control.sources(), LineageTransformType.WINDOW_DERIVED,
                LineageFlowKind.CONTROL);
    }

    protected OracleExpressionAnalysis valueOnlyAnalysis(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            visit(scalarSubquery.selectStatement());
            return scalarSubquerySelectValue(scalarSubquery.selectStatement());
        }
        if (expression instanceof OracleRelationSqlParser.CaseExpressionContext caseExpression) {
            OracleExpressionAnalysis value = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.CaseWhenClauseContext clause : caseExpression.caseWhenClause()) {
                value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.VALUE, value, valueOnlyAnalysis(clause.expression()));
            }
            List<OracleRelationSqlParser.ExpressionContext> outer = caseExpression.expression();
            if (caseExpression.ELSE() != null && !outer.isEmpty()) {
                value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.VALUE, value, valueOnlyAnalysis(outer.get(outer.size() - 1)));
            }
            return new OracleExpressionAnalysis(value.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleRelationSqlParser.ParenExpressionContext paren) {
            return valueOnlyAnalysis(paren.expression());
        }
        if (expression instanceof OracleRelationSqlParser.BinaryExpressionContext binary) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.combine(
                    LineageTransformType.ARITHMETIC, LineageFlowKind.VALUE,
                    valueOnlyAnalysis(binary.expression(0)), valueOnlyAnalysis(binary.expression(1)));
            return new OracleExpressionAnalysis(combined.sources(), combined.transform(), LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleRelationSqlParser.FunctionExpressionContext function) {
            OracleExpressionAnalysis args = OracleExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (OracleRelationSqlParser.ExpressionContext argument
                        : function.functionCall().expressionList().expression()) {
                    args = OracleExpressionAnalysis.combine(args.transform(), LineageFlowKind.VALUE,
                            args, valueOnlyAnalysis(argument));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            LineageTransformType transform = LineageTransformClassifier.classifyFunction(
                    functionName, false, functionExtensions);
            LineageTransformType effective = transform == LineageTransformType.AGGREGATE
                    || args.transform() == LineageTransformType.AGGREGATE
                    ? LineageTransformType.AGGREGATE
                    : LineageTransformClassifier.dominant(transform, args.transform());
            return new OracleExpressionAnalysis(args.sources(), effective, LineageFlowKind.VALUE);
        }
        OracleExpressionAnalysis analysis = analyze(expression);
        return new OracleExpressionAnalysis(analysis.sources(), analysis.transform(), LineageFlowKind.VALUE);
    }

    protected OracleExpressionAnalysis controlOnlyAnalysis(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            OracleExpressionAnalysis control = scalarSubqueryContext(scalarSubquery.selectStatement());
            List<OracleRelationSqlParser.SelectItemContext> items =
                    scalarSubquery.selectStatement().querySpecification().selectList().selectItem();
            if (items.size() == 1 && items.get(0).expression() != null) {
                OracleExpressionAnalysis projectionControl = controlOnlyAnalysis(items.get(0).expression());
                if (!projectionControl.sources().isEmpty()) {
                    control = OracleExpressionAnalysis.combine(
                            LineageTransformClassifier.dominant(
                                    control.transform(), projectionControl.transform()),
                            LineageFlowKind.CONTROL, control, projectionControl);
                }
            }
            return new OracleExpressionAnalysis(control.sources(), control.transform(),
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof OracleRelationSqlParser.CaseExpressionContext caseExpression) {
            OracleExpressionAnalysis control = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.CaseWhenClauseContext clause : caseExpression.caseWhenClause()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(clause.predicate()));
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, controlOnlyAnalysis(clause.expression()));
            }
            List<OracleRelationSqlParser.ExpressionContext> outer = caseExpression.expression();
            int selectorCount = outer.size() - (caseExpression.ELSE() == null ? 0 : 1);
            for (int index = 0; index < selectorCount; index++) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(outer.get(index)));
            }
            if (caseExpression.ELSE() != null && !outer.isEmpty()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, controlOnlyAnalysis(outer.get(outer.size() - 1)));
            }
            return control;
        }
        OracleExpressionAnalysis control = OracleExpressionAnalysis.empty();
        for (OracleRelationSqlParser.ExpressionContext child : childExpressions(expression)) {
            OracleExpressionAnalysis childControl = controlOnlyAnalysis(child);
            control = OracleExpressionAnalysis.combine(
                    LineageTransformClassifier.dominant(control.transform(), childControl.transform()),
                    LineageFlowKind.CONTROL, control, childControl);
        }
        return control;
    }

    private List<OracleRelationSqlParser.ExpressionContext> childExpressions(ParseTree tree) {
        List<OracleRelationSqlParser.ExpressionContext> result = new ArrayList<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (tree.getChild(index) instanceof OracleRelationSqlParser.ExpressionContext expression) {
                result.add(expression);
            } else if (!(tree.getChild(index) instanceof TerminalNode)) {
                result.addAll(childExpressions(tree.getChild(index)));
            }
        }
        return result;
    }

    private boolean containsRoleBoundary(ParseTree tree) {
        if (tree instanceof OracleRelationSqlParser.CaseExpressionContext
                || tree instanceof OracleRelationSqlParser.ScalarSubqueryExpressionContext) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsRoleBoundary(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private OracleExpressionAnalysis scalarSubquerySelectValue(
            OracleRelationSqlParser.SelectStatementContext select) {
        queryScopes.push(scopeFor(select.querySpecification()));
        try {
            List<OracleRelationSqlParser.SelectItemContext> items =
                    select.querySpecification().selectList().selectItem();
            return items.size() != 1 || items.get(0).expression() == null
                    ? OracleExpressionAnalysis.empty() : valueOnlyAnalysis(items.get(0).expression());
        } finally {
            queryScopes.pop();
        }
    }

    @Override
    protected OracleExpressionAnalysis scalarSubquerySelectExpression(
            OracleRelationSqlParser.SelectStatementContext select) {
        queryScopes.push(scopeFor(select.querySpecification()));
        try {
            List<OracleRelationSqlParser.SelectItemContext> items =
                    select.querySpecification().selectList().selectItem();
            return items.size() != 1 || items.get(0).expression() == null
                    ? OracleExpressionAnalysis.empty() : analyze(items.get(0).expression());
        } finally {
            queryScopes.pop();
        }
    }

    @Override
    protected OracleExpressionAnalysis scalarSubqueryWithContext(
            OracleRelationSqlParser.SelectStatementContext select,
            OracleExpressionAnalysis selectedExpression) {
        if (!containsAggregateFunction(select.querySpecification().selectList())) {
            return selectedExpression;
        }
        OracleExpressionAnalysis context = scalarSubqueryContext(select);
        if (context.sources().isEmpty()) {
            return selectedExpression;
        }
        OracleExpressionAnalysis combined = OracleExpressionAnalysis.combine(selectedExpression.transform(),
                selectedExpression.flowKind(), selectedExpression, context);
        return new OracleExpressionAnalysis(combined.sources(), selectedExpression.transform(),
                selectedExpression.flowKind());
    }

    protected boolean containsAggregateFunction(ParseTree tree) {
        if (tree instanceof OracleRelationSqlParser.FunctionExpressionContext function) {
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            if (LineageTransformClassifier.classifyFunction(functionName, false, functionExtensions)
                    == LineageTransformType.AGGREGATE) {
                return true;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsAggregateFunction(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    protected OracleExpressionAnalysis groupingControl(
            OracleRelationSqlParser.SelectStatementContext select) {
        OracleRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        if (query == null || query.groupByClause() == null) {
            return OracleExpressionAnalysis.empty();
        }
        queryScopes.push(scopeFor(query));
        try {
            OracleExpressionAnalysis grouping = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.ExpressionContext expression
                    : query.groupByClause().expressionList().expression()) {
                grouping = OracleExpressionAnalysis.combine(LineageTransformType.AGGREGATE,
                        LineageFlowKind.CONTROL, grouping, analyze(expression));
            }
            return new OracleExpressionAnalysis(grouping.sources(), LineageTransformType.AGGREGATE,
                    LineageFlowKind.CONTROL);
        } finally {
            queryScopes.pop();
        }
    }

    private OracleExpressionAnalysis scalarSubqueryContext(OracleRelationSqlParser.SelectStatementContext select) {
        OracleRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(scopeFor(query));
        try {
            OracleExpressionAnalysis context = OracleExpressionAnalysis.empty();
            if (query.fromClause() != null) {
                for (OracleRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
                    for (OracleRelationSqlParser.JoinClauseContext join : reference.joinClause()) {
                        if (join.predicate() != null) {
                            context = OracleExpressionAnalysis.combine(LineageTransformType.DIRECT,
                                    LineageFlowKind.CONTROL, context, analyze(join.predicate()));
                        }
                    }
                }
            }
            if (query.whereClause() != null) {
                context = OracleExpressionAnalysis.combine(LineageTransformType.DIRECT,
                        LineageFlowKind.CONTROL, context, analyze(query.whereClause().predicate()));
            }
            if (query.groupByClause() != null) {
                for (OracleRelationSqlParser.ExpressionContext expression
                        : query.groupByClause().expressionList().expression()) {
                    context = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL, context, analyze(expression));
                }
            }
            if (query.havingClause() != null) {
                context = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, context, analyze(query.havingClause().predicate()));
            }
            return new OracleExpressionAnalysis(context.sources(), LineageTransformType.DIRECT,
                    LineageFlowKind.CONTROL);
        } finally {
            queryScopes.pop();
        }
    }

    @Override
    protected OracleExpressionAnalysis analyze(OracleRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof OracleRelationSqlParser.AndPredicateContext value) {
            return OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    analyze(value.predicate(0)), analyze(value.predicate(1)));
        }
        if (predicate instanceof OracleRelationSqlParser.OrPredicateContext value) {
            return OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    analyze(value.predicate(0)), analyze(value.predicate(1)));
        }
        if (predicate instanceof OracleRelationSqlParser.NotPredicateContext value) return analyze(value.predicate());
        if (predicate instanceof OracleRelationSqlParser.ParenPredicateContext value) return analyze(value.predicate());
        if (predicate instanceof OracleRelationSqlParser.ComparisonPredicateContext value) {
            return OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    analyze(value.expression(0)), analyze(value.expression(1)));
        }
        if (predicate instanceof OracleRelationSqlParser.LikePredicateContext value) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, analyze(value.expression(0)), analyze(value.expression(1)));
            return value.expression().size() > 2
                    ? OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL, combined, analyze(value.expression(2))) : combined;
        }
        if (predicate instanceof OracleRelationSqlParser.IsNullPredicateContext value) return analyze(value.expression());
        if (predicate instanceof OracleRelationSqlParser.LiteralInPredicateContext value) {
            OracleExpressionAnalysis combined = analyze(value.expression());
            for (OracleRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(item));
            }
            return combined;
        }
        if (predicate instanceof OracleRelationSqlParser.InSubqueryPredicateContext value) {
            return analyze(value.expression());
        }
        if (predicate instanceof OracleRelationSqlParser.TupleInSubqueryPredicateContext value) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(item));
            }
            return combined;
        }
        if (predicate instanceof OracleRelationSqlParser.ExpressionPredicateContext value) {
            return analyze(value.expression());
        }
        return OracleExpressionAnalysis.empty();
    }
}
