package com.relationdetector.mysql.tokenevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 *
 * Direct expression and selected-value analysis for MySQL token-event SQL.
 */
abstract class MySqlTokenEventExpressionSupport extends MySqlTokenEventVisitorState {
    MySqlTokenEventExpressionSupport(SqlStatementRecord statement) {
        super(statement);
    }

    protected String outputColumn(MySqlRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    protected ExpressionAnalysis analyzeSelectItem(
            MySqlRelationSqlParser.SelectItemContext item,
            String defaultQualifier
    ) {
        if (item.expression() != null) {
            return analyze(item.expression(), defaultQualifier);
        }
        if (item.booleanSelectExpression() != null) {
            return analyze(item.booleanSelectExpression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    protected ColumnRead singleSelectColumn(MySqlRelationSqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    protected List<ColumnRead> selectColumns(MySqlRelationSqlParser.SelectStatementContext select) {
        List<MySqlRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        String defaultQualifier = singleProjectionQualifier(select.querySpecification().fromClause());
        List<ColumnRead> columns = new ArrayList<>();
        for (MySqlRelationSqlParser.SelectItemContext item : items) {
            if (item.expression() == null) {
                return List.of();
            }
            ColumnRead column = singleColumn(item.expression(), defaultQualifier);
            if (column == null) {
                return List.of();
            }
            columns.add(column);
        }
        return columns;
    }

    protected ColumnRead singleColumn(MySqlRelationSqlParser.ExpressionContext expression) {
        return singleColumn(expression, "");
    }

    protected ColumnRead singleColumn(
            MySqlRelationSqlParser.ExpressionContext expression,
            String defaultQualifier
    ) {
        if (expression instanceof MySqlRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> nameParts = parts(columnExpression.qualifiedName());
            if (nameParts.size() == 1) {
                if (nonColumnIdentifiers.contains(normalize(nameParts.get(0)))) {
                    return null;
                }
                return new ColumnRead(defaultQualifier, nameParts.get(0));
            }
            return new ColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
        }
        if (expression instanceof MySqlRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression(), defaultQualifier);
        }
        return null;
    }

    protected ExpressionAnalysis analyze(MySqlRelationSqlParser.ExpressionContext expression) {
        return analyze(expression, "");
    }

    protected ExpressionAnalysis analyze(
            MySqlRelationSqlParser.ExpressionContext expression,
            String defaultQualifier
    ) {
        if (expression instanceof MySqlRelationSqlParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression, defaultQualifier);
            return column == null ? ExpressionAnalysis.empty()
                    : ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof MySqlRelationSqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.BinaryExpressionContext binary) {
            LineageTransformType transform = binary.arithmeticOperator().CONCAT() != null
                    ? LineageTransformType.CONCAT_FORMAT : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE,
                    analyze(binary.expression(0), defaultQualifier),
                    analyze(binary.expression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.UnaryExpressionContext unary) {
            ExpressionAnalysis operand = analyze(unary.expression(), defaultQualifier);
            return new ExpressionAnalysis(operand.sources(), LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE);
        }
        if (expression instanceof MySqlRelationSqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (MySqlRelationSqlParser.ExpressionContext argument
                        : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args,
                            analyze(argument, defaultQualifier));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            LineageTransformType transform = LineageTransformClassifier.classifyFunction(
                    functionName, false, functionExtensions);
            LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
            LineageFlowKind flowKind = dominant == LineageTransformType.CASE_WHEN
                    ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE;
            return new ExpressionAnalysis(args.sources(), dominant, flowKind);
        }
        if (expression instanceof MySqlRelationSqlParser.CaseExpressionContext caseExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            if (caseExpression.selector != null) {
                combined = controls(combined, analyze(caseExpression.selector, defaultQualifier));
            }
            for (MySqlRelationSqlParser.CaseWhenClauseContext clause : caseExpression.caseWhenClause()) {
                combined = controls(combined, analyze(clause.predicate()));
                combined = controls(combined, analyze(clause.expression(), defaultQualifier));
            }
            if (caseExpression.elseBranch != null) {
                combined = controls(combined, analyze(caseExpression.elseBranch, defaultQualifier));
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof MySqlRelationSqlParser.IfExpressionContext conditional) {
            ExpressionAnalysis combined = controls(analyze(conditional.predicate()),
                    analyze(conditional.expression(0), defaultQualifier));
            combined = controls(combined, analyze(conditional.expression(1), defaultQualifier));
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof MySqlRelationSqlParser.IntervalExpressionContext interval) {
            return analyze(interval.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.CastExpressionContext cast) {
            ExpressionAnalysis operand = analyze(cast.expression(), defaultQualifier);
            return new ExpressionAnalysis(operand.sources(), LineageTransformClassifier.dominant(
                    LineageTransformType.FUNCTION_CALL, operand.transform()), LineageFlowKind.VALUE);
        }
        if (expression instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext scalar) {
            visit(scalar.selectStatement());
            ExpressionAnalysis selected = scalarSubquerySelectExpression(scalar.selectStatement());
            if (!selected.sources().isEmpty()) {
                return selected;
            }
            List<ColumnRead> columns = selectColumns(scalar.selectStatement());
            return columns.isEmpty() ? ExpressionAnalysis.empty()
                    : new ExpressionAnalysis(columns, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        return ExpressionAnalysis.empty();
    }

    protected ExpressionAnalysis scalarSubquerySelectExpression(MySqlRelationSqlParser.SelectStatementContext select) {
        List<MySqlRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        if (items.size() != 1 || items.get(0).expression() == null) {
            return ExpressionAnalysis.empty();
        }
        return selectedValueAnalysis(items.get(0).expression(),
                singleProjectionQualifier(select.querySpecification().fromClause()));
    }

    protected ExpressionAnalysis selectedValueAnalysis(
            MySqlRelationSqlParser.ExpressionContext expression,
            String defaultQualifier
    ) {
        if (expression instanceof MySqlRelationSqlParser.CaseExpressionContext conditional) {
            ExpressionAnalysis value = ExpressionAnalysis.empty();
            for (MySqlRelationSqlParser.CaseWhenClauseContext clause : conditional.caseWhenClause()) {
                value = conditionalValue(value, selectedValueAnalysis(clause.expression(), defaultQualifier));
            }
            if (conditional.elseBranch != null) {
                value = conditionalValue(value,
                        selectedValueAnalysis(conditional.elseBranch, defaultQualifier));
            }
            return value;
        }
        if (expression instanceof MySqlRelationSqlParser.IfExpressionContext conditional) {
            return conditionalValue(
                    selectedValueAnalysis(conditional.expression(0), defaultQualifier),
                    selectedValueAnalysis(conditional.expression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (MySqlRelationSqlParser.ExpressionContext argument
                        : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), LineageFlowKind.VALUE,
                            args, selectedValueAnalysis(argument, defaultQualifier));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            LineageTransformType transform = LineageTransformClassifier.classifyFunction(
                    functionName, false, functionExtensions);
            LineageTransformType effective = transform == LineageTransformType.AGGREGATE
                    ? transform : LineageTransformClassifier.dominant(transform, args.transform());
            return new ExpressionAnalysis(args.sources(), effective, LineageFlowKind.VALUE);
        }
        if (expression instanceof MySqlRelationSqlParser.BinaryExpressionContext binary) {
            LineageTransformType transform = binary.arithmeticOperator().CONCAT() != null
                    ? LineageTransformType.CONCAT_FORMAT : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE,
                    selectedValueAnalysis(binary.expression(0), defaultQualifier),
                    selectedValueAnalysis(binary.expression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.UnaryExpressionContext unary) {
            ExpressionAnalysis operand = selectedValueAnalysis(unary.expression(), defaultQualifier);
            return new ExpressionAnalysis(operand.sources(), LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE);
        }
        if (expression instanceof MySqlRelationSqlParser.ParenExpressionContext paren) {
            return selectedValueAnalysis(paren.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.IntervalExpressionContext interval) {
            return selectedValueAnalysis(interval.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.CastExpressionContext cast) {
            ExpressionAnalysis operand = selectedValueAnalysis(cast.expression(), defaultQualifier);
            return new ExpressionAnalysis(operand.sources(), LineageTransformClassifier.dominant(
                    LineageTransformType.FUNCTION_CALL, operand.transform()), LineageFlowKind.VALUE);
        }
        return asValue(analyze(expression, defaultQualifier));
    }

    protected ExpressionAnalysis conditionalValue(ExpressionAnalysis left, ExpressionAnalysis right) {
        List<ColumnRead> sources = new ArrayList<>();
        sources.addAll(left.sources());
        sources.addAll(right.sources());
        return new ExpressionAnalysis(sources.stream().distinct().toList(),
                LineageTransformType.CASE_WHEN, LineageFlowKind.VALUE);
    }

    protected ExpressionAnalysis asValue(ExpressionAnalysis analysis) {
        return analysis.sources().isEmpty() ? analysis
                : new ExpressionAnalysis(analysis.sources(), analysis.transform(), LineageFlowKind.VALUE);
    }

    protected ExpressionAnalysis asControl(ExpressionAnalysis analysis) {
        return analysis.sources().isEmpty() ? analysis
                : new ExpressionAnalysis(analysis.sources(), LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL);
    }

    private ExpressionAnalysis controls(ExpressionAnalysis left, ExpressionAnalysis right) {
        return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL, left, right);
    }

    protected abstract ExpressionAnalysis analyze(
            MySqlRelationSqlParser.PredicateContext predicate,
            String defaultQualifier);

    protected abstract ExpressionAnalysis analyze(MySqlRelationSqlParser.PredicateContext predicate);

    protected abstract ExpressionAnalysis analyze(
            MySqlRelationSqlParser.BooleanSelectExpressionContext expression,
            String defaultQualifier);
}
