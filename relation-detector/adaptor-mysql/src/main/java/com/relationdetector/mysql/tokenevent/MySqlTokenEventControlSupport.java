package com.relationdetector.mysql.tokenevent;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** Scalar-subquery and conditional CONTROL analysis for MySQL token-event SQL. */
abstract class MySqlTokenEventControlSupport extends MySqlTokenEventExpressionSupport {
    MySqlTokenEventControlSupport(SqlStatementRecord statement) {
        super(statement);
    }

    protected List<ExpressionAnalysis> writeAnalyses(
            MySqlRelationSqlParser.SelectItemContext item,
            String defaultQualifier
    ) {
        if (item.expression() != null) {
            return writeAnalyses(item.expression(), defaultQualifier);
        }
        ExpressionAnalysis analysis = analyzeSelectItem(item, defaultQualifier);
        if (analysis.sources().isEmpty()) {
            return List.of();
        }
        return List.of(new ExpressionAnalysis(analysis.sources(),
                projectionTransform(item.booleanSelectExpression(), defaultQualifier), LineageFlowKind.VALUE));
    }

    protected List<ExpressionAnalysis> writeAnalyses(
            MySqlRelationSqlParser.ExpressionContext expression,
            String defaultQualifier
    ) {
        List<ExpressionAnalysis> result = new ArrayList<>();
        if (containsConditionalExpression(expression)) {
            ExpressionAnalysis value = selectedValueAnalysis(expression, defaultQualifier);
            if (!value.sources().isEmpty()) {
                result.add(value);
            }
            ExpressionAnalysis control = controls(
                    conditionalControl(expression, defaultQualifier),
                    scalarSubqueryControl(expression, defaultQualifier));
            if (!control.sources().isEmpty()) {
                result.add(control);
            }
            return List.copyOf(result);
        }
        ExpressionAnalysis value = analyze(expression, defaultQualifier);
        if (containsScalarSubquery(expression)) {
            value = asValue(value);
        }
        if (!value.sources().isEmpty()) {
            result.add(value);
        }
        ExpressionAnalysis control = scalarSubqueryControl(expression, defaultQualifier);
        if (!control.sources().isEmpty()) {
            result.add(control);
        }
        return List.copyOf(result);
    }

