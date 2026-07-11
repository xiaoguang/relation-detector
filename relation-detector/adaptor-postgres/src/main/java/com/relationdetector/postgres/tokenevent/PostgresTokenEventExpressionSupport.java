package com.relationdetector.postgres.tokenevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** VALUE/CONTROL, scalar-subquery and transform analysis for PostgreSQL token-event SQL. */
abstract class PostgresTokenEventExpressionSupport extends PostgresTokenEventVisitorState {
    PostgresTokenEventExpressionSupport(SqlStatementRecord statement) { super(statement); }

    protected String outputColumn(PostgresRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) return clean(item.identifier().getText());
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    protected ColumnRead singleSelectColumn(PostgresRelationSqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    protected List<ColumnRead> selectColumns(PostgresRelationSqlParser.SelectStatementContext select) {
        queryScopes.push(scopeFor(select.querySpecification()));
        try {
            List<ColumnRead> columns = new ArrayList<>();
            for (PostgresRelationSqlParser.SelectItemContext item
                    : select.querySpecification().selectList().selectItem()) {
                if (item.expression() == null) return List.of();
                ColumnRead column = singleColumn(item.expression());
                if (column == null) return List.of();
                columns.add(column);
            }
            return columns;
        } finally {
            queryScopes.pop();
        }
    }

    protected ColumnRead singleColumn(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> nameParts = parts(columnExpression.qualifiedName());
            return nameParts.size() == 1
                    ? new ColumnRead(defaultColumnAlias(), nameParts.get(0))
                    : new ColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
        }
        if (expression instanceof PostgresRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    protected ExpressionAnalysis analyze(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression);
            return column == null ? ExpressionAnalysis.empty()
                    : ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresRelationSqlParser.ParenExpressionContext value) {
            return analyze(value.expression());
        }
        if (expression instanceof PostgresRelationSqlParser.CastExpressionContext value) {
            return analyze(value.expression());
        }
        if (expression instanceof PostgresRelationSqlParser.StandardCastExpressionContext value) {
            return analyze(value.expression());
        }
        if (expression instanceof PostgresRelationSqlParser.BinaryExpressionContext value) {
            LineageTransformType transform = "||".equals(value.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE,
                    analyze(value.expression(0)), analyze(value.expression(1)));
        }
        if (expression instanceof PostgresRelationSqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (PostgresRelationSqlParser.ExpressionContext argument
                        : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            if (function.windowClause() != null) {
                args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args,
                        analyzeWindowClause(function.windowClause()));
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            boolean windowed = function.windowClause() != null;
            LineageTransformType transform = switch (functionName) {
                case "sum" -> windowed ? LineageTransformType.CUMULATIVE : LineageTransformType.AGGREGATE;
                case "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
                case "coalesce" -> LineageTransformType.COALESCE;
                case "concat", "format", "string_agg", "to_char" -> LineageTransformType.CONCAT_FORMAT;
                default -> LineageTransformType.FUNCTION_CALL;
            };
            LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
            return new ExpressionAnalysis(args.sources(), dominant,
                    dominant == LineageTransformType.CASE_WHEN ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresRelationSqlParser.ExtractExpressionContext value) {
            ExpressionAnalysis source = analyze(value.expression());
            return new ExpressionAnalysis(source.sources(),
                    LineageTransformClassifier.dominant(LineageTransformType.FUNCTION_CALL, source.transform()),
                    source.flowKind());
        }
        if (expression instanceof PostgresRelationSqlParser.CaseExpressionContext value) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresRelationSqlParser.CaseWhenClauseContext clause : value.caseWhenClause()) {
                combined = controls(combined, analyze(clause.predicate()));
                combined = controls(combined, analyze(clause.expression()));
            }
            for (PostgresRelationSqlParser.ExpressionContext part : value.expression()) {
                combined = controls(combined, analyze(part));
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof PostgresRelationSqlParser.ScalarSubqueryExpressionContext value) {
            return analyzeScalarSubquery(value.selectStatement());
        }
        if (expression instanceof PostgresRelationSqlParser.ArrayExpressionContext value) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            if (value.expressionList() != null) {
                for (PostgresRelationSqlParser.ExpressionContext item : value.expressionList().expression()) {
                    combined = ExpressionAnalysis.combine(combined.transform(), combined.flowKind(), combined,
                            analyze(item));
                }
            }
            return combined;
        }
        return ExpressionAnalysis.empty();
    }

