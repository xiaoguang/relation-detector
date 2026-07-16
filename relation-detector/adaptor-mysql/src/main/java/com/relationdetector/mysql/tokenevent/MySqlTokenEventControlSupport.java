package com.relationdetector.mysql.tokenevent;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 *
 * Scalar-subquery and conditional CONTROL analysis for MySQL token-event SQL.
 */
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
            ExpressionAnalysis conditional = conditionalControl(expression, defaultQualifier);
            if (!conditional.sources().isEmpty()) {
                result.add(conditional);
            }
            ExpressionAnalysis locator = scalarSubqueryControl(expression, defaultQualifier);
            if (!locator.sources().isEmpty()) {
                result.add(asLocatorControl(locator));
            }
            ExpressionAnalysis grouping = scalarSubqueryGroupingControl(expression, defaultQualifier);
            if (!grouping.sources().isEmpty()) {
                result.add(grouping);
            }
            ExpressionAnalysis window = windowControl(expression, defaultQualifier);
            if (!window.sources().isEmpty()) {
                result.add(window);
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
            result.add(asLocatorControl(control));
        }
        ExpressionAnalysis grouping = scalarSubqueryGroupingControl(expression, defaultQualifier);
        if (!grouping.sources().isEmpty()) {
            result.add(grouping);
        }
        ExpressionAnalysis window = windowControl(expression, defaultQualifier);
        if (!window.sources().isEmpty()) {
            result.add(window);
        }
        return List.copyOf(result);
    }

    private ExpressionAnalysis windowControl(ParseTree tree, String defaultQualifier) {
        if (tree instanceof MySqlRelationSqlParser.FunctionExpressionContext function
                && function.windowSpecification() != null) {
            MySqlRelationSqlParser.WindowSpecificationContext window = function.windowSpecification();
            ExpressionAnalysis control = ExpressionAnalysis.emptyControl();
            if (window.windowPartitionByClause() != null) {
                for (MySqlRelationSqlParser.ExpressionContext expression
                        : window.windowPartitionByClause().expressionList().expression()) {
                    control = controls(control, asWindowControl(analyze(expression, defaultQualifier)));
                }
            }
            if (window.windowOrderByClause() != null) {
                for (MySqlRelationSqlParser.OrderByItemContext item
                        : window.windowOrderByClause().orderByItem()) {
                    control = controls(control, asWindowControl(analyze(item.expression(), defaultQualifier)));
                }
            }
            return asWindowControl(control);
        }
        ExpressionAnalysis control = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < tree.getChildCount(); index++) {
            control = controls(control, windowControl(tree.getChild(index), defaultQualifier));
        }
        return asWindowControl(control);
    }

    private ExpressionAnalysis asWindowControl(ExpressionAnalysis analysis) {
        return new ExpressionAnalysis(
                analysis.sources(), LineageTransformType.WINDOW_DERIVED, LineageFlowKind.CONTROL);
    }

    private ExpressionAnalysis scalarSubqueryGroupingControl(ParseTree tree, String defaultQualifier) {
        if (tree instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext scalar) {
            MySqlRelationSqlParser.QuerySpecificationContext query =
                    scalar.selectStatement().querySpecification();
            if (query.groupByClause() == null) {
                return ExpressionAnalysis.emptyControl();
            }
            String qualifier = singleProjectionQualifier(query.fromClause());
            if (qualifier.isBlank()) {
                qualifier = defaultQualifier;
            }
            ExpressionAnalysis grouping = ExpressionAnalysis.emptyControl();
            for (MySqlRelationSqlParser.ExpressionContext expression
                    : query.groupByClause().expressionList().expression()) {
                grouping = controls(grouping, controlExpression(expression, qualifier));
            }
            return new ExpressionAnalysis(
                    grouping.sources(), LineageTransformType.AGGREGATE, LineageFlowKind.CONTROL);
        }
        ExpressionAnalysis grouping = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < tree.getChildCount(); index++) {
            grouping = controls(grouping,
                    scalarSubqueryGroupingControl(tree.getChild(index), defaultQualifier));
        }
        return new ExpressionAnalysis(
                grouping.sources(), LineageTransformType.AGGREGATE, LineageFlowKind.CONTROL);
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
            return scalarSubqueryContext(select, defaultQualifier);
        }
        ExpressionAnalysis context = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < expression.getChildCount(); index++) {
            context = controls(context, scalarSubqueryControl(expression.getChild(index), defaultQualifier));
        }
        return asLocatorControl(context);
    }

    private ExpressionAnalysis asLocatorControl(ExpressionAnalysis analysis) {
        return new ExpressionAnalysis(
                analysis.sources(), LineageTransformType.DIRECT, LineageFlowKind.CONTROL);
    }

    protected ExpressionAnalysis locatorControl(
            MySqlRelationSqlParser.PredicateContext predicate,
            String defaultQualifier
    ) {
        return asLocatorControl(analyze(predicate, defaultQualifier));
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