    private ExpressionAnalysis controlExpression(ParseTree tree, String defaultQualifier) {
        if (tree instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext scalar) {
            return scalarSubqueryPredicateOperandControl(scalar.selectStatement(), defaultQualifier);
        }
        if (tree instanceof MySqlRelationSqlParser.ExpressionContext expression
                && !containsScalarSubquery(expression)) {
            return asControl(analyze(expression, defaultQualifier));
        }
        ExpressionAnalysis context = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < tree.getChildCount(); index++) {
            context = controls(context, controlExpression(tree.getChild(index), defaultQualifier));
        }
        return context;
    }

    private ExpressionAnalysis scalarSubqueryControl(ParseTree expression, String defaultQualifier) {
        if (expression instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext scalar) {
            MySqlRelationSqlParser.SelectStatementContext select = scalar.selectStatement();
            ExpressionAnalysis context = scalarSubqueryContext(select, defaultQualifier);
            List<MySqlRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
            if (items.size() == 1 && items.get(0).expression() != null) {
                context = controls(context, conditionalControl(items.get(0).expression(),
                        singleProjectionQualifier(select.querySpecification().fromClause())));
            }
            return context;
        }
        ExpressionAnalysis context = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < expression.getChildCount(); index++) {
            context = controls(context, scalarSubqueryControl(expression.getChild(index), defaultQualifier));
        }
        return context;
    }

    private ExpressionAnalysis conditionalControl(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return ExpressionAnalysis.emptyControl();
        }
        if (tree instanceof MySqlRelationSqlParser.CaseExpressionContext conditional) {
            ExpressionAnalysis control = ExpressionAnalysis.emptyControl();
            if (conditional.selector != null) {
                control = controls(control, asControl(analyze(conditional.selector, defaultQualifier)));
            }
            for (MySqlRelationSqlParser.CaseWhenClauseContext clause : conditional.caseWhenClause()) {
                control = controls(control, analyze(clause.predicate(), defaultQualifier));
                control = controls(control, conditionalControl(clause.expression(), defaultQualifier));
            }
            if (conditional.elseBranch != null) {
                control = controls(control, conditionalControl(conditional.elseBranch, defaultQualifier));
            }
            return control;
        }
        if (tree instanceof MySqlRelationSqlParser.IfExpressionContext conditional) {
            return controls(analyze(conditional.predicate(), defaultQualifier),
                    controls(conditionalControl(conditional.expression(0), defaultQualifier),
                            conditionalControl(conditional.expression(1), defaultQualifier)));
        }
        ExpressionAnalysis control = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < tree.getChildCount(); index++) {
            control = controls(control, conditionalControl(tree.getChild(index), defaultQualifier));
        }
        return control;
    }

    private boolean containsConditionalExpression(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof MySqlRelationSqlParser.CaseExpressionContext
                || tree instanceof MySqlRelationSqlParser.IfExpressionContext) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsConditionalExpression(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private LineageTransformType projectionTransform(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return LineageTransformType.DIRECT;
        }
        if (tree instanceof MySqlRelationSqlParser.ExpressionContext expression) {
            LineageTransformType transform = analyze(expression, defaultQualifier).transform();
            if (transform != LineageTransformType.CASE_WHEN) {
                return transform;
            }
        }
        LineageTransformType transform = LineageTransformType.DIRECT;
        for (int index = 0; index < tree.getChildCount(); index++) {
            transform = LineageTransformClassifier.dominant(
                    transform, projectionTransform(tree.getChild(index), defaultQualifier));
        }
        return transform;
    }

    private ExpressionAnalysis scalarSubqueryPredicateOperandControl(
            MySqlRelationSqlParser.SelectStatementContext select,
            String defaultQualifier
    ) {
        return controls(asControl(scalarSubquerySelectExpression(select)),
                scalarSubqueryContext(select, defaultQualifier));
    }

    private boolean containsScalarSubquery(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsScalarSubquery(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private ExpressionAnalysis scalarSubqueryContext(
            MySqlRelationSqlParser.SelectStatementContext select,
            String defaultQualifier
    ) {
        MySqlRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        String scalarQualifier = singleProjectionQualifier(query.fromClause());
        if (scalarQualifier.isBlank()) {
            scalarQualifier = defaultQualifier;
        }
        ExpressionAnalysis context = ExpressionAnalysis.empty();
        if (query.fromClause() != null) {
            for (MySqlRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
                for (MySqlRelationSqlParser.JoinClauseContext join : reference.joinClause()) {
                    if (join.predicate() != null) {
                        context = controls(context, analyze(join.predicate(), scalarQualifier));
                    }
                }
            }
        }
        if (query.whereClause() != null) {
            context = controls(context, analyze(query.whereClause().predicate(), scalarQualifier));
        }
        if (query.groupByClause() != null) {
            for (MySqlRelationSqlParser.ExpressionContext grouping
                    : query.groupByClause().expressionList().expression()) {
                context = controls(context, controlExpression(grouping, scalarQualifier));
            }
        }
        if (query.havingClause() != null) {
            context = controls(context, analyze(query.havingClause().predicate(), scalarQualifier));
        }
        return context;
    }

    @Override
    protected ExpressionAnalysis analyze(
            MySqlRelationSqlParser.PredicateContext predicate,
            String defaultQualifier
    ) {
        if (predicate instanceof MySqlRelationSqlParser.AndPredicateContext value) {
            return controls(analyze(value.predicate(0), defaultQualifier),
                    analyze(value.predicate(1), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.OrPredicateContext value) {
            return controls(analyze(value.predicate(0), defaultQualifier),
                    analyze(value.predicate(1), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.NotPredicateContext value) {
            return analyze(value.predicate(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.ParenPredicateContext value) {
            return analyze(value.predicate(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.ComparisonPredicateContext value) {
            return controls(controlExpression(value.expression(0), defaultQualifier),
                    controlExpression(value.expression(1), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.LikePredicateContext value) {
            ExpressionAnalysis result = controls(controlExpression(value.expression(0), defaultQualifier),
                    controlExpression(value.expression(1), defaultQualifier));
            return value.expression().size() > 2
                    ? controls(result, controlExpression(value.expression(2), defaultQualifier)) : result;
        }
        if (predicate instanceof MySqlRelationSqlParser.BetweenPredicateContext value) {
            return controls(controls(controlExpression(value.expression(0), defaultQualifier),
                    controlExpression(value.expression(1), defaultQualifier)),
                    controlExpression(value.expression(2), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.LiteralInPredicateContext value) {
            ExpressionAnalysis result = controlExpression(value.expression(), defaultQualifier);
            for (MySqlRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                result = controls(result, controlExpression(item, defaultQualifier));
            }
            return result;
        }
        if (predicate instanceof MySqlRelationSqlParser.IsNullPredicateContext value) {
            return controlExpression(value.expression(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.InSubqueryPredicateContext value) {
            return controlExpression(value.expression(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.TupleInSubqueryPredicateContext value) {
            ExpressionAnalysis result = ExpressionAnalysis.empty();
            for (MySqlRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                result = controls(result, controlExpression(item, defaultQualifier));
            }
            return result;
        }
        if (predicate instanceof MySqlRelationSqlParser.ExpressionPredicateContext value) {
            return controlExpression(value.expression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    @Override
    protected ExpressionAnalysis analyze(MySqlRelationSqlParser.PredicateContext predicate) {
        return analyze(predicate, "");
    }

    @Override
    protected ExpressionAnalysis analyze(
            MySqlRelationSqlParser.BooleanSelectExpressionContext expression,
            String defaultQualifier
    ) {
        if (expression instanceof MySqlRelationSqlParser.SelectAndBooleanContext value) {
            return controls(analyze(value.booleanSelectExpression(0), defaultQualifier),
                    analyze(value.booleanSelectExpression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectOrBooleanContext value) {
            return controls(analyze(value.booleanSelectExpression(0), defaultQualifier),
                    analyze(value.booleanSelectExpression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectNotBooleanContext value) {
            return analyze(value.booleanSelectExpression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.SelectComparisonBooleanContext value) {
            return controls(controlExpression(value.expression(0), defaultQualifier),
                    controlExpression(value.expression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectLikeBooleanContext value) {
            ExpressionAnalysis result = controls(controlExpression(value.expression(0), defaultQualifier),
                    controlExpression(value.expression(1), defaultQualifier));
            return value.expression().size() > 2
                    ? controls(result, controlExpression(value.expression(2), defaultQualifier)) : result;
        }
        if (expression instanceof MySqlRelationSqlParser.SelectBetweenBooleanContext value) {
            return controls(controls(controlExpression(value.expression(0), defaultQualifier),
                    controlExpression(value.expression(1), defaultQualifier)),
                    controlExpression(value.expression(2), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectIsNullBooleanContext value) {
            return controlExpression(value.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.SelectParenBooleanContext value) {
            return analyze(value.booleanSelectExpression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis controls(ExpressionAnalysis left, ExpressionAnalysis right) {
        return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL, left, right);
    }
}
