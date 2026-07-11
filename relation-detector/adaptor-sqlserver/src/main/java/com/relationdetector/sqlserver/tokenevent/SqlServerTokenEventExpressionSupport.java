package com.relationdetector.sqlserver.tokenevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** Column, scalar-subquery and CASE expression analysis for SQL Server token-event SQL. */
abstract class SqlServerTokenEventExpressionSupport extends SqlServerTokenEventVisitorState {
    SqlServerTokenEventExpressionSupport(SqlStatementRecord statement, boolean ddlOnly) {
        super(statement, ddlOnly);
    }

    protected ColumnRead singleSelectColumn(SqlServerRelationSqlParser.Select_statementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    protected List<ColumnRead> selectColumns(SqlServerRelationSqlParser.Select_statementContext select) {
        SqlServerRelationSqlParser.Query_specificationContext query = firstQuerySpecification(select);
        if (query == null) return List.of();
        List<ColumnRead> columns = new ArrayList<>();
        for (SqlServerRelationSqlParser.Select_list_elemContext item : query.select_list().select_list_elem()) {
            if (item.expression() == null) return List.of();
            ColumnRead column = singleColumn(item.expression(), singleProjectionQualifier(query.table_sources()));
            if (column == null) return List.of();
            columns.add(column);
        }
        return columns;
    }

    protected SqlServerRelationSqlParser.Query_specificationContext firstQuerySpecification(
            SqlServerRelationSqlParser.Select_statementContext select) {
        if (select == null || select.query_expression() == null
                || select.query_expression().query_specification().isEmpty()) return null;
        return select.query_expression().query_specification(0);
    }

    @Override
    protected ColumnRead singleColumn(SqlServerRelationSqlParser.ExpressionContext expression) {
        return singleColumn(expression, "");
    }

    protected ColumnRead singleColumn(
            SqlServerRelationSqlParser.ExpressionContext expression,
            String defaultQualifier) {
        if (expression.expression_atom().size() != 1 || !expression.binary_operator().isEmpty()) return null;
        return singleColumn(expression.expression_atom(0), defaultQualifier);
    }

    private ColumnRead singleColumn(
            SqlServerRelationSqlParser.Expression_atomContext atom,
            String defaultQualifier) {
        if (atom.full_column_name() != null) {
            List<String> nameParts = parts(atom.full_column_name().getText());
            if (nameParts.size() == 1) {
                return defaultQualifier.isBlank() ? null : new ColumnRead(defaultQualifier, nameParts.get(0));
            }
            return new ColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
        }
        return atom.expression() == null ? null : singleColumn(atom.expression(), defaultQualifier);
    }

    protected ExpressionAnalysis analyze(SqlServerRelationSqlParser.ExpressionContext expression) {
        return analyze(expression, "");
    }

    protected ExpressionAnalysis analyze(
            SqlServerRelationSqlParser.ExpressionContext expression,
            String defaultQualifier) {
        ExpressionAnalysis result = ExpressionAnalysis.empty();
        for (SqlServerRelationSqlParser.Expression_atomContext atom : expression.expression_atom()) {
            result = ExpressionAnalysis.combine(
                    expression.binary_operator().isEmpty() ? result.transform() : LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE, result, analyze(atom, defaultQualifier));
        }
        return result;
    }

    private ExpressionAnalysis analyze(
            SqlServerRelationSqlParser.Expression_atomContext atom,
            String defaultQualifier) {
        if (atom.CAST() != null && atom.expression() != null) {
            ExpressionAnalysis value = analyze(atom.expression(), defaultQualifier);
            return new ExpressionAnalysis(value.sources(), LineageTransformType.FUNCTION_CALL,
                    LineageFlowKind.VALUE, value.controlSources(), value.controlTransform());
        }
        ColumnRead column = singleColumn(atom, defaultQualifier);
        if (column != null) return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        if (atom.function_call() != null) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (atom.function_call().expression_list_() != null) {
                for (SqlServerRelationSqlParser.ExpressionContext argument
                        : atom.function_call().expression_list_().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(),
                            args, analyze(argument, defaultQualifier));
                }
            }
            String name = baseName(atom.function_call().function_name().getText()).toLowerCase(Locale.ROOT);
            LineageTransformType transform = LineageTransformClassifier.classifyFunction(
                    name, false, functionExtensions);
            return new ExpressionAnalysis(args.sources(),
                    LineageTransformClassifier.dominant(transform, args.transform()),
                    LineageFlowKind.VALUE, args.controlSources(), args.controlTransform());
        }
        if (atom.case_expression() != null) return analyzeCase(atom.case_expression(), defaultQualifier);
        if (atom.expression_atom() != null) {
            ExpressionAnalysis nested = analyze(atom.expression_atom(), defaultQualifier);
            return new ExpressionAnalysis(nested.sources(),
                    LineageTransformClassifier.dominant(LineageTransformType.ARITHMETIC, nested.transform()),
                    nested.flowKind(), nested.controlSources(), nested.controlTransform());
        }
        if (atom.expression() != null) return analyze(atom.expression(), defaultQualifier);
        if (atom.select_statement() != null) return analyzeScalarSubquery(atom.select_statement());
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis analyzeScalarSubquery(SqlServerRelationSqlParser.Select_statementContext select) {
        visit(select);
        SqlServerRelationSqlParser.Query_specificationContext query = firstQuerySpecification(select);
        if (query == null || query.select_list().select_list_elem().size() != 1
                || query.select_list().select_list_elem(0).expression() == null) return ExpressionAnalysis.empty();
        String qualifier = singleProjectionQualifier(query.table_sources());
        ExpressionAnalysis value = analyze(query.select_list().select_list_elem(0).expression(), qualifier);
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        if (query.table_sources() != null) {
            for (SqlServerRelationSqlParser.Table_sourceContext table : query.table_sources().table_source()) {
                for (SqlServerRelationSqlParser.Table_source_suffixContext suffix : table.table_source_suffix()) {
                    if (suffix.join_on() != null) {
                        control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                                LineageFlowKind.CONTROL, control,
                                analyzeSearchCondition(suffix.join_on().search_condition(), qualifier));
                    }
                }
            }
        }
        if (query.search_condition_clause() != null) {
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control,
                    analyzeSearchCondition(query.search_condition_clause().search_condition(), qualifier));
        }
        if (query.group_by_clause() != null) {
            for (SqlServerRelationSqlParser.ExpressionContext grouping
                    : query.group_by_clause().expression_list_().expression()) {
                control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(grouping, qualifier));
            }
        }
        if (query.having_clause() != null) {
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control,
                    analyzeSearchCondition(query.having_clause().search_condition(), qualifier));
        }
        return new ExpressionAnalysis(value.sources(), value.transform(), LineageFlowKind.VALUE,
                control.sources(), LineageTransformType.CASE_WHEN);
    }

    private ExpressionAnalysis analyzeCase(
            SqlServerRelationSqlParser.Case_expressionContext ctx,
            String defaultQualifier) {
        ExpressionAnalysis value = ExpressionAnalysis.empty();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        if (ctx.expression() != null) control = combineCaseControl(control, analyze(ctx.expression(), defaultQualifier));
        for (SqlServerRelationSqlParser.Searched_case_whenContext when : ctx.searched_case_when()) {
            control = combineCaseControl(control, analyzeSearchCondition(when.search_condition(), defaultQualifier));
            value = conditionalValue(value, analyze(when.expression(), defaultQualifier));
        }
        for (SqlServerRelationSqlParser.Simple_case_whenContext when : ctx.simple_case_when()) {
            control = combineCaseControl(control, analyze(when.expression(0), defaultQualifier));
            value = conditionalValue(value, analyze(when.expression(1), defaultQualifier));
        }
        if (ctx.case_else() != null) value = conditionalValue(value,
                analyze(ctx.case_else().expression(), defaultQualifier));
        List<ColumnRead> controls = new ArrayList<>();
        controls.addAll(control.sources());
        controls.addAll(control.controlSources());
        controls.addAll(value.controlSources());
        return new ExpressionAnalysis(value.sources(), LineageTransformType.CASE_WHEN,
                LineageFlowKind.VALUE, controls.stream().distinct().toList(), LineageTransformType.CASE_WHEN);
    }

    private ExpressionAnalysis conditionalValue(ExpressionAnalysis left, ExpressionAnalysis right) {
        List<ColumnRead> sources = new ArrayList<>();
        sources.addAll(left.sources());
        sources.addAll(right.sources());
        List<ColumnRead> controls = new ArrayList<>();
        controls.addAll(left.controlSources());
        controls.addAll(right.controlSources());
        return new ExpressionAnalysis(sources.stream().distinct().toList(), LineageTransformType.CASE_WHEN,
                LineageFlowKind.VALUE, controls.stream().distinct().toList(), LineageTransformType.CASE_WHEN);
    }

    private ExpressionAnalysis combineCaseControl(ExpressionAnalysis left, ExpressionAnalysis right) {
        List<ColumnRead> controls = new ArrayList<>();
        controls.addAll(right.sources());
        controls.addAll(right.controlSources());
        ExpressionAnalysis normalized = new ExpressionAnalysis(controls.stream().distinct().toList(),
                LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                List.of(), LineageTransformType.CASE_WHEN);
        return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                LineageFlowKind.CONTROL, left, normalized);
    }

    private ExpressionAnalysis analyzeSearchCondition(
            SqlServerRelationSqlParser.Search_conditionContext ctx,
            String defaultQualifier) {
        ExpressionAnalysis result = ExpressionAnalysis.empty();
        for (SqlServerRelationSqlParser.PredicateContext predicate : ctx.predicate()) {
            for (SqlServerRelationSqlParser.ExpressionContext expression : predicate.expression()) {
                result = ExpressionAnalysis.combine(result.transform(), LineageFlowKind.CONTROL,
                        result, analyze(expression, defaultQualifier));
            }
        }
        return result;
    }
}
