package com.relationdetector.postgres.routine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** CASE, scalar-subquery and predicate role analysis for PostgreSQL routine bodies. */
abstract class PostgresRoutineControlSupport extends PostgresRoutineExpressionSupport {
    PostgresRoutineControlSupport(SqlStatementRecord statement) { super(statement); }

    @Override
    protected List<ExpressionAnalysis> binaryWriteAnalyses(
            PostgresRoutineBodySqlParser.BinaryExpressionContext expression) {
        LineageTransformType transform = "||".equals(expression.arithmeticOperator().getText())
                ? LineageTransformType.CONCAT_FORMAT : LineageTransformType.ARITHMETIC;
        ExpressionAnalysis value = ExpressionAnalysis.empty();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        for (PostgresRoutineBodySqlParser.ExpressionContext operand : expression.expression()) {
            for (ExpressionAnalysis analysis : writeAnalyses(operand)) {
                if (analysis.flowKind() == LineageFlowKind.CONTROL) {
                    control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL, control, analysis);
                } else {
                    value = ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE, value, analysis);
                }
            }
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            LineageTransformType effective = value.transform() == LineageTransformType.AGGREGATE
                    || value.transform() == LineageTransformType.CUMULATIVE ? value.transform() : transform;
            result.add(new ExpressionAnalysis(value.sources(), effective, LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) result.add(new ExpressionAnalysis(
                control.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        return List.copyOf(result);
    }

    @Override
    protected List<ExpressionAnalysis> functionWriteAnalyses(
            PostgresRoutineBodySqlParser.FunctionExpressionContext expression) {
        PostgresRoutineBodySqlParser.FunctionCallContext function = expression.functionCall();
        String name = baseName(qualifiedName(function.qualifiedName())).toLowerCase(Locale.ROOT);
        LineageTransformType outer = switch (name) {
            case "sum" -> expression.windowClause() == null
                    ? LineageTransformType.AGGREGATE : LineageTransformType.CUMULATIVE;
            case "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
            case "coalesce" -> LineageTransformType.COALESCE;
            case "concat", "format", "string_agg" -> LineageTransformType.CONCAT_FORMAT;
            default -> LineageTransformType.FUNCTION_CALL;
        };
        ExpressionAnalysis value = ExpressionAnalysis.empty();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        if (function.expressionList() != null) {
            for (PostgresRoutineBodySqlParser.ExpressionContext argument
                    : function.expressionList().expression()) {
                for (ExpressionAnalysis analysis : writeAnalyses(argument)) {
                    if (analysis.flowKind() == LineageFlowKind.CONTROL) {
                        control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                                LineageFlowKind.CONTROL, control, analysis);
                    } else {
                        value = ExpressionAnalysis.combine(outer, LineageFlowKind.VALUE, value, analysis);
                    }
                }
            }
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            LineageTransformType effective = outer == LineageTransformType.AGGREGATE
                    || outer == LineageTransformType.CUMULATIVE
                    ? outer : LineageTransformClassifier.dominant(outer, value.transform());
            result.add(new ExpressionAnalysis(value.sources(), effective, LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) result.add(new ExpressionAnalysis(
                control.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        return List.copyOf(result);
    }

    @Override
    protected List<ExpressionAnalysis> scalarSubqueryWriteAnalyses(
            PostgresRoutineBodySqlParser.SelectStatementContext select) {
        if (select.withClause() != null) visit(select.withClause());
        PostgresRoutineBodySqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(new QueryScope());
        try {
            if (query.fromClause() != null) visit(query.fromClause());
            if (query.whereClause() != null) visit(query.whereClause());
            if (query.havingClause() != null) visit(query.havingClause());
            ExpressionAnalysis value = ExpressionAnalysis.empty();
            ExpressionAnalysis control = scalarSubqueryContext(select);
            List<PostgresRoutineBodySqlParser.SelectItemContext> items = query.selectList().selectItem();
            if (items.size() == 1) {
                for (ExpressionAnalysis analysis : selectItemAnalyses(items.get(0))) {
                    if (analysis.flowKind() == LineageFlowKind.CONTROL) {
                        control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                                LineageFlowKind.CONTROL, control, analysis);
                    } else {
                        value = ExpressionAnalysis.combine(analysis.transform(),
                                LineageFlowKind.VALUE, value, analysis);
                    }
                }
            }
            List<ExpressionAnalysis> result = new ArrayList<>(2);
            if (!value.sources().isEmpty()) result.add(new ExpressionAnalysis(
                    value.sources(), value.transform(), LineageFlowKind.VALUE));
            if (!control.sources().isEmpty()) result.add(new ExpressionAnalysis(
                    control.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
            return List.copyOf(result);
        } finally {
            queryScopes.pop();
        }
    }

    private ExpressionAnalysis scalarSubqueryContext(PostgresRoutineBodySqlParser.SelectStatementContext select) {
        PostgresRoutineBodySqlParser.QuerySpecificationContext query = select.querySpecification();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        if (query.fromClause() != null) {
            for (PostgresRoutineBodySqlParser.TableReferenceContext table : query.fromClause().tableReference()) {
                for (PostgresRoutineBodySqlParser.JoinClauseContext join : table.joinClause()) {
                    if (join.predicate() != null) control = ExpressionAnalysis.combine(
                            LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                            control, analyze(join.predicate()));
                }
            }
        }
        if (query.whereClause() != null) control = ExpressionAnalysis.combine(
                LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                control, analyze(query.whereClause().predicate()));
        if (query.groupByClause() != null) {
            for (PostgresRoutineBodySqlParser.ExpressionContext grouping
                    : query.groupByClause().expressionList().expression()) {
                control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(grouping));
            }
        }
        if (query.havingClause() != null) control = ExpressionAnalysis.combine(
                LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                control, analyze(query.havingClause().predicate()));
        return control;
    }

    @Override
    protected ExpressionAnalysis analyzeScalarSubquery(PostgresRoutineBodySqlParser.SelectStatementContext select) {
        if (select.withClause() != null) visit(select.withClause());
        PostgresRoutineBodySqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(new QueryScope());
        try {
            if (query.fromClause() != null) visit(query.fromClause());
            if (query.whereClause() != null) visit(query.whereClause());
            if (query.havingClause() != null) visit(query.havingClause());
            List<PostgresRoutineBodySqlParser.SelectItemContext> items = query.selectList().selectItem();
            return items.size() != 1 || items.get(0).expression() == null
                    ? ExpressionAnalysis.empty() : analyze(items.get(0).expression());
        } finally {
            queryScopes.pop();
        }
    }

    @Override
    protected ExpressionAnalysis analyze(PostgresRoutineBodySqlParser.PredicateContext predicate) {
        if (predicate instanceof PostgresRoutineBodySqlParser.AndPredicateContext value) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    analyze(value.predicate(0)), analyze(value.predicate(1)));
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.OrPredicateContext value) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    analyze(value.predicate(0)), analyze(value.predicate(1)));
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.NotPredicateContext value) return analyze(value.predicate());
        if (predicate instanceof PostgresRoutineBodySqlParser.ParenPredicateContext value) return analyze(value.predicate());
        if (predicate instanceof PostgresRoutineBodySqlParser.ComparisonPredicateContext value) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    analyze(value.expression(0)), analyze(value.expression(1)));
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.LikePredicateContext value) {
            ExpressionAnalysis combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, analyze(value.expression(0)), analyze(value.expression(1)));
            return value.expression().size() > 2 ? ExpressionAnalysis.combine(
                    LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    combined, analyze(value.expression(2))) : combined;
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.IsNullPredicateContext value) {
            return new ExpressionAnalysis(analyze(value.expression()).sources(),
                    LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.LiteralInPredicateContext value) {
            ExpressionAnalysis combined = analyze(value.expression());
            for (PostgresRoutineBodySqlParser.ExpressionContext item : value.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(item));
            }
            return combined;
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.InSubqueryPredicateContext value) {
            return analyze(value.expression());
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.TupleInSubqueryPredicateContext value) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresRoutineBodySqlParser.ExpressionContext item : value.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(item));
            }
            return combined;
        }
        if (predicate instanceof PostgresRoutineBodySqlParser.ExpressionPredicateContext value) {
            return analyze(value.expression());
        }
        return ExpressionAnalysis.empty();
    }
}
