package com.relationdetector.postgres.fullgrammer.routine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.postgres.fullgrammer.routine.PostgresFullGrammerRoutineBodySqlBaseVisitor;
import com.relationdetector.postgres.fullgrammer.routine.PostgresFullGrammerRoutineBodySqlLexer;
import com.relationdetector.postgres.fullgrammer.routine.PostgresFullGrammerRoutineBodySqlParser;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * Parse-tree visitor for PostgreSQL full-grammer routine bodies.
 *
 * <p>CN: 本 visitor 只从 {@code PostgresFullGrammerRoutineBodySql.g4} 的 typed context 生成
 * SQL 结构事件。它可以读取 identifier 原文和 source location，但不通过 regex、
 * token span scanner 或特殊表/列名判断 SQL 结构。
 *
 * <p>EN: Parse-tree visitor for the PostgreSQL full-grammer routine-body grammar. It emits
 * structural events from typed grammar contexts, using token text only for
 * identifier spelling and source locations.
 */
public final class PostgresFullGrammerRoutineBodyParseTreeVisitor extends PostgresFullGrammerRoutineBodySqlBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    private int existsDepth;

    public PostgresFullGrammerRoutineBodyParseTreeVisitor(SqlStatementRecord statement) {
        this.statement = statement;
    }

    public List<StructuredSqlEvent> collect(PostgresFullGrammerRoutineBodySqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        if (node.getSymbol() != null
                && node.getSymbol().getType() == PostgresFullGrammerRoutineBodySqlParser.DOLLAR_QUOTED_STRING) {
            collectRoutineBody(node.getText(), node.getSymbol().getLine());
        }
        return null;
    }

    @Override
    public Void visitCommonTableExpression(PostgresFullGrammerRoutineBodySqlParser.CommonTableExpressionContext ctx) {
        String name = clean(ctx.identifier().getText());
        if (!name.isBlank()) {
            cteNames.add(normalize(name));
            Map<String, Object> attrs = attrs();
            attrs.put("name", name);
            attrs.put("table", name);
            attrs.put("qualifiedTable", name);
            add(StructuredParseEventType.CTE_DECLARATION, ctx, attrs);
            add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
        }
        List<String> outputColumns = ctx.identifierList() == null
                ? List.of()
                : ctx.identifierList().identifier().stream().map(identifier -> clean(identifier.getText())).toList();
        projectionOwners.push(new ProjectionOwner(name, outputColumns));
        visit(ctx.selectStatement());
        projectionOwners.pop();
        return null;
    }

    @Override
    public Void visitQuerySpecification(PostgresFullGrammerRoutineBodySqlParser.QuerySpecificationContext ctx) {
        queryScopes.push(new QueryScope());
        try {
            if (ctx.fromClause() != null) {
                visit(ctx.fromClause());
            }
            if (ctx.whereClause() != null) {
                visit(ctx.whereClause());
            }
            if (ctx.havingClause() != null) {
                visit(ctx.havingClause());
            }
            if (!projectionOwners.isEmpty()) {
                emitProjectionItems(ctx.selectList(), projectionOwners.peek());
            }
        } finally {
            queryScopes.pop();
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(PostgresFullGrammerRoutineBodySqlParser.NamedTablePrimaryContext ctx) {
        String qualified = qualifiedName(ctx.qualifiedName());
        String table = baseName(qualified);
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        Map<String, Object> attrs = attrs();
        attrs.put("keyword", "FROM");
        attrs.put("qualifiedTable", qualified);
        attrs.put("table", table);
        if (!alias.isBlank()) {
            attrs.put("alias", alias);
        }
        add(StructuredParseEventType.ROWSET_REFERENCE, ctx, attrs);
        registerCurrentRowset(alias.isBlank() ? table : alias);
        return null;
    }

    @Override
    public Void visitTableReference(PostgresFullGrammerRoutineBodySqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (PostgresFullGrammerRoutineBodySqlParser.JoinClauseContext join : ctx.joinClause()) {
            String rightAlias = rowsetAlias(join.tablePrimary());
            visit(join.tablePrimary());
            if (join.identifierList() != null) {
                Map<String, Object> attrs = attrs();
                attrs.put("leftAlias", leftAlias);
                attrs.put("rightAlias", rightAlias);
                attrs.put("usingColumns", join.identifierList().identifier().stream()
                        .map(identifier -> clean(identifier.getText()))
                        .toList());
                add(StructuredParseEventType.JOIN_USING_COLUMNS, join, attrs);
            }
            if (join.predicate() != null) {
                joinKinds.push(joinKind(join));
                visit(join.predicate());
                joinKinds.pop();
            }
            leftAlias = rightAlias;
        }
        return null;
    }

    @Override
    public Void visitDerivedTablePrimary(PostgresFullGrammerRoutineBodySqlParser.DerivedTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) {
            Map<String, Object> attrs = attrs();
            attrs.put("name", alias);
            attrs.put("table", alias);
            attrs.put("qualifiedTable", alias);
            add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
            registerCurrentRowset(alias);
            projectionOwners.push(new ProjectionOwner(alias, List.of()));
            visit(ctx.selectStatement());
            projectionOwners.pop();
        } else {
            visit(ctx.selectStatement());
        }
        return null;
    }

    @Override
    public Void visitRowsFromTablePrimary(PostgresFullGrammerRoutineBodySqlParser.RowsFromTablePrimaryContext ctx) {
        Map<String, Object> attrs = attrs();
        attrs.put("name", "ROWS");
        attrs.put("table", "ROWS");
        attrs.put("qualifiedTable", "ROWS FROM");
        if (ctx.tableAlias() != null) {
            attrs.put("alias", clean(ctx.tableAlias().identifier().getText()));
            registerCurrentRowset(clean(ctx.tableAlias().identifier().getText()));
        }
        add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
        return null;
    }

    @Override
    public Void visitFunctionRowsetPrimary(PostgresFullGrammerRoutineBodySqlParser.FunctionRowsetPrimaryContext ctx) {
        String functionName = baseName(qualifiedName(ctx.qualifiedName())).toUpperCase(Locale.ROOT);
        Map<String, Object> attrs = attrs();
        attrs.put("name", functionName);
        attrs.put("table", functionName);
        attrs.put("qualifiedTable", qualifiedName(ctx.qualifiedName()));
        if (ctx.tableAlias() != null) {
            attrs.put("alias", clean(ctx.tableAlias().identifier().getText()));
            registerCurrentRowset(clean(ctx.tableAlias().identifier().getText()));
        }
        add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
        return null;
    }

    @Override
    public Void visitComparisonPredicate(PostgresFullGrammerRoutineBodySqlParser.ComparisonPredicateContext ctx) {
        if (!"=".equals(ctx.comparisonOperator().getText())) {
            return visitChildren(ctx);
        }
        ColumnRead left = singleColumn(ctx.expression(0));
        ColumnRead right = singleColumn(ctx.expression(1));
        if (left == null || right == null) {
            return visitChildren(ctx);
        }
        Map<String, Object> attrs = attrs();
        attrs.put("leftAlias", left.alias());
        attrs.put("leftColumn", left.column());
        attrs.put("rightAlias", right.alias());
        attrs.put("rightColumn", right.column());
        attrs.put("joinKind", existsDepth > 0 ? "EXISTS" : currentJoinKind());
        add(existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE : StructuredParseEventType.PREDICATE_EQUALITY,
                ctx,
                attrs);
        return null;
    }

    @Override
    public Void visitExistsPredicate(PostgresFullGrammerRoutineBodySqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(PostgresFullGrammerRoutineBodySqlParser.InSubqueryPredicateContext ctx) {
        ColumnRead outer = singleColumn(ctx.expression());
        ColumnRead inner = singleSelectColumn(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (outer == null || inner == null) {
            return null;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("outerAlias", outer.alias());
        attrs.put("outerColumn", outer.column());
        attrs.put("innerAlias", inner.alias());
        attrs.put("innerColumn", inner.column());
        attrs.put("innerTable", "");
        attrs.put("innerTableAlias", inner.alias());
        attrs.put("verifiedColumnSubquery", true);
        add(StructuredParseEventType.IN_SUBQUERY_PREDICATE, ctx, attrs);
        return null;
    }

    @Override
    public Void visitTupleInSubqueryPredicate(PostgresFullGrammerRoutineBodySqlParser.TupleInSubqueryPredicateContext ctx) {
        List<ColumnRead> outer = ctx.expressionList().expression().stream().map(this::singleColumn).toList();
        List<ColumnRead> inner = selectColumns(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (outer.isEmpty() || outer.size() != inner.size() || outer.stream().anyMatch(java.util.Objects::isNull)) {
            return null;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("outerAliases", outer.stream().map(ColumnRead::alias).toList());
        attrs.put("outerColumns", outer.stream().map(ColumnRead::column).toList());
        attrs.put("innerAliases", inner.stream().map(ColumnRead::alias).toList());
        attrs.put("innerColumns", inner.stream().map(ColumnRead::column).toList());
        attrs.put("innerTable", "");
        attrs.put("verifiedColumnSubquery", true);
        add(StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, ctx, attrs);
        return null;
    }

    @Override
    public Void visitLiteralInPredicate(PostgresFullGrammerRoutineBodySqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(PostgresFullGrammerRoutineBodySqlParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitInsertSelectStatement(PostgresFullGrammerRoutineBodySqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        visit(ctx.selectStatement());
        List<PostgresFullGrammerRoutineBodySqlParser.SelectItemContext> selectItems =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            PostgresFullGrammerRoutineBodySqlParser.SelectItemContext item = selectItems.get(index);
            if (item.expression() == null) {
                continue;
            }
            ExpressionAnalysis source = analyze(item.expression());
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "INSERT_SELECT");
            add(StructuredParseEventType.INSERT_SELECT_MAPPING, item, attrs);
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(PostgresFullGrammerRoutineBodySqlParser.UpdateStatementContext ctx) {
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        Map<String, Object> writeTarget = attrs();
        writeTarget.put("qualifiedTable", targetTable);
        writeTarget.put("table", baseName(targetTable));
        if (!targetAlias.isBlank()) {
            writeTarget.put("alias", targetAlias);
        }
        add(StructuredParseEventType.WRITE_TARGET, ctx.tablePrimary(), writeTarget);
        if (ctx.fromClause() != null) {
            visit(ctx.fromClause());
        }
        for (PostgresFullGrammerRoutineBodySqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            ExpressionAnalysis source = analyze(assignment.expression());
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("targetAlias", assignmentAlias);
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumn);
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "UPDATE_SET");
            add(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, attrs);
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return null;
    }

    @Override
    public Void visitMergeStatement(PostgresFullGrammerRoutineBodySqlParser.MergeStatementContext ctx) {
        PostgresFullGrammerRoutineBodySqlParser.TablePrimaryContext target = ctx.tablePrimary(0);
        PostgresFullGrammerRoutineBodySqlParser.TablePrimaryContext source = ctx.tablePrimary(1);
        visit(target);
        String targetAlias = targetAlias(target);
        String targetTable = targetTable(target);
        Map<String, Object> writeTarget = attrs();
        writeTarget.put("qualifiedTable", targetTable);
        writeTarget.put("table", baseName(targetTable));
        if (!targetAlias.isBlank()) {
            writeTarget.put("alias", targetAlias);
        }
        add(StructuredParseEventType.WRITE_TARGET, target, writeTarget);
        visit(source);
        joinKinds.push("MERGE_OR_USING");
        visit(ctx.predicate());
        joinKinds.pop();
        for (PostgresFullGrammerRoutineBodySqlParser.MergeWhenClauseContext whenClause : ctx.mergeWhenClause()) {
            PostgresFullGrammerRoutineBodySqlParser.MergeActionContext action = whenClause.mergeAction();
            if (action instanceof PostgresFullGrammerRoutineBodySqlParser.MergeUpdateActionContext updateAction) {
                emitMergeUpdateMappings(updateAction.assignmentList(), targetAlias, targetTable);
            } else if (action instanceof PostgresFullGrammerRoutineBodySqlParser.MergeInsertActionContext insertAction) {
                emitMergeInsertMappings(insertAction, targetAlias, targetTable);
            }
        }
        return null;
    }

    private void emitMergeUpdateMappings(
            PostgresFullGrammerRoutineBodySqlParser.AssignmentListContext assignments,
            String targetAlias,
            String targetTable
    ) {
        for (PostgresFullGrammerRoutineBodySqlParser.AssignmentContext assignment : assignments.assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            ExpressionAnalysis source = analyze(assignment.expression());
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("targetAlias", assignmentAlias);
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumn);
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "MERGE_UPDATE");
            add(StructuredParseEventType.MERGE_WRITE_MAPPING, assignment, attrs);
        }
    }

    private void emitMergeInsertMappings(
            PostgresFullGrammerRoutineBodySqlParser.MergeInsertActionContext insertAction,
            String targetAlias,
            String targetTable
    ) {
        List<String> targetColumns = insertAction.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        List<PostgresFullGrammerRoutineBodySqlParser.ExpressionContext> expressions = insertAction.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int index = 0; index < count; index++) {
            ExpressionAnalysis source = analyze(expressions.get(index));
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("targetAlias", targetAlias);
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "MERGE_INSERT");
            add(StructuredParseEventType.MERGE_WRITE_MAPPING, insertAction, attrs);
        }
    }

    private void emitProjectionItems(PostgresFullGrammerRoutineBodySqlParser.SelectListContext ctx, ProjectionOwner owner) {
        List<PostgresFullGrammerRoutineBodySqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            PostgresFullGrammerRoutineBodySqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) {
                continue;
            }
            ExpressionAnalysis source = analyze(item.expression());
            if (source.sources().isEmpty()) {
                continue;
            }
            String outputColumn = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            Map<String, Object> attrs = attrs();
            attrs.put("outputAlias", owner.alias());
            attrs.put("outputColumn", outputColumn);
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            add(StructuredParseEventType.PROJECTION_ITEM, item, attrs);
        }
    }

    private String outputColumn(PostgresFullGrammerRoutineBodySqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    private ColumnRead singleSelectColumn(PostgresFullGrammerRoutineBodySqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private List<ColumnRead> selectColumns(PostgresFullGrammerRoutineBodySqlParser.SelectStatementContext select) {
        List<PostgresFullGrammerRoutineBodySqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        List<ColumnRead> columns = new ArrayList<>();
        for (PostgresFullGrammerRoutineBodySqlParser.SelectItemContext item : items) {
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

    private ColumnRead singleColumn(PostgresFullGrammerRoutineBodySqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.ColumnExpressionContext columnExpression) {
            List<String> parts = parts(columnExpression.qualifiedName());
            if (parts.size() == 1) {
                return new ColumnRead(defaultColumnAlias(), parts.get(0));
            }
            return new ColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    private ExpressionAnalysis analyze(PostgresFullGrammerRoutineBodySqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression);
            if (column == null) {
                return ExpressionAnalysis.empty();
            }
            return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression());
        }
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.BinaryExpressionContext binary) {
            ExpressionAnalysis left = analyze(binary.expression(0));
            ExpressionAnalysis right = analyze(binary.expression(1));
            LineageTransformType transform = "||".equals(binary.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE, left, right);
        }
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (PostgresFullGrammerRoutineBodySqlParser.ExpressionContext argument : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName())).toLowerCase(Locale.ROOT);
            boolean windowed = function.windowClause() != null;
            LineageTransformType transform = switch (functionName) {
                case "sum" -> windowed ? LineageTransformType.CUMULATIVE : LineageTransformType.AGGREGATE;
                case "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
                case "coalesce" -> LineageTransformType.COALESCE;
                case "concat", "format", "string_agg" -> LineageTransformType.CONCAT_FORMAT;
                default -> LineageTransformType.FUNCTION_CALL;
            };
            LineageTransformType dominant = ExpressionAnalysis.dominant(transform, args.transform());
            LineageFlowKind flowKind = dominant == LineageTransformType.CASE_WHEN
                    ? LineageFlowKind.CONTROL
                    : LineageFlowKind.VALUE;
            return new ExpressionAnalysis(args.sources(), dominant, flowKind);
        }
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.CaseExpressionContext caseExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresFullGrammerRoutineBodySqlParser.CaseWhenClauseContext whenClause : caseExpression.caseWhenClause()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(whenClause.predicate()));
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(whenClause.expression()));
            }
            if (caseExpression.expression().size() > 0) {
                for (PostgresFullGrammerRoutineBodySqlParser.ExpressionContext part : caseExpression.expression()) {
                    combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL,
                            combined,
                            analyze(part));
                }
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (expression instanceof PostgresFullGrammerRoutineBodySqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            return analyzeScalarSubquery(scalarSubquery.selectStatement());
        }
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis analyzeScalarSubquery(PostgresFullGrammerRoutineBodySqlParser.SelectStatementContext select) {
        if (select.withClause() != null) {
            visit(select.withClause());
        }
        PostgresFullGrammerRoutineBodySqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(new QueryScope());
        try {
            if (query.fromClause() != null) {
                visit(query.fromClause());
            }
            if (query.whereClause() != null) {
                visit(query.whereClause());
            }
            if (query.havingClause() != null) {
                visit(query.havingClause());
            }
            List<PostgresFullGrammerRoutineBodySqlParser.SelectItemContext> items = query.selectList().selectItem();
            if (items.size() != 1 || items.get(0).expression() == null) {
                return ExpressionAnalysis.empty();
            }
            return analyze(items.get(0).expression());
        } finally {
            queryScopes.pop();
        }
    }

    private ExpressionAnalysis analyze(PostgresFullGrammerRoutineBodySqlParser.PredicateContext predicate) {
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.AndPredicateContext andPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(andPredicate.predicate(0)),
                    analyze(andPredicate.predicate(1)));
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.OrPredicateContext orPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(orPredicate.predicate(0)),
                    analyze(orPredicate.predicate(1)));
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.NotPredicateContext notPredicate) {
            return analyze(notPredicate.predicate());
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.ParenPredicateContext parenPredicate) {
            return analyze(parenPredicate.predicate());
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.ComparisonPredicateContext comparisonPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(comparisonPredicate.expression(0)),
                    analyze(comparisonPredicate.expression(1)));
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.LikePredicateContext likePredicate) {
            ExpressionAnalysis combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(likePredicate.expression(0)),
                    analyze(likePredicate.expression(1)));
            if (likePredicate.expression().size() > 2) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(likePredicate.expression(2)));
            }
            return combined;
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.LiteralInPredicateContext literalInPredicate) {
            ExpressionAnalysis combined = analyze(literalInPredicate.expression());
            for (PostgresFullGrammerRoutineBodySqlParser.ExpressionContext item : literalInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.InSubqueryPredicateContext inSubqueryPredicate) {
            return analyze(inSubqueryPredicate.expression());
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.TupleInSubqueryPredicateContext tupleInPredicate) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresFullGrammerRoutineBodySqlParser.ExpressionContext item : tupleInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof PostgresFullGrammerRoutineBodySqlParser.ExpressionPredicateContext expressionPredicate) {
            return analyze(expressionPredicate.expression());
        }
        return ExpressionAnalysis.empty();
    }

    private Map<String, Object> attrs() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("tokenEventNative", true);
        return attrs;
    }

    private void add(StructuredParseEventType type, ParserRuleContext ctx, Map<String, Object> attrs) {
        events.add(new StructuredSqlEvent(type, statement.sourceName(), line(ctx), attrs));
    }

    private void collectRoutineBody(String quotedBody, int tokenLine) {
        String body = unquoteDollarBody(quotedBody);
        if (body.isBlank()) {
            return;
        }
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        PostgresFullGrammerRoutineBodySqlLexer lexer = new PostgresFullGrammerRoutineBodySqlLexer(CharStreams.fromString(body));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresFullGrammerRoutineBodySqlParser parser = new PostgresFullGrammerRoutineBodySqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        PostgresFullGrammerRoutineBodySqlParser.ScriptContext root = parser.script();
        tokens.fill();
        if (errors.count() > 0) {
            return;
        }
        SqlStatementRecord nested = new SqlStatementRecord(body,
                statement.sourceType(),
                statement.sourceName(),
                statement.startLine() + Math.max(0, tokenLine - 1),
                statement.startLine() + Math.max(0, tokenLine - 1) + body.lines().count(),
                statement.attributes());
        events.addAll(new PostgresFullGrammerRoutineBodyParseTreeVisitor(nested).collect(root));
    }

    private String unquoteDollarBody(String raw) {
        if (raw == null || raw.length() < 4 || raw.charAt(0) != '$') {
            return "";
        }
        int endTagStart = raw.indexOf('$', 1);
        if (endTagStart < 0) {
            return "";
        }
        String tag = raw.substring(0, endTagStart + 1);
        if (!raw.endsWith(tag) || raw.length() < tag.length() * 2) {
            return "";
        }
        return raw.substring(tag.length(), raw.length() - tag.length());
    }

    private long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }

    private String targetAlias(PostgresFullGrammerRoutineBodySqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    private String rowsetAlias(PostgresFullGrammerRoutineBodySqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.NamedTablePrimaryContext named) {
            if (named.tableAlias() != null) {
                return clean(named.tableAlias().identifier().getText());
            }
            return baseName(qualifiedName(named.qualifiedName()));
        }
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.RowsFromTablePrimaryContext rows && rows.tableAlias() != null) {
            return clean(rows.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.FunctionRowsetPrimaryContext function && function.tableAlias() != null) {
            return clean(function.tableAlias().identifier().getText());
        }
        return "";
    }

    private String targetTable(PostgresFullGrammerRoutineBodySqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.RowsFromTablePrimaryContext rows && rows.tableAlias() != null) {
            return clean(rows.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresFullGrammerRoutineBodySqlParser.FunctionRowsetPrimaryContext function && function.tableAlias() != null) {
            return clean(function.tableAlias().identifier().getText());
        }
        return "";
    }

    private String qualifiedName(PostgresFullGrammerRoutineBodySqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    private List<String> parts(PostgresFullGrammerRoutineBodySqlParser.QualifiedNameContext ctx) {
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("`") && trimmed.endsWith("`")))) {
            return trimmed.substring(1, trimmed.length() - 1).replace(trimmed.substring(0, 1) + trimmed.substring(0, 1),
                    trimmed.substring(0, 1));
        }
        return trimmed;
    }

    private String normalize(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private String baseName(String qualified) {
        if (qualified == null) {
            return "";
        }
        int dot = qualified.lastIndexOf('.');
        return dot < 0 ? qualified : qualified.substring(dot + 1);
    }

    private String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    private String joinKind(PostgresFullGrammerRoutineBodySqlParser.JoinClauseContext join) {
        if (join.joinType() == null) {
            return "JOIN";
        }
        String text = join.joinType().getText().toUpperCase(Locale.ROOT);
        if (text.startsWith("LEFT")) {
            return "LEFT_JOIN";
        }
        if (text.startsWith("RIGHT")) {
            return "RIGHT_JOIN";
        }
        if (text.startsWith("FULL")) {
            return "FULL_JOIN";
        }
        if (text.startsWith("CROSS")) {
            return "CROSS_JOIN";
        }
        return "JOIN";
    }

    private void registerCurrentRowset(String alias) {
        if (alias == null || alias.isBlank() || queryScopes.isEmpty()) {
            return;
        }
        queryScopes.peek().rowsetAliases().add(alias);
    }

    private String defaultColumnAlias() {
        if (queryScopes.isEmpty()) {
            return "";
        }
        Set<String> aliases = queryScopes.peek().rowsetAliases();
        return aliases.size() == 1 ? aliases.iterator().next() : "";
    }

    private record QueryScope(Set<String> rowsetAliases) {
        QueryScope() {
            this(new LinkedHashSet<>());
        }
    }

    private record ProjectionOwner(String alias, List<String> columns) {
    }

    private record ColumnRead(String alias, String column) {
    }

    private record ExpressionAnalysis(
            List<ColumnRead> sources,
            LineageTransformType transform,
            LineageFlowKind flowKind
    ) {
        static ExpressionAnalysis empty() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }

        static ExpressionAnalysis of(ColumnRead column, LineageTransformType transform, LineageFlowKind flowKind) {
            return new ExpressionAnalysis(List.of(column), transform, flowKind);
        }

        static ExpressionAnalysis combine(
                LineageTransformType transform,
                LineageFlowKind flowKind,
                ExpressionAnalysis left,
                ExpressionAnalysis right
        ) {
            List<ColumnRead> sources = new ArrayList<>();
            sources.addAll(left.sources());
            sources.addAll(right.sources());
            return new ExpressionAnalysis(sources.stream().distinct().toList(),
                    dominant(transform, left.transform(), right.transform()), flowKind);
        }

        static LineageTransformType dominant(LineageTransformType... transforms) {
            LineageTransformType dominant = LineageTransformType.DIRECT;
            for (LineageTransformType transform : transforms) {
                if (priority(transform) > priority(dominant)) {
                    dominant = transform;
                }
            }
            return dominant;
        }

        private static int priority(LineageTransformType transform) {
            return switch (transform) {
                case CASE_WHEN -> 8;
                case CUMULATIVE -> 7;
                case AGGREGATE -> 6;
                case WINDOW_DERIVED -> 5;
                case COALESCE -> 4;
                case CONCAT_FORMAT -> 3;
                case ARITHMETIC -> 2;
                case FUNCTION_CALL -> 1;
                default -> 0;
            };
        }

        List<String> aliases() {
            return sources.stream().map(ColumnRead::alias).toList();
        }

        List<String> columns() {
            return sources.stream().map(ColumnRead::column).toList();
        }
    }
}
