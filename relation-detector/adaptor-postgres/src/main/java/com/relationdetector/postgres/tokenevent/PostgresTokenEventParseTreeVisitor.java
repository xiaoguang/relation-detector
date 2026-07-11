package com.relationdetector.postgres.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlBaseVisitor;
import com.relationdetector.postgres.tokenevent.PostgresRelationSqlParser;
import com.relationdetector.postgres.routine.PostgresRoutineBodyParser;

/**
 * Parse-tree visitor for the PostgreSQL token-event structural grammar.
 *
 * <p>CN: 本 visitor 只从 {@code PostgresRelationSql.g4} 的 typed context 生成
 * SQL 结构事件。它可以读取 identifier 原文和 source location，但不通过 regex、
 * token span scanner 或特殊表/列名判断 SQL 结构。
 *
 * <p>EN: Parse-tree visitor for the PostgreSQL token-event grammar. It emits
 * structural events from typed grammar contexts, using token text only for
 * identifier spelling and source locations.
 */
public final class PostgresTokenEventParseTreeVisitor extends PostgresRelationSqlBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final TokenEventEventEmitter emitter;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    private int existsDepth;

    public PostgresTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        this.statement = statement;
        this.emitter = new TokenEventEventEmitter(statement);
    }

    public List<StructuredSqlEvent> collect(PostgresRelationSqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        if (node.getSymbol() != null
                && node.getSymbol().getType() == PostgresRelationSqlParser.DOLLAR_QUOTED_STRING) {
            collectRoutineBody(node.getText(), node.getSymbol().getLine());
        }
        return null;
    }

    @Override
    public Void visitCommonTableExpression(PostgresRelationSqlParser.CommonTableExpressionContext ctx) {
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
    public Void visitQuerySpecification(PostgresRelationSqlParser.QuerySpecificationContext ctx) {
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
            } else {
                visit(ctx.selectList());
            }
        } finally {
            queryScopes.pop();
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(PostgresRelationSqlParser.NamedTablePrimaryContext ctx) {
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
    public Void visitTableReference(PostgresRelationSqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (PostgresRelationSqlParser.JoinClauseContext join : ctx.joinClause()) {
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
    public Void visitDerivedTablePrimary(PostgresRelationSqlParser.DerivedTablePrimaryContext ctx) {
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
    public Void visitRowsFromTablePrimary(PostgresRelationSqlParser.RowsFromTablePrimaryContext ctx) {
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
    public Void visitFunctionRowsetPrimary(PostgresRelationSqlParser.FunctionRowsetPrimaryContext ctx) {
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
    public Void visitComparisonPredicate(PostgresRelationSqlParser.ComparisonPredicateContext ctx) {
        if (!"=".equals(ctx.comparisonOperator().getText())) {
            return visitChildren(ctx);
        }
        emitColumnEquality(ctx.expression(0), ctx.expression(1), ctx);
        return null;
    }

    @Override
    public Void visitIsNotDistinctPredicate(PostgresRelationSqlParser.IsNotDistinctPredicateContext ctx) {
        emitColumnEquality(ctx.expression(0), ctx.expression(1), ctx);
        return null;
    }

    private void emitColumnEquality(
            PostgresRelationSqlParser.ExpressionContext leftExpression,
            PostgresRelationSqlParser.ExpressionContext rightExpression,
            ParserRuleContext ctx
    ) {
        ColumnRead left = singleColumn(leftExpression);
        ColumnRead right = singleColumn(rightExpression);
        if (left == null || right == null) {
            return;
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
    }

    @Override
    public Void visitExistsPredicate(PostgresRelationSqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(PostgresRelationSqlParser.InSubqueryPredicateContext ctx) {
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
    public Void visitTupleInSubqueryPredicate(PostgresRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
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
    public Void visitLiteralInPredicate(PostgresRelationSqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(PostgresRelationSqlParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitScalarSubqueryExpression(PostgresRelationSqlParser.ScalarSubqueryExpressionContext ctx) {
        visit(ctx.selectStatement());
        return null;
    }

    @Override
    public Void visitInsertSelectStatement(PostgresRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        visit(ctx.selectStatement());
        List<PostgresRelationSqlParser.SelectItemContext> selectItems =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            PostgresRelationSqlParser.SelectItemContext item = selectItems.get(index);
            if (item.expression() == null) {
                continue;
            }
            for (ExpressionAnalysis source : writeAnalyses(item.expression())) {
                addWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, item, "", targetTable,
                        targetColumns.get(index), source, "INSERT_SELECT");
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(PostgresRelationSqlParser.UpdateStatementContext ctx) {
        if (ctx.withClause() != null) {
            visit(ctx.withClause());
        }
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
        for (PostgresRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression())) {
                addWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, assignmentAlias,
                        targetTable, targetColumn, source, "UPDATE_SET");
            }
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return null;
    }

    @Override
    public Void visitMergeStatement(PostgresRelationSqlParser.MergeStatementContext ctx) {
        if (ctx.withClause() != null) {
            visit(ctx.withClause());
        }
        PostgresRelationSqlParser.TablePrimaryContext target = ctx.tablePrimary(0);
        PostgresRelationSqlParser.TablePrimaryContext source = ctx.tablePrimary(1);
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
        for (PostgresRelationSqlParser.MergeWhenClauseContext whenClause : ctx.mergeWhenClause()) {
            PostgresRelationSqlParser.MergeActionContext action = whenClause.mergeAction();
            if (action instanceof PostgresRelationSqlParser.MergeUpdateActionContext updateAction) {
                emitMergeUpdateMappings(updateAction.assignmentList(), targetAlias, targetTable);
            } else if (action instanceof PostgresRelationSqlParser.MergeInsertActionContext insertAction) {
                emitMergeInsertMappings(insertAction, targetAlias, targetTable);
            }
        }
        return null;
    }

    @Override
    public Void visitCreateTableStatement(PostgresRelationSqlParser.CreateTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        for (PostgresRelationSqlParser.TableElementContext element : ctx.tableElement()) {
            visit(element);
        }
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(PostgresRelationSqlParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(PostgresRelationSqlParser.TableForeignKeyContext ctx) {
        String sourceTable = currentDdlTable();
        List<String> sourceColumns = identifiers(ctx.identifierList(0));
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = identifiers(ctx.identifierList(1));
        addForeignKeyEvents(sourceTable, sourceColumns, targetTable, targetColumns, ctx);
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(PostgresRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        }
        return null;
    }

    @Override
    public Void visitUniqueConstraint(PostgresRelationSqlParser.UniqueConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        }
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(PostgresRelationSqlParser.TableIndexConstraintContext ctx) {
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(currentDdlTable(), column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        }
        return null;
    }

    @Override
    public Void visitColumnDefinition(PostgresRelationSqlParser.ColumnDefinitionContext ctx) {
        String table = currentDdlTable();
        String column = clean(ctx.identifier().getText());
        emitter.addDdlColumnEvent(events, ctx, table, column);
        for (PostgresRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            PostgresRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
            if (constraint == null) {
                continue;
            }
            if (constraint.PRIMARY() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (constraint.UNIQUE() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (constraint.REFERENCES() != null) {
                addForeignKeyEvents(table,
                        List.of(column),
                        qualifiedName(constraint.qualifiedName()),
                        identifiers(constraint.identifierList()),
                        constraint);
            }
        }
        return null;
    }

    @Override
    public Void visitCreateIndexStatement(PostgresRelationSqlParser.CreateIndexStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(table, column, role, kind, ctx);
        }
        return null;
    }

    private void emitMergeUpdateMappings(
            PostgresRelationSqlParser.AssignmentListContext assignments,
            String targetAlias,
            String targetTable
    ) {
        for (PostgresRelationSqlParser.AssignmentContext assignment : assignments.assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression())) {
                addWriteMapping(StructuredParseEventType.MERGE_WRITE_MAPPING, assignment, assignmentAlias,
                        targetTable, targetColumn, source, "MERGE_UPDATE");
            }
        }
    }

    private void emitMergeInsertMappings(
            PostgresRelationSqlParser.MergeInsertActionContext insertAction,
            String targetAlias,
            String targetTable
    ) {
        List<String> targetColumns = insertAction.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        List<PostgresRelationSqlParser.ExpressionContext> expressions = insertAction.expressionList().expression();
        int count = Math.min(targetColumns.size(), expressions.size());
        for (int index = 0; index < count; index++) {
            for (ExpressionAnalysis source : writeAnalyses(expressions.get(index))) {
                addWriteMapping(StructuredParseEventType.MERGE_WRITE_MAPPING, insertAction, targetAlias,
                        targetTable, targetColumns.get(index), source, "MERGE_INSERT");
            }
        }
    }

    private void emitProjectionItems(PostgresRelationSqlParser.SelectListContext ctx, ProjectionOwner owner) {
        List<PostgresRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            PostgresRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) {
                continue;
            }
            String outputColumn = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            for (ExpressionAnalysis source : writeAnalyses(item.expression())) {
                if (source.sources().isEmpty()) {
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
    }

    private void addWriteMapping(
            StructuredParseEventType type,
            ParserRuleContext context,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ExpressionAnalysis source,
            String mappingKind
    ) {
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attributes = attrs();
        if (!targetAlias.isBlank()) {
            attributes.put("targetAlias", targetAlias);
        }
        attributes.put("targetTable", targetTable);
        attributes.put("targetColumn", targetColumn);
        attributes.put("sourceAliases", source.aliases());
        attributes.put("sourceColumns", source.columns());
        attributes.put("transformType", source.transform().name());
        attributes.put("flowKind", source.flowKind().name());
        attributes.put("mappingKind", mappingKind);
        add(type, context, attributes);
    }

    private String outputColumn(PostgresRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    private ColumnRead singleSelectColumn(PostgresRelationSqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private List<ColumnRead> selectColumns(PostgresRelationSqlParser.SelectStatementContext select) {
        queryScopes.push(scopeFor(select.querySpecification()));
        try {
            List<PostgresRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
            List<ColumnRead> columns = new ArrayList<>();
            for (PostgresRelationSqlParser.SelectItemContext item : items) {
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
        } finally {
            queryScopes.pop();
        }
    }

    private ColumnRead singleColumn(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> parts = parts(columnExpression.qualifiedName());
            if (parts.size() == 1) {
                return new ColumnRead(defaultColumnAlias(), parts.get(0));
            }
            return new ColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (expression instanceof PostgresRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    private ExpressionAnalysis analyze(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression);
            if (column == null) {
                return ExpressionAnalysis.empty();
            }
            return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof PostgresRelationSqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression());
        }
        if (expression instanceof PostgresRelationSqlParser.CastExpressionContext cast) {
            return analyze(cast.expression());
        }
        if (expression instanceof PostgresRelationSqlParser.StandardCastExpressionContext cast) {
            return analyze(cast.expression());
        }
        if (expression instanceof PostgresRelationSqlParser.BinaryExpressionContext binary) {
            ExpressionAnalysis left = analyze(binary.expression(0));
            ExpressionAnalysis right = analyze(binary.expression(1));
            LineageTransformType transform = "||".equals(binary.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE, left, right);
        }
        if (expression instanceof PostgresRelationSqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (PostgresRelationSqlParser.ExpressionContext argument : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            if (function.windowClause() != null) {
                args = ExpressionAnalysis.combine(args.transform(),
                        args.flowKind(),
                        args,
                        analyzeWindowClause(function.windowClause()));
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName())).toLowerCase(Locale.ROOT);
            boolean windowed = function.windowClause() != null;
            LineageTransformType transform = switch (functionName) {
                case "sum" -> windowed ? LineageTransformType.CUMULATIVE : LineageTransformType.AGGREGATE;
                case "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
                case "coalesce" -> LineageTransformType.COALESCE;
                case "concat", "format", "string_agg", "to_char" -> LineageTransformType.CONCAT_FORMAT;
                default -> LineageTransformType.FUNCTION_CALL;
            };
            LineageTransformType dominant = LineageTransformClassifier.dominant(transform, args.transform());
            LineageFlowKind flowKind = dominant == LineageTransformType.CASE_WHEN
                    ? LineageFlowKind.CONTROL
                    : LineageFlowKind.VALUE;
            return new ExpressionAnalysis(args.sources(), dominant, flowKind);
        }
        if (expression instanceof PostgresRelationSqlParser.ExtractExpressionContext extract) {
            ExpressionAnalysis source = analyze(extract.expression());
            return new ExpressionAnalysis(source.sources(),
                    LineageTransformClassifier.dominant(LineageTransformType.FUNCTION_CALL, source.transform()),
                    source.flowKind());
        }
        if (expression instanceof PostgresRelationSqlParser.CaseExpressionContext caseExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresRelationSqlParser.CaseWhenClauseContext whenClause : caseExpression.caseWhenClause()) {
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
                for (PostgresRelationSqlParser.ExpressionContext part : caseExpression.expression()) {
                    combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL,
                            combined,
                            analyze(part));
                }
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (expression instanceof PostgresRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            return analyzeScalarSubquery(scalarSubquery.selectStatement());
        }
        if (expression instanceof PostgresRelationSqlParser.ArrayExpressionContext arrayExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            if (arrayExpression.expressionList() != null) {
                for (PostgresRelationSqlParser.ExpressionContext item : arrayExpression.expressionList().expression()) {
                    combined = ExpressionAnalysis.combine(combined.transform(), combined.flowKind(), combined, analyze(item));
                }
            }
            return combined;
        }
        return ExpressionAnalysis.empty();
    }

    private List<ExpressionAnalysis> writeAnalyses(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            return scalarSubqueryWriteAnalyses(scalarSubquery.selectStatement());
        }
        if (!(expression instanceof PostgresRelationSqlParser.CaseExpressionContext caseExpression)) {
            ExpressionAnalysis analysis = analyze(expression);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        ExpressionAnalysis value = ExpressionAnalysis.empty();
        ExpressionAnalysis control = ExpressionAnalysis.empty();
        for (PostgresRelationSqlParser.CaseWhenClauseContext clause : caseExpression.caseWhenClause()) {
            value = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(clause.expression()));
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control, analyze(clause.predicate()));
        }
        List<PostgresRelationSqlParser.ExpressionContext> outerExpressions = caseExpression.expression();
        int selectorCount = outerExpressions.size() - (caseExpression.ELSE() == null ? 0 : 1);
        for (int index = 0; index < selectorCount; index++) {
            control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL, control, analyze(outerExpressions.get(index)));
        }
        if (caseExpression.ELSE() != null && !outerExpressions.isEmpty()) {
            value = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(outerExpressions.get(outerExpressions.size() - 1)));
        }
        List<ExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            result.add(new ExpressionAnalysis(value.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) {
            result.add(new ExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    private List<ExpressionAnalysis> scalarSubqueryWriteAnalyses(
            PostgresRelationSqlParser.SelectStatementContext select
    ) {
        ExpressionAnalysis value = analyzeScalarSubquery(select);
        ExpressionAnalysis control = scalarSubqueryContext(select);
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

    private ExpressionAnalysis scalarSubqueryContext(PostgresRelationSqlParser.SelectStatementContext select) {
        PostgresRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        queryScopes.push(scopeFor(query));
        try {
            ExpressionAnalysis control = ExpressionAnalysis.empty();
            if (query.fromClause() != null) {
                for (PostgresRelationSqlParser.TableReferenceContext table : query.fromClause().tableReference()) {
                    for (PostgresRelationSqlParser.JoinClauseContext join : table.joinClause()) {
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
                for (PostgresRelationSqlParser.ExpressionContext grouping
                        : query.groupByClause().expressionList().expression()) {
                    control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL, control, analyze(grouping));
                }
            }
            if (query.havingClause() != null) {
                control = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(query.havingClause().predicate()));
            }
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
            if (!".".equals(tokens.get(index + 1))) {
                continue;
            }
            String alias = clean(tokens.get(index));
            String column = clean(tokens.get(index + 2));
            if (alias.isBlank() || column.isBlank()) {
                continue;
            }
            columns.add(new ColumnRead(alias, column));
        }
        if (columns.isEmpty()) {
            return ExpressionAnalysis.empty();
        }
        return new ExpressionAnalysis(columns.stream().distinct().toList(),
                LineageTransformType.WINDOW_DERIVED,
                LineageFlowKind.VALUE);
    }

    private void collectLeafText(ParseTree tree, List<String> tokens) {
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            if (text != null && !text.isBlank()) {
                tokens.add(text);
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectLeafText(tree.getChild(index), tokens);
        }
    }

    private ExpressionAnalysis analyzeScalarSubquery(PostgresRelationSqlParser.SelectStatementContext select) {
        if (select.withClause() != null) {
            visit(select.withClause());
        }
        PostgresRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
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
            List<PostgresRelationSqlParser.SelectItemContext> items = query.selectList().selectItem();
            if (items.size() != 1 || items.get(0).expression() == null) {
                return ExpressionAnalysis.empty();
            }
            return analyze(items.get(0).expression());
        } finally {
            queryScopes.pop();
        }
    }

    private ExpressionAnalysis analyze(PostgresRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof PostgresRelationSqlParser.AndPredicateContext andPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(andPredicate.predicate(0)),
                    analyze(andPredicate.predicate(1)));
        }
        if (predicate instanceof PostgresRelationSqlParser.OrPredicateContext orPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(orPredicate.predicate(0)),
                    analyze(orPredicate.predicate(1)));
        }
        if (predicate instanceof PostgresRelationSqlParser.NotPredicateContext notPredicate) {
            return analyze(notPredicate.predicate());
        }
        if (predicate instanceof PostgresRelationSqlParser.ParenPredicateContext parenPredicate) {
            return analyze(parenPredicate.predicate());
        }
        if (predicate instanceof PostgresRelationSqlParser.ComparisonPredicateContext comparisonPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(comparisonPredicate.expression(0)),
                    analyze(comparisonPredicate.expression(1)));
        }
        if (predicate instanceof PostgresRelationSqlParser.LikePredicateContext likePredicate) {
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
        if (predicate instanceof PostgresRelationSqlParser.IsNullPredicateContext isNullPredicate) {
            return analyze(isNullPredicate.expression());
        }
        if (predicate instanceof PostgresRelationSqlParser.LiteralInPredicateContext literalInPredicate) {
            ExpressionAnalysis combined = analyze(literalInPredicate.expression());
            for (PostgresRelationSqlParser.ExpressionContext item : literalInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof PostgresRelationSqlParser.InSubqueryPredicateContext inSubqueryPredicate) {
            return analyze(inSubqueryPredicate.expression());
        }
        if (predicate instanceof PostgresRelationSqlParser.TupleInSubqueryPredicateContext tupleInPredicate) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (PostgresRelationSqlParser.ExpressionContext item : tupleInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof PostgresRelationSqlParser.ExpressionPredicateContext expressionPredicate) {
            return analyze(expressionPredicate.expression());
        }
        return ExpressionAnalysis.empty();
    }

    private Map<String, Object> attrs() {
        return emitter.attrs();
    }

    private void add(StructuredParseEventType type, ParserRuleContext ctx, Map<String, Object> attrs) {
        emitter.add(events, type, ctx, attrs);
    }

    private void collectRoutineBody(String quotedBody, int tokenLine) {
        String body = unquoteDollarBody(quotedBody);
        if (body.isBlank()) {
            return;
        }
        SqlStatementRecord nested = new SqlStatementRecord(body,
                statement.sourceType(),
                statement.sourceName(),
                statement.startLine() + Math.max(0, tokenLine - 1),
                statement.startLine() + Math.max(0, tokenLine - 1) + body.lines().count(),
                statement.attributes());
        events.addAll(PostgresRoutineBodyParser.extract(nested));
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

    private String targetAlias(PostgresRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    private String rowsetAlias(PostgresRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRelationSqlParser.NamedTablePrimaryContext named) {
            if (named.tableAlias() != null) {
                return clean(named.tableAlias().identifier().getText());
            }
            return baseName(qualifiedName(named.qualifiedName()));
        }
        if (primary instanceof PostgresRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.RowsFromTablePrimaryContext rows && rows.tableAlias() != null) {
            return clean(rows.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.FunctionRowsetPrimaryContext function && function.tableAlias() != null) {
            return clean(function.tableAlias().identifier().getText());
        }
        return "";
    }

    private String targetTable(PostgresRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof PostgresRelationSqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof PostgresRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.RowsFromTablePrimaryContext rows && rows.tableAlias() != null) {
            return clean(rows.tableAlias().identifier().getText());
        }
        if (primary instanceof PostgresRelationSqlParser.FunctionRowsetPrimaryContext function && function.tableAlias() != null) {
            return clean(function.tableAlias().identifier().getText());
        }
        return "";
    }

    private String qualifiedName(PostgresRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    private List<String> parts(PostgresRelationSqlParser.QualifiedNameContext ctx) {
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

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> identifiers(PostgresRelationSqlParser.IdentifierListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(PostgresRelationSqlParser.IndexPartListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (PostgresRelationSqlParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) {
                columns.add(clean(part.identifier().getText()));
            }
        }
        return columns;
    }

    private void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            ParserRuleContext ctx
    ) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        emitter.addIndexEvent(events, ctx, table, column, role, kind);
    }

    private String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    private String joinKind(PostgresRelationSqlParser.JoinClauseContext join) {
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

    private QueryScope scopeFor(PostgresRelationSqlParser.QuerySpecificationContext query) {
        QueryScope scope = new QueryScope();
        if (query == null || query.fromClause() == null) {
            return scope;
        }
        for (PostgresRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
            addScopeAlias(scope, rowsetAlias(reference.tablePrimary()));
            for (PostgresRelationSqlParser.JoinClauseContext join : reference.joinClause()) {
                addScopeAlias(scope, rowsetAlias(join.tablePrimary()));
            }
        }
        return scope;
    }

    private void addScopeAlias(QueryScope scope, String alias) {
        if (alias != null && !alias.isBlank()) {
            scope.rowsetAliases().add(alias);
        }
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
                    LineageTransformClassifier.dominantForFlow(
                            flowKind, transform, left.transform(), right.transform()), flowKind);
        }

        List<String> aliases() {
            return sources.stream().map(ColumnRead::alias).toList();
        }

        List<String> columns() {
            return sources.stream().map(ColumnRead::column).toList();
        }
    }
}
