package com.relationdetector.core.tokenevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** VALUE/CONTROL and transform analysis for common token-event expressions. */
abstract class CommonTokenEventExpressionSupport extends CommonTokenEventVisitorState {
    CommonTokenEventExpressionSupport(SqlStatementRecord statement) {
        super(statement);
    }

    protected String outputColumn(CommonRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    protected ColumnRead singleSelectColumn(CommonRelationSqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    protected List<ColumnRead> selectColumns(CommonRelationSqlParser.SelectStatementContext select) {
        List<CommonRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        List<ColumnRead> columns = new ArrayList<>();
        for (CommonRelationSqlParser.SelectItemContext item : items) {
            if (item.expression() == null) {
                return List.of();
            }
            ColumnRead column = singleColumn(item.expression());
            if (column == null) {
                return List.of();
            }
            columns.add(column);
        }
        return columns;
    }

    protected ColumnRead singleColumn(CommonRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof CommonRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> parts = parts(columnExpression.qualifiedName());
            if (parts.size() == 1) {
                return new ColumnRead("", parts.get(0));
            }
            return new ColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (expression instanceof CommonRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    protected ExpressionAnalysis analyze(CommonRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof CommonRelationSqlParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression);
            return column == null
                    ? ExpressionAnalysis.empty()
                    : ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof CommonRelationSqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression());
        }
        if (expression instanceof CommonRelationSqlParser.BinaryExpressionContext binary) {
            LineageTransformType transform = binary.arithmeticOperator().CONCAT() != null
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE,
                    analyze(binary.expression(0)), analyze(binary.expression(1)));
        }
        if (expression instanceof CommonRelationSqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (CommonRelationSqlParser.ExpressionContext argument
                        : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            LineageTransformType transform = switch (functionName) {
                case "sum", "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
                case "coalesce" -> LineageTransformType.COALESCE;
                case "concat", "format", "string_agg" -> LineageTransformType.CONCAT_FORMAT;
                default -> LineageTransformType.FUNCTION_CALL;
            };
            LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
            LineageFlowKind flowKind = dominant == LineageTransformType.CASE_WHEN
                    ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE;
            return new ExpressionAnalysis(args.sources(), dominant, flowKind);
        }
        if (expression instanceof CommonRelationSqlParser.CaseExpressionContext caseExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (CommonRelationSqlParser.CaseWhenClauseContext whenClause : caseExpression.caseWhenClause()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(whenClause.predicate()));
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(whenClause.expression()));
            }
            for (CommonRelationSqlParser.ExpressionContext part : caseExpression.expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(part));
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof CommonRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            visit(scalarSubquery.selectStatement());
            List<ColumnRead> columns = selectColumns(scalarSubquery.selectStatement());
            return columns.isEmpty()
                    ? ExpressionAnalysis.empty()
                    : new ExpressionAnalysis(columns, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        return ExpressionAnalysis.empty();
    }

    protected List<ExpressionAnalysis> writeAnalyses(CommonRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof CommonRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            return scalarSubqueryWriteAnalyses(scalarSubquery.selectStatement());
        }
        if (!(expression instanceof CommonRelationSqlParser.CaseExpressionContext caseExpression)) {
            ExpressionAnalysis analysis = analyze(expression);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        ExpressionAnalysis value = ExpressionAnalysis.empty();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        for (CommonRelationSqlParser.CaseWhenClauseContext clause : caseExpression.caseWhenClause()) {
            value = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(clause.expression()));
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control, analyze(clause.predicate()));
        }
        List<CommonRelationSqlParser.ExpressionContext> outerExpressions = caseExpression.expression();
        int selectorCount = outerExpressions.size() - (caseExpression.ELSE() == null ? 0 : 1);
        for (int index = 0; index < selectorCount; index++) {
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control, analyze(outerExpressions.get(index)));
        }
        if (caseExpression.ELSE() != null && !outerExpressions.isEmpty()) {
            value = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(outerExpressions.get(outerExpressions.size() - 1)));
        }
        return roleAnalyses(value, control);
    }

    private List<ExpressionAnalysis> scalarSubqueryWriteAnalyses(
            CommonRelationSqlParser.SelectStatementContext select
    ) {
        visit(select);
        CommonRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        List<CommonRelationSqlParser.SelectItemContext> items = query.selectList().selectItem();
        if (items.size() != 1 || items.get(0).expression() == null) {
            return List.of();
        }
        ExpressionAnalysis value = analyze(items.get(0).expression());
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        if (query.fromClause() != null) {
            for (CommonRelationSqlParser.TableReferenceContext table : query.fromClause().tableReference()) {
                for (CommonRelationSqlParser.JoinClauseContext join : table.joinClause()) {
                    if (join.predicate() != null) {
                        control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                                LineageFlowKind.CONTROL, control, analyze(join.predicate()));
                    }
                }
            }
        }
        if (query.whereClause() != null) {
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control, analyze(query.whereClause().predicate()));
        }
        if (query.groupByClause() != null) {
            for (CommonRelationSqlParser.ExpressionContext grouping
                    : query.groupByClause().expressionList().expression()) {
                control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(grouping));
            }
        }
        if (query.havingClause() != null) {
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control, analyze(query.havingClause().predicate()));
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            result.add(new ExpressionAnalysis(value.sources(), value.transform(), LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) {
            result.add(new ExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    protected ExpressionAnalysis analyze(CommonRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof CommonRelationSqlParser.AndPredicateContext value) {
            return controls(analyze(value.predicate(0)), analyze(value.predicate(1)));
        }
        if (predicate instanceof CommonRelationSqlParser.OrPredicateContext value) {
            return controls(analyze(value.predicate(0)), analyze(value.predicate(1)));
        }
        if (predicate instanceof CommonRelationSqlParser.NotPredicateContext value) {
            return analyze(value.predicate());
        }
        if (predicate instanceof CommonRelationSqlParser.ParenPredicateContext value) {
            return analyze(value.predicate());
        }
        if (predicate instanceof CommonRelationSqlParser.ComparisonPredicateContext value) {
            return controls(analyze(value.expression(0)), analyze(value.expression(1)));
        }
        if (predicate instanceof CommonRelationSqlParser.LikePredicateContext value) {
            ExpressionAnalysis combined = controls(analyze(value.expression(0)), analyze(value.expression(1)));
            return value.expression().size() > 2 ? controls(combined, analyze(value.expression(2))) : combined;
        }
        if (predicate instanceof CommonRelationSqlParser.LiteralInPredicateContext value) {
            ExpressionAnalysis combined = analyze(value.expression());
            for (CommonRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                combined = controls(combined, analyze(item));
            }
            return combined;
        }
        if (predicate instanceof CommonRelationSqlParser.InSubqueryPredicateContext value) {
            return analyze(value.expression());
        }
        if (predicate instanceof CommonRelationSqlParser.TupleInSubqueryPredicateContext value) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (CommonRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                combined = controls(combined, analyze(item));
            }
            return combined;
        }
        if (predicate instanceof CommonRelationSqlParser.ExpressionPredicateContext value) {
            return analyze(value.expression());
        }
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis controls(ExpressionAnalysis left, ExpressionAnalysis right) {
        return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL, left, right);
    }

    private List<ExpressionAnalysis> roleAnalyses(ExpressionAnalysis value, ExpressionAnalysis control) {
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            result.add(new ExpressionAnalysis(value.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) {
            result.add(new ExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }
}
