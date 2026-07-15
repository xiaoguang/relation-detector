package com.relationdetector.oracle.tokenevent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** Direct column and value-expression analysis for Oracle token-event SQL. */
abstract class OracleTokenEventExpressionSupport extends OracleTokenEventVisitorState {
    OracleTokenEventExpressionSupport(SqlStatementRecord statement) {
        super(statement);
    }

    protected String outputColumn(OracleRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        OracleColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    protected OracleColumnRead singleSelectColumn(OracleRelationSqlParser.SelectStatementContext select) {
        List<OracleColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    protected List<OracleColumnRead> selectColumns(OracleRelationSqlParser.SelectStatementContext select) {
        queryScopes.push(scopeFor(select.querySpecification()));
        try {
            List<OracleRelationSqlParser.SelectItemContext> items =
                    select.querySpecification().selectList().selectItem();
            List<OracleColumnRead> columns = new ArrayList<>();
            for (OracleRelationSqlParser.SelectItemContext item : items) {
                if (item.expression() == null) {
                    return List.of();
                }
                OracleColumnRead column = singleColumn(item.expression());
                if (column == null) {
                    return List.of();
                }
                columns.add(column);
            }
            return columns;
        } finally {
            queryScopes.pop();
        }
    }

    protected OracleColumnRead singleColumn(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.TriggerPseudoColumnExpressionContext pseudo) {
            return pseudoColumn(pseudo.triggerPseudoColumn());
        }
        if (expression instanceof OracleRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> nameParts = parts(columnExpression.qualifiedName());
            if (nameParts.size() == 1) {
                if (routineScope.isSymbol(nameParts.get(0))) {
                    return null;
                }
                return new OracleColumnRead(defaultColumnAlias(), nameParts.get(0));
            }
            return new OracleColumnRead(nameParts.get(nameParts.size() - 2), nameParts.get(nameParts.size() - 1));
        }
        if (expression instanceof OracleRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    protected OracleExpressionAnalysis analyze(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.TriggerPseudoColumnExpressionContext pseudo) {
            OracleColumnRead column = pseudoColumn(pseudo.triggerPseudoColumn());
            return column == null ? OracleExpressionAnalysis.empty()
                    : OracleExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleRelationSqlParser.ColumnExpressionContext columnExpression) {
            OracleColumnRead column = singleColumn(columnExpression);
            return column == null ? OracleExpressionAnalysis.empty()
                    : OracleExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleRelationSqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression());
        }
        if (expression instanceof OracleRelationSqlParser.BinaryExpressionContext binary) {
            OracleExpressionAnalysis left = analyze(binary.expression(0));
            OracleExpressionAnalysis right = analyze(binary.expression(1));
            LineageTransformType transform = binary.arithmeticOperator().CONCAT() != null
                    ? LineageTransformType.CONCAT_FORMAT : LineageTransformType.ARITHMETIC;
            return OracleExpressionAnalysis.withTransform(transform, LineageFlowKind.VALUE, left, right);
        }
        if (expression instanceof OracleRelationSqlParser.UnaryExpressionContext unary) {
            return OracleExpressionAnalysis.withTransform(LineageTransformType.ARITHMETIC,
                    LineageFlowKind.VALUE, analyze(unary.expression()));
        }
        if (expression instanceof OracleRelationSqlParser.FunctionExpressionContext function) {
            OracleExpressionAnalysis args = OracleExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (OracleRelationSqlParser.ExpressionContext argument
                        : function.functionCall().expressionList().expression()) {
                    args = OracleExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName()))
                    .toLowerCase(Locale.ROOT);
            LineageTransformType transform = LineageTransformClassifier.classifyFunction(
                    functionName, false, functionExtensions);
            if (transform == LineageTransformType.AGGREGATE
                    || transform == LineageTransformType.CONCAT_FORMAT) {
                return new OracleExpressionAnalysis(args.sources(), transform, LineageFlowKind.VALUE);
            }
            if (transform == LineageTransformType.COALESCE) {
                return new OracleExpressionAnalysis(args.sources(),
                        LineageTransformClassifier.dominant(transform, args.transform()), LineageFlowKind.VALUE);
            }
            LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
            LineageFlowKind flowKind = dominant == LineageTransformType.CASE_WHEN
                    ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE;
            return new OracleExpressionAnalysis(args.sources(), dominant, flowKind);
        }
        if (expression instanceof OracleRelationSqlParser.ExtractExpressionContext extract) {
            OracleExpressionAnalysis source = analyze(extract.expression());
            return new OracleExpressionAnalysis(source.sources(),
                    LineageTransformClassifier.dominant(LineageTransformType.FUNCTION_CALL, source.transform()),
                    LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleRelationSqlParser.CaseExpressionContext caseExpression) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.CaseWhenClauseContext clause : caseExpression.caseWhenClause()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(clause.predicate()));
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(clause.expression()));
            }
            for (OracleRelationSqlParser.ExpressionContext part : caseExpression.expression()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, combined, analyze(part));
            }
            return new OracleExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL);
        }
        if (expression instanceof OracleRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            visit(scalarSubquery.selectStatement());
            OracleExpressionAnalysis selected = scalarSubquerySelectExpression(scalarSubquery.selectStatement());
            if (!selected.sources().isEmpty()) {
                return scalarSubqueryWithContext(scalarSubquery.selectStatement(), selected);
            }
            List<OracleColumnRead> columns = selectColumns(scalarSubquery.selectStatement());
            if (columns.isEmpty()) {
                return OracleExpressionAnalysis.empty();
            }
            return scalarSubqueryWithContext(scalarSubquery.selectStatement(),
                    new OracleExpressionAnalysis(columns, LineageTransformType.DIRECT, LineageFlowKind.VALUE));
        }
        return OracleExpressionAnalysis.empty();
    }

    private OracleColumnRead pseudoColumn(OracleRelationSqlParser.TriggerPseudoColumnContext pseudo) {
        String alias = clean(pseudo.PARAMETER().getText());
        if (alias.startsWith(":")) {
            alias = alias.substring(1);
        }
        String column = clean(pseudo.identifier().getText());
        return alias.isBlank() || column.isBlank() ? null : new OracleColumnRead(alias, column);
    }

    protected abstract OracleExpressionAnalysis scalarSubquerySelectExpression(
            OracleRelationSqlParser.SelectStatementContext select);

    protected abstract OracleExpressionAnalysis scalarSubqueryWithContext(
            OracleRelationSqlParser.SelectStatementContext select,
            OracleExpressionAnalysis selectedExpression);

    protected abstract OracleExpressionAnalysis analyze(OracleRelationSqlParser.PredicateContext predicate);
}