    protected List<ExpressionAnalysis> writeAnalyses(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.ScalarSubqueryExpressionContext value) {
            return scalarSubqueryWriteAnalyses(value.selectStatement());
        }
        if (!(expression instanceof PostgresRelationSqlParser.CaseExpressionContext value)) {
            ExpressionAnalysis analysis = analyze(expression);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        ExpressionAnalysis selected = ExpressionAnalysis.empty();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        for (PostgresRelationSqlParser.CaseWhenClauseContext clause : value.caseWhenClause()) {
            selected = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, selected, analyze(clause.expression()));
            control = controls(control, analyze(clause.predicate()));
        }
        List<PostgresRelationSqlParser.ExpressionContext> outer = value.expression();
        int selectorCount = outer.size() - (value.ELSE() == null ? 0 : 1);
        for (int index = 0; index < selectorCount; index++) control = controls(control, analyze(outer.get(index)));
        if (value.ELSE() != null && !outer.isEmpty()) {
            selected = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.VALUE,
                    selected, analyze(outer.get(outer.size() - 1)));
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!selected.sources().isEmpty()) result.add(new ExpressionAnalysis(selected.sources(),
                LineageTransformType.CASE_WHEN, LineageFlowKind.VALUE));
        if (!control.sources().isEmpty()) result.add(new ExpressionAnalysis(control.sources(),
                LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        return List.copyOf(result);
    }

    private List<ExpressionAnalysis> scalarSubqueryWriteAnalyses(
            PostgresRelationSqlParser.SelectStatementContext select) {
        ExpressionAnalysis value = analyzeScalarSubquery(select);
        ExpressionAnalysis control = scalarSubqueryContext(select);
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) result.add(new ExpressionAnalysis(value.sources(), value.transform(),
                LineageFlowKind.VALUE));
        if (!control.sources().isEmpty()) result.add(new ExpressionAnalysis(control.sources(),
                LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        return List.copyOf(result);
    }

    private ExpressionAnalysis scalarSubqueryContext(PostgresRelationSqlParser.SelectStatementContext select) {
        PostgresRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(scopeFor(query));
        try {
            ExpressionAnalysis control = ExpressionAnalysis.empty();
            if (query.fromClause() != null) {
                for (PostgresRelationSqlParser.TableReferenceContext table : query.fromClause().tableReference()) {
                    for (PostgresRelationSqlParser.JoinClauseContext join : table.joinClause()) {
                        if (join.predicate() != null) control = controls(control, analyze(join.predicate()));
                    }
                }
            }
            if (query.whereClause() != null) control = controls(control, analyze(query.whereClause().predicate()));
            if (query.groupByClause() != null) {
                for (PostgresRelationSqlParser.ExpressionContext grouping
                        : query.groupByClause().expressionList().expression()) {
                    control = controls(control, analyze(grouping));
                }
            }
            if (query.havingClause() != null) control = controls(control, analyze(query.havingClause().predicate()));
            return control;
        } finally {
            queryScopes.pop();
        }
    }

    private ExpressionAnalysis analyzeWindowClause(PostgresRelationSqlParser.WindowClauseContext windowClause) {
        List<String> tokens = new ArrayList<>();
        collectLeafText(windowClause, tokens);
        List<ColumnRead> columns = new ArrayList<>();
        for (int index = 0; index + 2 < tokens.size(); index++) {
            if (!".".equals(tokens.get(index + 1))) continue;
            String alias = clean(tokens.get(index));
            String column = clean(tokens.get(index + 2));
            if (!alias.isBlank() && !column.isBlank()) columns.add(new ColumnRead(alias, column));
        }
        return columns.isEmpty() ? ExpressionAnalysis.empty()
                : new ExpressionAnalysis(columns.stream().distinct().toList(),
                        LineageTransformType.WINDOW_DERIVED, LineageFlowKind.VALUE);
    }

    private void collectLeafText(ParseTree tree, List<String> tokens) {
        if (tree instanceof TerminalNode terminal) {
            if (terminal.getText() != null && !terminal.getText().isBlank()) tokens.add(terminal.getText());
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) collectLeafText(tree.getChild(index), tokens);
    }

    private ExpressionAnalysis analyzeScalarSubquery(PostgresRelationSqlParser.SelectStatementContext select) {
        if (select.withClause() != null) visit(select.withClause());
        PostgresRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(new QueryScope());
        try {
            if (query.fromClause() != null) visit(query.fromClause());
            if (query.whereClause() != null) visit(query.whereClause());
            if (query.havingClause() != null) visit(query.havingClause());
            List<PostgresRelationSqlParser.SelectItemContext> items = query.selectList().selectItem();
            return items.size() == 1 && items.get(0).expression() != null
                    ? analyze(items.get(0).expression()) : ExpressionAnalysis.empty();
        } finally {
            queryScopes.pop();
        }
    }

    protected ExpressionAnalysis analyze(PostgresRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof PostgresRelationSqlParser.AndPredicateContext value)
            return controls(analyze(value.predicate(0)), analyze(value.predicate(1)));
        if (predicate instanceof PostgresRelationSqlParser.OrPredicateContext value)
            return controls(analyze(value.predicate(0)), analyze(value.predicate(1)));
        if (predicate instanceof PostgresRelationSqlParser.NotPredicateContext value) return analyze(value.predicate());
        if (predicate instanceof PostgresRelationSqlParser.ParenPredicateContext value) return analyze(value.predicate());
        if (predicate instanceof PostgresRelationSqlParser.ComparisonPredicateContext value)
            return controls(analyze(value.expression(0)), analyze(value.expression(1)));
        if (predicate instanceof PostgresRelationSqlParser.LikePredicateContext value) {
            ExpressionAnalysis result = controls(analyze(value.expression(0)), analyze(value.expression(1)));
            return value.expression().size() > 2 ? controls(result, analyze(value.expression(2))) : result;
        }
        if (predicate instanceof PostgresRelationSqlParser.IsNullPredicateContext value) return analyze(value.expression());
        if (predicate instanceof PostgresRelationSqlParser.LiteralInPredicateContext value) {
            ExpressionAnalysis result = analyze(value.expression());
            for (PostgresRelationSqlParser.ExpressionContext item : value.expressionList().expression())
                result = controls(result, analyze(item));
            return result;
        }
        if (predicate instanceof PostgresRelationSqlParser.InSubqueryPredicateContext value)
            return analyze(value.expression());
        if (predicate instanceof PostgresRelationSqlParser.TupleInSubqueryPredicateContext value) {
            ExpressionAnalysis result = ExpressionAnalysis.empty();
            for (PostgresRelationSqlParser.ExpressionContext item : value.expressionList().expression())
                result = controls(result, analyze(item));
            return result;
        }
        if (predicate instanceof PostgresRelationSqlParser.ExpressionPredicateContext value)
            return analyze(value.expression());
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis controls(ExpressionAnalysis left, ExpressionAnalysis right) {
        return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL, left, right);
    }
}
