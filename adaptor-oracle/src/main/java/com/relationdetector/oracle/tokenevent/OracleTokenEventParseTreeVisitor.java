package com.relationdetector.oracle.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;
import com.relationdetector.oracle.routine.OracleRoutineScope;
import com.relationdetector.oracle.tokenevent.OracleRelationSqlBaseVisitor;
import com.relationdetector.oracle.tokenevent.OracleRelationSqlParser;

/**
 * Parse-tree visitor for the Oracle token-event structural grammar.
 *
 * <p>CN: 本 visitor 只从 {@code OracleRelationSql.g4} 的 typed context 生成
 * SQL 结构事件。它可以读取 identifier 原文和 source location，但不通过 regex、
 * token span scanner 或特殊表/列名判断 SQL 结构。
 *
 * <p>EN: Parse-tree visitor for the Oracle SQL token-event grammar. It emits
 * structural events from typed grammar contexts, using token text only for
 * identifier spelling and source locations.
 */
public final class OracleTokenEventParseTreeVisitor extends OracleRelationSqlBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final TokenEventEventEmitter emitter;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final OracleRoutineScope routineScope = new OracleRoutineScope();
    private int existsDepth;

    public OracleTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        this.statement = statement;
        this.emitter = new TokenEventEventEmitter(statement);
    }

    public List<StructuredSqlEvent> collect(OracleRelationSqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitRoutineStartStatement(OracleRelationSqlParser.RoutineStartStatementContext ctx) {
        routineScope.enterRoutine();
        return null;
    }

    @Override
    public Void visitBlockEndStatement(OracleRelationSqlParser.BlockEndStatementContext ctx) {
        routineScope.leaveRoutineEnd(ctx.IF() != null
                || ctx.LOOP() != null
                || ctx.WHILE() != null
                || ctx.REPEAT() != null);
        return null;
    }

    @Override
    public Void visitCommonTableExpression(OracleRelationSqlParser.CommonTableExpressionContext ctx) {
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
    public Void visitQuerySpecification(OracleRelationSqlParser.QuerySpecificationContext ctx) {
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
    public Void visitNamedTablePrimary(OracleRelationSqlParser.NamedTablePrimaryContext ctx) {
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
    public Void visitTableReference(OracleRelationSqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (OracleRelationSqlParser.JoinClauseContext join : ctx.joinClause()) {
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
    public Void visitDerivedTablePrimary(OracleRelationSqlParser.DerivedTablePrimaryContext ctx) {
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
    public Void visitComparisonPredicate(OracleRelationSqlParser.ComparisonPredicateContext ctx) {
        if (!"=".equals(ctx.comparisonOperator().getText())) {
            return visitChildren(ctx);
        }
        OracleColumnRead left = singleColumn(ctx.expression(0));
        OracleColumnRead right = singleColumn(ctx.expression(1));
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
    public Void visitExistsPredicate(OracleRelationSqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(OracleRelationSqlParser.InSubqueryPredicateContext ctx) {
        OracleColumnRead outer = singleColumn(ctx.expression());
        OracleColumnRead inner = singleSelectColumn(ctx.selectStatement());
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
    public Void visitTupleInSubqueryPredicate(OracleRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
        List<OracleColumnRead> outer = ctx.expressionList().expression().stream().map(this::singleColumn).toList();
        List<OracleColumnRead> inner = selectColumns(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (outer.isEmpty() || outer.size() != inner.size() || outer.stream().anyMatch(java.util.Objects::isNull)) {
            return null;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("outerAliases", outer.stream().map(OracleColumnRead::alias).toList());
        attrs.put("outerColumns", outer.stream().map(OracleColumnRead::column).toList());
        attrs.put("innerAliases", inner.stream().map(OracleColumnRead::alias).toList());
        attrs.put("innerColumns", inner.stream().map(OracleColumnRead::column).toList());
        attrs.put("innerTable", "");
        attrs.put("verifiedColumnSubquery", true);
        add(StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE, ctx, attrs);
        return null;
    }

    @Override
    public Void visitLiteralInPredicate(OracleRelationSqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(OracleRelationSqlParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitScalarSubqueryExpression(OracleRelationSqlParser.ScalarSubqueryExpressionContext ctx) {
        visit(ctx.selectStatement());
        return null;
    }

    @Override
    public Void visitInsertSelectStatement(OracleRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        visit(ctx.selectStatement());
        List<OracleRelationSqlParser.SelectItemContext> selectItems =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            OracleRelationSqlParser.SelectItemContext item = selectItems.get(index);
            if (item.expression() == null) {
                continue;
            }
            OracleExpressionAnalysis source = analyze(item.expression());
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
    public Void visitUpdateStatement(OracleRelationSqlParser.UpdateStatementContext ctx) {
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        emitWriteTarget(ctx.tablePrimary(), targetAlias, targetTable);
        for (OracleRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            emitAssignmentMapping(assignment, targetAlias, targetTable,
                    StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET");
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return null;
    }

    @Override
    public Void visitMergeStatement(OracleRelationSqlParser.MergeStatementContext ctx) {
        OracleRelationSqlParser.TablePrimaryContext target = ctx.tablePrimary(0);
        OracleRelationSqlParser.TablePrimaryContext source = ctx.tablePrimary(1);
        visit(target);
        visit(source);
        String targetAlias = targetAlias(target);
        String targetTable = targetTable(target);
        emitWriteTarget(target, targetAlias, targetTable);
        visit(ctx.predicate());
        for (OracleRelationSqlParser.MergeWhenClauseContext clause : ctx.mergeWhenClause()) {
            if (clause.mergeAction() instanceof OracleRelationSqlParser.MergeUpdateActionContext updateAction) {
                for (OracleRelationSqlParser.AssignmentContext assignment
                        : updateAction.assignmentList().assignment()) {
                    emitAssignmentMapping(assignment, targetAlias, targetTable,
                            StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE_SET");
                }
            }
        }
        return null;
    }

    @Override
    public Void visitCreateTableStatement(OracleRelationSqlParser.CreateTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        for (OracleRelationSqlParser.TableElementContext element : ctx.tableElement()) {
            visit(element);
        }
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(OracleRelationSqlParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(OracleRelationSqlParser.TableForeignKeyContext ctx) {
        String sourceTable = currentDdlTable();
        List<String> sourceColumns = identifiers(ctx.identifierList(0));
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = identifiers(ctx.identifierList(1));
        addForeignKeyEvents(sourceTable, sourceColumns, targetTable, targetColumns, ctx);
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(OracleRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        }
        return null;
    }

    @Override
    public Void visitUniqueConstraint(OracleRelationSqlParser.UniqueConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        }
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(OracleRelationSqlParser.TableIndexConstraintContext ctx) {
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(currentDdlTable(), column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        }
        return null;
    }

    @Override
    public Void visitColumnDefinition(OracleRelationSqlParser.ColumnDefinitionContext ctx) {
        String table = currentDdlTable();
        String column = clean(ctx.identifier().getText());
        for (OracleRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            OracleRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
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
    public Void visitCreateIndexStatement(OracleRelationSqlParser.CreateIndexStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(table, column, role, kind, ctx);
        }
        return null;
    }

    private void emitProjectionItems(OracleRelationSqlParser.SelectListContext ctx, ProjectionOwner owner) {
        List<OracleRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            OracleRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) {
                continue;
            }
            OracleExpressionAnalysis source = analyze(item.expression());
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

    private void emitWriteTarget(
            OracleRelationSqlParser.TablePrimaryContext target,
            String targetAlias,
            String targetTable
    ) {
        Map<String, Object> writeTarget = attrs();
        writeTarget.put("qualifiedTable", targetTable);
        writeTarget.put("table", baseName(targetTable));
        if (!targetAlias.isBlank()) {
            writeTarget.put("alias", targetAlias);
        }
        add(StructuredParseEventType.WRITE_TARGET, target, writeTarget);
    }

    private void emitAssignmentMapping(
            OracleRelationSqlParser.AssignmentContext assignment,
            String targetAlias,
            String targetTable,
            StructuredParseEventType eventType,
            String mappingKind
    ) {
        List<String> targetParts = parts(assignment.qualifiedName());
        String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
        String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
        OracleExpressionAnalysis source = analyze(assignment.expression());
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("targetAlias", assignmentAlias);
        attrs.put("targetTable", targetTable);
        attrs.put("targetColumn", targetColumn);
        attrs.put("sourceAliases", source.aliases());
        attrs.put("sourceColumns", source.columns());
        attrs.put("transformType", source.transform().name());
        attrs.put("flowKind", source.flowKind().name());
        attrs.put("mappingKind", mappingKind);
        add(eventType, assignment, attrs);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> identifiers(OracleRelationSqlParser.IdentifierListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(OracleRelationSqlParser.IndexPartListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (OracleRelationSqlParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) {
                columns.add(clean(part.identifier().getText()));
            }
        }
        return columns;
    }

    private String outputColumn(OracleRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        OracleColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    private OracleColumnRead singleSelectColumn(OracleRelationSqlParser.SelectStatementContext select) {
        List<OracleColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private List<OracleColumnRead> selectColumns(OracleRelationSqlParser.SelectStatementContext select) {
        queryScopes.push(scopeFor(select.querySpecification()));
        try {
            List<OracleRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
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

    private OracleColumnRead singleColumn(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> parts = parts(columnExpression.qualifiedName());
            if (parts.size() == 1) {
                return new OracleColumnRead(defaultColumnAlias(), parts.get(0));
            }
            return new OracleColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (expression instanceof OracleRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    private OracleExpressionAnalysis analyze(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.ColumnExpressionContext columnExpression) {
            OracleColumnRead column = singleColumn(columnExpression);
            if (column == null) {
                return OracleExpressionAnalysis.empty();
            }
            return OracleExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleRelationSqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression());
        }
        if (expression instanceof OracleRelationSqlParser.BinaryExpressionContext binary) {
            OracleExpressionAnalysis left = analyze(binary.expression(0));
            OracleExpressionAnalysis right = analyze(binary.expression(1));
            LineageTransformType transform = "||".equals(binary.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformType.ARITHMETIC;
            return OracleExpressionAnalysis.withTransform(transform, LineageFlowKind.VALUE, left, right);
        }
        if (expression instanceof OracleRelationSqlParser.UnaryExpressionContext unary) {
            return OracleExpressionAnalysis.withTransform(LineageTransformType.ARITHMETIC, LineageFlowKind.VALUE,
                    analyze(unary.expression()));
        }
        if (expression instanceof OracleRelationSqlParser.FunctionExpressionContext function) {
            OracleExpressionAnalysis args = OracleExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (OracleRelationSqlParser.ExpressionContext argument : function.functionCall().expressionList().expression()) {
                    args = OracleExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName())).toLowerCase(Locale.ROOT);
            if (Set.of("sum", "avg", "count", "min", "max").contains(functionName)) {
                return new OracleExpressionAnalysis(args.sources(), LineageTransformType.AGGREGATE, LineageFlowKind.VALUE);
            }
            if (Set.of("coalesce", "nvl").contains(functionName)) {
                return new OracleExpressionAnalysis(args.sources(), LineageTransformType.COALESCE, LineageFlowKind.VALUE);
            }
            if (Set.of("concat", "format", "string_agg", "listagg").contains(functionName)) {
                return new OracleExpressionAnalysis(args.sources(), LineageTransformType.CONCAT_FORMAT, LineageFlowKind.VALUE);
            }
            LineageTransformType dominant = OracleExpressionAnalysis.dominant(LineageTransformType.FUNCTION_CALL,
                    args.transform());
            LineageFlowKind flowKind = dominant == LineageTransformType.CASE_WHEN
                    ? LineageFlowKind.CONTROL
                    : LineageFlowKind.VALUE;
            return new OracleExpressionAnalysis(args.sources(), dominant, flowKind);
        }
        if (expression instanceof OracleRelationSqlParser.CaseExpressionContext caseExpression) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.CaseWhenClauseContext whenClause : caseExpression.caseWhenClause()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(whenClause.predicate()));
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(whenClause.expression()));
            }
            if (caseExpression.expression().size() > 0) {
                for (OracleRelationSqlParser.ExpressionContext part : caseExpression.expression()) {
                    combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL,
                            combined,
                            analyze(part));
                }
            }
            return new OracleExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (expression instanceof OracleRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            visit(scalarSubquery.selectStatement());
            List<OracleColumnRead> columns = selectColumns(scalarSubquery.selectStatement());
            if (columns.isEmpty()) {
                return OracleExpressionAnalysis.empty();
            }
            return new OracleExpressionAnalysis(columns, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        return OracleExpressionAnalysis.empty();
    }

    private OracleExpressionAnalysis analyze(OracleRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof OracleRelationSqlParser.AndPredicateContext andPredicate) {
            return OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(andPredicate.predicate(0)),
                    analyze(andPredicate.predicate(1)));
        }
        if (predicate instanceof OracleRelationSqlParser.OrPredicateContext orPredicate) {
            return OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(orPredicate.predicate(0)),
                    analyze(orPredicate.predicate(1)));
        }
        if (predicate instanceof OracleRelationSqlParser.NotPredicateContext notPredicate) {
            return analyze(notPredicate.predicate());
        }
        if (predicate instanceof OracleRelationSqlParser.ParenPredicateContext parenPredicate) {
            return analyze(parenPredicate.predicate());
        }
        if (predicate instanceof OracleRelationSqlParser.ComparisonPredicateContext comparisonPredicate) {
            return OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(comparisonPredicate.expression(0)),
                    analyze(comparisonPredicate.expression(1)));
        }
        if (predicate instanceof OracleRelationSqlParser.LikePredicateContext likePredicate) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(likePredicate.expression(0)),
                    analyze(likePredicate.expression(1)));
            if (likePredicate.expression().size() > 2) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(likePredicate.expression(2)));
            }
            return combined;
        }
        if (predicate instanceof OracleRelationSqlParser.LiteralInPredicateContext literalInPredicate) {
            OracleExpressionAnalysis combined = analyze(literalInPredicate.expression());
            for (OracleRelationSqlParser.ExpressionContext item : literalInPredicate.expressionList().expression()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof OracleRelationSqlParser.InSubqueryPredicateContext inSubqueryPredicate) {
            return analyze(inSubqueryPredicate.expression());
        }
        if (predicate instanceof OracleRelationSqlParser.TupleInSubqueryPredicateContext tupleInPredicate) {
            OracleExpressionAnalysis combined = OracleExpressionAnalysis.empty();
            for (OracleRelationSqlParser.ExpressionContext item : tupleInPredicate.expressionList().expression()) {
                combined = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof OracleRelationSqlParser.ExpressionPredicateContext expressionPredicate) {
            return analyze(expressionPredicate.expression());
        }
        return OracleExpressionAnalysis.empty();
    }

    private Map<String, Object> attrs() {
        return emitter.attrs();
    }

    private void add(StructuredParseEventType type, ParserRuleContext ctx, Map<String, Object> attrs) {
        emitter.add(events, type, ctx, attrs);
    }

    private String targetAlias(OracleRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof OracleRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    private String rowsetAlias(OracleRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof OracleRelationSqlParser.NamedTablePrimaryContext named) {
            if (named.tableAlias() != null) {
                return clean(named.tableAlias().identifier().getText());
            }
            return baseName(qualifiedName(named.qualifiedName()));
        }
        if (primary instanceof OracleRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        return "";
    }

    private String targetTable(OracleRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof OracleRelationSqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof OracleRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        return "";
    }

    private String qualifiedName(OracleRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    private List<String> parts(OracleRelationSqlParser.QualifiedNameContext ctx) {
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

    private void registerCurrentRowset(String alias) {
        if (alias == null || alias.isBlank() || queryScopes.isEmpty()) {
            return;
        }
        queryScopes.peek().rowsetAliases().add(alias);
    }

    private QueryScope scopeFor(OracleRelationSqlParser.QuerySpecificationContext query) {
        QueryScope scope = new QueryScope();
        if (query == null || query.fromClause() == null) {
            return scope;
        }
        for (OracleRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
            addScopeAlias(scope, rowsetAlias(reference.tablePrimary()));
            for (OracleRelationSqlParser.JoinClauseContext join : reference.joinClause()) {
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

    private String joinKind(OracleRelationSqlParser.JoinClauseContext join) {
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

    private record OracleColumnRead(String alias, String column) {
    }

    private record OracleExpressionAnalysis(
            List<OracleColumnRead> sources,
            LineageTransformType transform,
            LineageFlowKind flowKind
    ) {
        private static OracleExpressionAnalysis empty() {
            return new OracleExpressionAnalysis(List.of(), LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }

        private static OracleExpressionAnalysis of(
                OracleColumnRead column,
                LineageTransformType transform,
                LineageFlowKind flowKind
        ) {
            return new OracleExpressionAnalysis(List.of(column), transform, flowKind);
        }

        private static OracleExpressionAnalysis combine(
                LineageTransformType transform,
                LineageFlowKind flowKind,
                OracleExpressionAnalysis left,
                OracleExpressionAnalysis right
        ) {
            List<OracleColumnRead> sources = new ArrayList<>();
            sources.addAll(left.sources());
            sources.addAll(right.sources());
            return new OracleExpressionAnalysis(sources.stream().distinct().toList(),
                    dominant(transform, left.transform(), right.transform()), flowKind);
        }

        private static OracleExpressionAnalysis withTransform(
                LineageTransformType transform,
                LineageFlowKind flowKind,
                OracleExpressionAnalysis... analyses
        ) {
            List<OracleColumnRead> sources = new ArrayList<>();
            for (OracleExpressionAnalysis analysis : analyses) {
                sources.addAll(analysis.sources());
            }
            return new OracleExpressionAnalysis(sources.stream().distinct().toList(), transform, flowKind);
        }

        private static LineageTransformType dominant(LineageTransformType... transforms) {
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

        private List<String> aliases() {
            return sources.stream().map(OracleColumnRead::alias).toList();
        }

        private List<String> columns() {
            return sources.stream().map(OracleColumnRead::column).toList();
        }
    }

    private record ProjectionOwner(String alias, List<String> columns) {
    }

    private record QueryScope(Set<String> rowsetAliases) {
        private QueryScope() {
            this(new LinkedHashSet<>());
        }
    }

}
