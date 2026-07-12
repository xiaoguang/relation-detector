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
        if (expression.expressionContinuation().expression() != null
                || expression.castAtom().castSuffix().TYPE_CAST() != null) return null;
        PostgresRoutineBodySqlParser.ExpressionAtomContext atom = expression.castAtom().expressionAtom();
        if (atom.qualifiedName() != null) {
            List<String> nameParts = parts(atom.qualifiedName());
            if (nameParts.size() == 1) return new ColumnRead(defaultColumnAlias(), nameParts.get(0));
            return new ColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
        }
        if (atom.CASE() == null && atom.expression().size() == 1) {
            return singleColumn(atom.expression(0));
        }
        return null;
    }

    protected ExpressionAnalysis analyze(PostgresRoutineBodySqlParser.ExpressionContext expression) {
        ExpressionAnalysis base = analyze(expression.castAtom().expressionAtom());
        PostgresRoutineBodySqlParser.ExpressionContinuationContext continuation =
                expression.expressionContinuation();
        if (continuation.expression() == null) return base;
        LineageTransformType transform = continuation.CONCAT() == null
                ? LineageTransformType.ARITHMETIC : LineageTransformType.CONCAT_FORMAT;
        return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE,
                base, analyze(continuation.expression()));
    }

    private ExpressionAnalysis analyze(PostgresRoutineBodySqlParser.ExpressionAtomContext atom) {
        if (atom.qualifiedName() != null) {
            List<String> nameParts = parts(atom.qualifiedName());
            ColumnRead column = nameParts.size() == 1
                    ? new ColumnRead(defaultColumnAlias(), nameParts.get(0))
                    : new ColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
            return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (atom.expressionAtom() != null) {
            ExpressionAnalysis operand = analyze(atom.expressionAtom());
            return new ExpressionAnalysis(operand.sources(), LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE);
        }
        if (atom.functionCall() != null) return analyzeFunction(atom);
        if (atom.selectStatement() != null) return analyzeScalarSubquery(atom.selectStatement());
        if (atom.CASE() != null) return analyzeCase(atom);
        if (atom.expression().size() == 1) return analyze(atom.expression(0));
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis analyzeFunction(PostgresRoutineBodySqlParser.ExpressionAtomContext atom) {
        ExpressionAnalysis args = ExpressionAnalysis.empty();
        if (atom.functionCall().expressionList() != null) {
            for (PostgresRoutineBodySqlParser.ExpressionContext argument
                    : atom.functionCall().expressionList().expression()) {
                args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
            }
        }
        String functionName = baseName(qualifiedName(atom.functionCall().qualifiedName())).toLowerCase(Locale.ROOT);
        LineageTransformType transform = switch (functionName) {
            case "sum" -> atom.windowClause() == null
                    ? LineageTransformType.AGGREGATE : LineageTransformType.CUMULATIVE;
            case "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
            case "coalesce" -> LineageTransformType.COALESCE;
            case "concat", "format", "string_agg" -> LineageTransformType.CONCAT_FORMAT;
            default -> LineageTransformType.FUNCTION_CALL;
        };
        LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
        return new ExpressionAnalysis(args.sources(), dominant,
                dominant == LineageTransformType.CASE_WHEN ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE);
    }

    private ExpressionAnalysis analyzeCase(PostgresRoutineBodySqlParser.ExpressionAtomContext atom) {
        ExpressionAnalysis combined = ExpressionAnalysis.empty();
        for (PostgresRoutineBodySqlParser.CaseWhenClauseContext clause : atom.caseWhenClause()) {
            combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, combined, analyze(clause.predicate()));
            combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, combined, analyze(clause.expression()));
        }
        for (PostgresRoutineBodySqlParser.ExpressionContext part : atom.expression()) {
            combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, combined, analyze(part));
        }
        return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                LineageFlowKind.CONTROL);
    }

    protected List<ExpressionAnalysis> writeAnalyses(PostgresRoutineBodySqlParser.ExpressionContext expression) {
        List<ExpressionAnalysis> base = writeAtomAnalyses(expression.castAtom().expressionAtom());
        PostgresRoutineBodySqlParser.ExpressionContinuationContext continuation =
                expression.expressionContinuation();
        if (continuation.expression() == null) return base;
        LineageTransformType transform = continuation.CONCAT() == null
                ? LineageTransformType.ARITHMETIC : LineageTransformType.CONCAT_FORMAT;
        return combineWriteAnalyses(base, writeAnalyses(continuation.expression()), transform);
    }

    private List<ExpressionAnalysis> writeAtomAnalyses(
            PostgresRoutineBodySqlParser.ExpressionAtomContext atom
    ) {
        if (atom.selectStatement() != null) return scalarSubqueryWriteAnalyses(atom.selectStatement());
        if (atom.functionCall() != null) return functionWriteAnalyses(atom);
        if (atom.expressionAtom() != null) {
            return writeAtomAnalyses(atom.expressionAtom()).stream()
                    .map(analysis -> analysis.flowKind() == LineageFlowKind.CONTROL ? analysis
                            : new ExpressionAnalysis(analysis.sources(), LineageTransformType.ARITHMETIC,
                                    LineageFlowKind.VALUE))
                    .toList();
        }
        if (atom.CASE() == null) {
            ExpressionAnalysis analysis = analyze(atom);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        ExpressionAnalysis values = ExpressionAnalysis.empty();
        ExpressionAnalysis controls = ExpressionAnalysis.empty();
        for (PostgresRoutineBodySqlParser.CaseWhenClauseContext clause : atom.caseWhenClause()) {
            values = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, values, analyze(clause.expression()));
            controls = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, controls, analyze(clause.predicate()));
        }
        List<PostgresRoutineBodySqlParser.ExpressionContext> outer = atom.expression();
        int selectorCount = outer.size() - (atom.ELSE() == null ? 0 : 1);
        for (int index = 0; index < selectorCount; index++) {
            controls = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, controls, analyze(outer.get(index)));
        }
        if (atom.ELSE() != null && !outer.isEmpty()) {
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

    private List<ExpressionAnalysis> combineWriteAnalyses(
            List<ExpressionAnalysis> left,
            List<ExpressionAnalysis> right,
            LineageTransformType transform
    ) {
        ExpressionAnalysis values = ExpressionAnalysis.empty();
        ExpressionAnalysis controls = ExpressionAnalysis.empty();
        for (ExpressionAnalysis analysis : java.util.stream.Stream.concat(left.stream(), right.stream()).toList()) {
            if (analysis.flowKind() == LineageFlowKind.CONTROL) {
                controls = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, controls, analysis);
            } else {
                values = ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE, values, analysis);
            }
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!values.sources().isEmpty()) {
            LineageTransformType effective = transform == LineageTransformType.CONCAT_FORMAT
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformClassifier.dominant(transform, values.transform());
            result.add(new ExpressionAnalysis(values.sources(), effective, LineageFlowKind.VALUE));
        }
        if (!controls.sources().isEmpty()) result.add(new ExpressionAnalysis(controls.sources(),
                LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        return List.copyOf(result);
    }

    protected abstract List<ExpressionAnalysis> functionWriteAnalyses(
            PostgresRoutineBodySqlParser.ExpressionAtomContext expression);
    protected abstract List<ExpressionAnalysis> scalarSubqueryWriteAnalyses(
            PostgresRoutineBodySqlParser.SelectStatementContext select);
    protected abstract ExpressionAnalysis analyzeScalarSubquery(
            PostgresRoutineBodySqlParser.SelectStatementContext select);
    protected abstract ExpressionAnalysis analyze(PostgresRoutineBodySqlParser.PredicateContext predicate);
    protected abstract List<ExpressionAnalysis> selectItemAnalyses(
            PostgresRoutineBodySqlParser.SelectItemContext item);
}
