package com.relationdetector.postgres.routine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** Direct columns and base expression semantics for PostgreSQL routine bodies. */
abstract class PostgresRoutineExpressionSupport extends PostgresRoutineVisitorState {
    PostgresRoutineExpressionSupport(SqlStatementRecord statement) { super(statement); }

    protected String outputColumn(PostgresRoutineBodySqlParser.SelectItemContext item) {
        if (item.identifier() != null) return clean(item.identifier().getText());
        if (item.expression() == null) return "";
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    protected ColumnRead singleSelectColumn(PostgresRoutineBodySqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    protected List<ColumnRead> selectColumns(PostgresRoutineBodySqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = new ArrayList<>();
        for (PostgresRoutineBodySqlParser.SelectItemContext item
                : select.querySpecification().selectList().selectItem()) {
            if (item.expression() == null) return List.of();
            ColumnRead column = singleColumn(item.expression());
            if (column == null) return List.of();
            columns.add(column);
        }
        return columns;
    }

    protected ColumnRead singleColumn(PostgresRoutineBodySqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRoutineBodySqlParser.ColumnExpressionContext columnExpression) {
            List<String> nameParts = parts(columnExpression.qualifiedName());
            if (nameParts.size() == 1) return new ColumnRead(defaultColumnAlias(), nameParts.get(0));
            return new ColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
        }
        if (expression instanceof PostgresRoutineBodySqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    protected ExpressionAnalysis analyze(PostgresRoutineBodySqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRoutineBodySqlParser.ColumnExpressionContext value) {
            ColumnRead column = singleColumn(value);
            return column == null ? ExpressionAnalysis.empty()
                    : ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresRoutineBodySqlParser.ParenExpressionContext value) {
            return analyze(value.expression());
        }
        if (expression instanceof PostgresRoutineBodySqlParser.BinaryExpressionContext value) {
            LineageTransformType transform = "||".equals(value.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE,
                    analyze(value.expression(0)), analyze(value.expression(1)));
        }
        if (expression instanceof PostgresRoutineBodySqlParser.TypeCastExpressionContext value) {
            return analyze(value.expression());
        }
        if (expression instanceof PostgresRoutineBodySqlParser.UnaryMinusExpressionContext value) {
            ExpressionAnalysis operand = analyze(value.expression());
            return new ExpressionAnalysis(operand.sources(), LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresRoutineBodySqlParser.FunctionExpressionContext value) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (value.functionCall().expressionList() != null) {
                for (PostgresRoutineBodySqlParser.ExpressionContext argument
                        : value.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            String functionName = baseName(qualifiedName(value.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            boolean windowed = value.windowClause() != null;
            LineageTransformType transform = switch (functionName) {
                case "sum" -> windowed ? LineageTransformType.CUMULATIVE : LineageTransformType.AGGREGATE;
                case "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
                case "coalesce" -> LineageTransformType.COALESCE;
                case "concat", "format", "string_agg" -> LineageTransformType.CONCAT_FORMAT;
                default -> LineageTransformType.FUNCTION_CALL;
            };
            LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
            return new ExpressionAnalysis(args.sources(), dominant,
                    dominant == LineageTransformType.CASE_WHEN
                            ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresRoutineBodySqlParser.CaseExpressionContext value) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresRoutineBodySqlParser.CaseWhenClauseContext clause : value.caseWhenClause()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(clause.predicate()));
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(clause.expression()));
            }
            for (PostgresRoutineBodySqlParser.ExpressionContext part : value.expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(part));
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof PostgresRoutineBodySqlParser.ScalarSubqueryExpressionContext value) {
            return analyzeScalarSubquery(value.selectStatement());
        }
        return ExpressionAnalysis.empty();
    }

    protected List<ExpressionAnalysis> writeAnalyses(PostgresRoutineBodySqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRoutineBodySqlParser.ScalarSubqueryExpressionContext value) {
            return scalarSubqueryWriteAnalyses(value.selectStatement());
        }
        if (expression instanceof PostgresRoutineBodySqlParser.FunctionExpressionContext value) {
            return functionWriteAnalyses(value);
        }
        if (expression instanceof PostgresRoutineBodySqlParser.BinaryExpressionContext value) {
            return binaryWriteAnalyses(value);
        }
        if (expression instanceof PostgresRoutineBodySqlParser.TypeCastExpressionContext value) {
            return writeAnalyses(value.expression());
        }
        if (expression instanceof PostgresRoutineBodySqlParser.UnaryMinusExpressionContext value) {
            return writeAnalyses(value.expression()).stream()
                    .map(analysis -> analysis.flowKind() == LineageFlowKind.CONTROL ? analysis
                            : new ExpressionAnalysis(analysis.sources(), LineageTransformType.ARITHMETIC,
                                    LineageFlowKind.VALUE))
                    .toList();
        }
        if (!(expression instanceof PostgresRoutineBodySqlParser.CaseExpressionContext value)) {
            ExpressionAnalysis analysis = analyze(expression);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        ExpressionAnalysis values = ExpressionAnalysis.empty();
        ExpressionAnalysis controls = ExpressionAnalysis.empty();
        for (PostgresRoutineBodySqlParser.CaseWhenClauseContext clause : value.caseWhenClause()) {
            values = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, values, analyze(clause.expression()));
            controls = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, controls, analyze(clause.predicate()));
        }
        List<PostgresRoutineBodySqlParser.ExpressionContext> outer = value.expression();
        int selectorCount = outer.size() - (value.ELSE() == null ? 0 : 1);
        for (int index = 0; index < selectorCount; index++) {
            controls = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, controls, analyze(outer.get(index)));
        }
        if (value.ELSE() != null && !outer.isEmpty()) {
            values = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, values, analyze(outer.get(outer.size() - 1)));
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!values.sources().isEmpty()) result.add(new ExpressionAnalysis(
                values.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.VALUE));
        if (!controls.sources().isEmpty()) result.add(new ExpressionAnalysis(
                controls.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        return List.copyOf(result);
    }

    protected abstract List<ExpressionAnalysis> binaryWriteAnalyses(
            PostgresRoutineBodySqlParser.BinaryExpressionContext expression);
    protected abstract List<ExpressionAnalysis> functionWriteAnalyses(
            PostgresRoutineBodySqlParser.FunctionExpressionContext expression);
    protected abstract List<ExpressionAnalysis> scalarSubqueryWriteAnalyses(
            PostgresRoutineBodySqlParser.SelectStatementContext select);
    protected abstract ExpressionAnalysis analyzeScalarSubquery(
            PostgresRoutineBodySqlParser.SelectStatementContext select);
    protected abstract ExpressionAnalysis analyze(PostgresRoutineBodySqlParser.PredicateContext predicate);
    protected abstract List<ExpressionAnalysis> selectItemAnalyses(
            PostgresRoutineBodySqlParser.SelectItemContext item);
}
