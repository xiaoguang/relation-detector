package com.relationdetector.mysql.tokenevent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.tokenevent.TokenEventEventEmitter;
import com.relationdetector.mysql.tokenevent.MySqlRelationSqlBaseVisitor;
import com.relationdetector.mysql.tokenevent.MySqlRelationSqlParser;

/**
 * Parse-tree visitor for the MySQL token-event structural grammar.
 *
 * <p>CN: 本 visitor 只从 {@code MySqlRelationSql.g4} 的 typed context 生成
 * SQL 结构事件。它可以读取 identifier 原文和 source location，但不通过 regex、
 * token span scanner 或特殊表/列名判断 SQL 结构。
 *
 * <p>EN: Parse-tree visitor for the MySQL token-event grammar. It emits
 * structural events from typed grammar contexts, using token text only for
 * identifier spelling and source locations.
 */
public final class MySqlTokenEventParseTreeVisitor extends MySqlRelationSqlBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final TokenEventEventEmitter emitter;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private int existsDepth;

    public MySqlTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        this.statement = statement;
        this.emitter = new TokenEventEventEmitter(statement);
    }

    public List<StructuredSqlEvent> collect(MySqlRelationSqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitCommonTableExpression(MySqlRelationSqlParser.CommonTableExpressionContext ctx) {
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
    public Void visitQuerySpecification(MySqlRelationSqlParser.QuerySpecificationContext ctx) {
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
            emitProjectionItems(ctx.selectList(), projectionOwners.peek(), singleProjectionQualifier(ctx.fromClause()));
        } else {
            visit(ctx.selectList());
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(MySqlRelationSqlParser.NamedTablePrimaryContext ctx) {
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
        return null;
    }

    @Override
    public Void visitTableReference(MySqlRelationSqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (MySqlRelationSqlParser.JoinClauseContext join : ctx.joinClause()) {
            String rightAlias = rowsetAlias(join.tablePrimary());
            visit(join.tablePrimary());
            if (join.identifierList() != null && join.usingAlias() == null) {
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
    public Void visitDerivedTablePrimary(MySqlRelationSqlParser.DerivedTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) {
            Map<String, Object> attrs = attrs();
            attrs.put("name", alias);
            attrs.put("table", alias);
            attrs.put("qualifiedTable", alias);
            add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
            projectionOwners.push(new ProjectionOwner(alias, List.of()));
            visit(ctx.selectStatement());
            projectionOwners.pop();
        } else {
            visit(ctx.selectStatement());
        }
        return null;
    }

    @Override
    public Void visitJsonTablePrimary(MySqlRelationSqlParser.JsonTablePrimaryContext ctx) {
        Map<String, Object> attrs = attrs();
        attrs.put("name", "JSON_TABLE");
        attrs.put("table", "JSON_TABLE");
        attrs.put("qualifiedTable", "JSON_TABLE");
        if (ctx.tableAlias() != null) {
            attrs.put("alias", clean(ctx.tableAlias().identifier().getText()));
        }
        add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
        return null;
    }

    @Override
    public Void visitOdbcTablePrimary(MySqlRelationSqlParser.OdbcTablePrimaryContext ctx) {
        visit(ctx.tableReference());
        return null;
    }

    @Override
    public Void visitComparisonPredicate(MySqlRelationSqlParser.ComparisonPredicateContext ctx) {
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
    public Void visitExistsPredicate(MySqlRelationSqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(MySqlRelationSqlParser.InSubqueryPredicateContext ctx) {
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
    public Void visitTupleInSubqueryPredicate(MySqlRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
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
    public Void visitLiteralInPredicate(MySqlRelationSqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(MySqlRelationSqlParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitInsertSelectStatement(MySqlRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        visit(ctx.selectStatement());
        List<MySqlRelationSqlParser.SelectItemContext> selectItems =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            MySqlRelationSqlParser.SelectItemContext item = selectItems.get(index);
            for (ExpressionAnalysis source : writeAnalyses(item, "")) {
                emitWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, item, "", targetTable,
                        targetColumns.get(index), source, "INSERT_SELECT");
            }
        }
        return null;
    }

    @Override
    public Void visitCreateTableStatement(MySqlRelationSqlParser.CreateTableStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        ddlTables.push(table);
        for (MySqlRelationSqlParser.TableElementContext element : ctx.tableElement()) {
            visit(element);
        }
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(MySqlRelationSqlParser.AlterTableStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        ddlTables.push(table);
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(MySqlRelationSqlParser.TableForeignKeyContext ctx) {
        addForeignKeyEvents(ctx, currentDdlTable(), identifiers(ctx.identifierList(0)), qualifiedName(ctx.qualifiedName()),
                identifiers(ctx.identifierList(1)));
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(MySqlRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        }
        return null;
    }

    @Override
    public Void visitUniqueConstraint(MySqlRelationSqlParser.UniqueConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        }
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(MySqlRelationSqlParser.TableIndexConstraintContext ctx) {
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(currentDdlTable(), column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        }
        return null;
    }

    @Override
    public Void visitColumnDefinition(MySqlRelationSqlParser.ColumnDefinitionContext ctx) {
        String column = clean(ctx.identifier().getText());
        String table = currentDdlTable();
        addDdlColumnEvent(table, column, ctx);
        for (MySqlRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            MySqlRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
            if (constraint == null) {
                continue;
            }
            if (constraint.PRIMARY() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (constraint.UNIQUE() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (constraint.REFERENCES() != null) {
                List<String> targetColumns = constraint.identifierList() == null
                        ? List.of("id")
                        : identifiers(constraint.identifierList());
                addForeignKeyEvents(ctx, table, List.of(column), qualifiedName(constraint.qualifiedName()), targetColumns);
            }
        }
        return null;
    }

    @Override
    public Void visitCreateIndexStatement(MySqlRelationSqlParser.CreateIndexStatementContext ctx) {
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(qualifiedName(ctx.qualifiedName()), column, role, kind, ctx);
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(MySqlRelationSqlParser.UpdateStatementContext ctx) {
        if (ctx.withClause() != null) {
            visit(ctx.withClause());
        }
        for (MySqlRelationSqlParser.TableReferenceContext tableReference : ctx.tableReference()) {
            visit(tableReference);
        }
        MySqlRelationSqlParser.TablePrimaryContext targetPrimary = firstTablePrimary(ctx.tableReference());
        String targetAlias = targetPrimary == null ? "" : targetAlias(targetPrimary);
        String targetTable = targetPrimary == null ? "" : targetTable(targetPrimary);
        Map<String, Object> writeTarget = attrs();
        writeTarget.put("qualifiedTable", targetTable);
        writeTarget.put("table", baseName(targetTable));
        if (!targetAlias.isBlank()) {
            writeTarget.put("alias", targetAlias);
        }
        add(StructuredParseEventType.WRITE_TARGET, targetPrimary == null ? ctx : targetPrimary, writeTarget);
        for (MySqlRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression(), "")) {
                emitWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, assignmentAlias, targetTable,
                        targetColumn, source, "UPDATE_SET");
            }
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return null;
    }

    private void emitProjectionItems(
            MySqlRelationSqlParser.SelectListContext ctx,
            ProjectionOwner owner,
            String defaultQualifier
    ) {
        List<MySqlRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            MySqlRelationSqlParser.SelectItemContext item = items.get(index);
            ExpressionAnalysis source = analyzeSelectItem(item, defaultQualifier);
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

    private MySqlRelationSqlParser.TablePrimaryContext firstTablePrimary(
            List<MySqlRelationSqlParser.TableReferenceContext> tableReferences
    ) {
        if (tableReferences == null || tableReferences.isEmpty()) {
            return null;
        }
        MySqlRelationSqlParser.TablePrimaryContext primary = tableReferences.get(0).tablePrimary();
        if (primary instanceof MySqlRelationSqlParser.OdbcTablePrimaryContext odbc) {
            return firstTablePrimary(List.of(odbc.tableReference()));
        }
        return primary;
    }

    private String outputColumn(MySqlRelationSqlParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    private ExpressionAnalysis analyzeSelectItem(
            MySqlRelationSqlParser.SelectItemContext item,
            String defaultQualifier
    ) {
        if (item.expression() != null) {
            return analyze(item.expression(), defaultQualifier);
        }
        if (item.booleanSelectExpression() != null) {
            return analyze(item.booleanSelectExpression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    private List<ExpressionAnalysis> writeAnalyses(
            MySqlRelationSqlParser.SelectItemContext item,
            String defaultQualifier
    ) {
        if (item.expression() != null) {
            return writeAnalyses(item.expression(), defaultQualifier);
        }
        ExpressionAnalysis analysis = analyzeSelectItem(item, defaultQualifier);
        return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
    }

    private List<ExpressionAnalysis> writeAnalyses(
            MySqlRelationSqlParser.ExpressionContext expression,
            String defaultQualifier
    ) {
        List<ExpressionAnalysis> result = new ArrayList<>();
        ExpressionAnalysis value = analyze(expression, defaultQualifier);
        if (containsScalarSubquery(expression)) {
            value = asValue(value);
        }
        if (!value.sources().isEmpty()) {
            result.add(value);
        }
        ExpressionAnalysis control = scalarSubqueryControl(expression, defaultQualifier);
        if (!control.sources().isEmpty()) {
            result.add(control);
        }
        return result;
    }

    private void emitWriteMapping(
            StructuredParseEventType eventType,
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ExpressionAnalysis source,
            String mappingKind
    ) {
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("targetAlias", targetAlias);
        attrs.put("targetTable", targetTable);
        attrs.put("targetColumn", targetColumn);
        attrs.put("sourceAliases", source.aliases());
        attrs.put("sourceColumns", source.columns());
        attrs.put("transformType", source.transform().name());
        attrs.put("flowKind", source.flowKind().name());
        attrs.put("mappingKind", mappingKind);
        add(eventType, ctx, attrs);
    }

    private ColumnRead singleSelectColumn(MySqlRelationSqlParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private List<ColumnRead> selectColumns(MySqlRelationSqlParser.SelectStatementContext select) {
        List<MySqlRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        List<ColumnRead> columns = new ArrayList<>();
        for (MySqlRelationSqlParser.SelectItemContext item : items) {
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

    private ColumnRead singleColumn(MySqlRelationSqlParser.ExpressionContext expression) {
        return singleColumn(expression, "");
    }

    private ColumnRead singleColumn(MySqlRelationSqlParser.ExpressionContext expression, String defaultQualifier) {
        if (expression instanceof MySqlRelationSqlParser.ColumnExpressionContext columnExpression) {
            List<String> parts = parts(columnExpression.qualifiedName());
            if (parts.size() == 1) {
                return new ColumnRead(defaultQualifier, parts.get(0));
            }
            return new ColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (expression instanceof MySqlRelationSqlParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression(), defaultQualifier);
        }
        return null;
    }

    private ExpressionAnalysis analyze(MySqlRelationSqlParser.ExpressionContext expression) {
        return analyze(expression, "");
    }

    private ExpressionAnalysis analyze(MySqlRelationSqlParser.ExpressionContext expression, String defaultQualifier) {
        if (expression instanceof MySqlRelationSqlParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression, defaultQualifier);
            if (column == null) {
                return ExpressionAnalysis.empty();
            }
            return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof MySqlRelationSqlParser.ParenExpressionContext paren) {
            return analyze(paren.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.BinaryExpressionContext binary) {
            ExpressionAnalysis left = analyze(binary.expression(0), defaultQualifier);
            ExpressionAnalysis right = analyze(binary.expression(1), defaultQualifier);
            LineageTransformType transform = "||".equals(binary.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE, left, right);
        }
        if (expression instanceof MySqlRelationSqlParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (MySqlRelationSqlParser.ExpressionContext argument : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument, defaultQualifier));
                }
            }
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName())).toLowerCase(Locale.ROOT);
            LineageTransformType transform = switch (functionName) {
                case "sum", "avg", "count", "min", "max" -> LineageTransformType.AGGREGATE;
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
        if (expression instanceof MySqlRelationSqlParser.CaseExpressionContext caseExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (MySqlRelationSqlParser.CaseWhenClauseContext whenClause : caseExpression.caseWhenClause()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(whenClause.predicate()));
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(whenClause.expression(), defaultQualifier));
            }
            if (caseExpression.expression().size() > 0) {
                for (MySqlRelationSqlParser.ExpressionContext part : caseExpression.expression()) {
                    combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL,
                            combined,
                            analyze(part, defaultQualifier));
                }
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (expression instanceof MySqlRelationSqlParser.IfExpressionContext ifExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(ifExpression.predicate()),
                    analyze(ifExpression.expression(0), defaultQualifier));
            combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    combined,
                    analyze(ifExpression.expression(1), defaultQualifier));
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (expression instanceof MySqlRelationSqlParser.IntervalExpressionContext intervalExpression) {
            return analyze(intervalExpression.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            visit(scalarSubquery.selectStatement());
            ExpressionAnalysis selectedExpression = scalarSubquerySelectExpression(scalarSubquery.selectStatement());
            if (!selectedExpression.sources().isEmpty()) {
                return selectedExpression;
            }
            List<ColumnRead> columns = selectColumns(scalarSubquery.selectStatement());
            if (columns.isEmpty()) {
                return ExpressionAnalysis.empty();
            }
            return new ExpressionAnalysis(columns, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis scalarSubquerySelectExpression(MySqlRelationSqlParser.SelectStatementContext select) {
        List<MySqlRelationSqlParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        if (items.size() != 1 || items.get(0).expression() == null) {
            return ExpressionAnalysis.empty();
        }
        return asValue(analyze(items.get(0).expression(), singleProjectionQualifier(select.querySpecification().fromClause())));
    }

    private ExpressionAnalysis asValue(ExpressionAnalysis analysis) {
        return analysis.sources().isEmpty()
                ? analysis
                : new ExpressionAnalysis(analysis.sources(), analysis.transform(), LineageFlowKind.VALUE);
    }

    private ExpressionAnalysis scalarSubqueryControl(ParseTree expression, String defaultQualifier) {
        if (expression instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext scalarSubquery) {
            return scalarSubqueryContext(scalarSubquery.selectStatement(), defaultQualifier);
        }
        ExpressionAnalysis context = ExpressionAnalysis.emptyControl();
        for (int index = 0; index < expression.getChildCount(); index++) {
            context = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL,
                    context, scalarSubqueryControl(expression.getChild(index), defaultQualifier));
        }
        return context;
    }

    private boolean containsScalarSubquery(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof MySqlRelationSqlParser.ScalarSubqueryExpressionContext) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsScalarSubquery(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAggregateFunction(ParseTree tree) {
        if (tree instanceof MySqlRelationSqlParser.FunctionExpressionContext function) {
            String functionName = baseName(qualifiedName(function.functionCall().qualifiedName())).toLowerCase(Locale.ROOT);
            if (Set.of("sum", "avg", "count", "min", "max").contains(functionName)) {
                return true;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsAggregateFunction(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private ExpressionAnalysis scalarSubqueryContext(MySqlRelationSqlParser.SelectStatementContext select, String defaultQualifier) {
        MySqlRelationSqlParser.QuerySpecificationContext query = select.querySpecification();
        String scalarDefaultQualifier = singleProjectionQualifier(query.fromClause());
        if (scalarDefaultQualifier.isBlank()) {
            scalarDefaultQualifier = defaultQualifier;
        }
        ExpressionAnalysis context = ExpressionAnalysis.empty();
        if (query.fromClause() != null) {
            for (MySqlRelationSqlParser.TableReferenceContext reference : query.fromClause().tableReference()) {
                for (MySqlRelationSqlParser.JoinClauseContext join : reference.joinClause()) {
                    if (join.predicate() != null) {
                        context = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                                LineageFlowKind.CONTROL,
                                context,
                                analyze(join.predicate(), scalarDefaultQualifier));
                    }
                }
            }
        }
        if (query.whereClause() != null) {
            context = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    context,
                    analyze(query.whereClause().predicate(), scalarDefaultQualifier));
        }
        if (query.groupByClause() != null) {
            for (MySqlRelationSqlParser.ExpressionContext expression : query.groupByClause().expressionList().expression()) {
                context = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        context,
                        analyze(expression, scalarDefaultQualifier));
            }
        }
        if (query.havingClause() != null) {
            context = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    context,
                    analyze(query.havingClause().predicate(), scalarDefaultQualifier));
        }
        return context;
    }

    private ExpressionAnalysis analyze(MySqlRelationSqlParser.PredicateContext predicate) {
        return analyze(predicate, "");
    }

    private ExpressionAnalysis analyze(MySqlRelationSqlParser.PredicateContext predicate, String defaultQualifier) {
        if (predicate instanceof MySqlRelationSqlParser.AndPredicateContext andPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(andPredicate.predicate(0), defaultQualifier),
                    analyze(andPredicate.predicate(1), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.OrPredicateContext orPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(orPredicate.predicate(0), defaultQualifier),
                    analyze(orPredicate.predicate(1), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.NotPredicateContext notPredicate) {
            return analyze(notPredicate.predicate(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.ParenPredicateContext parenPredicate) {
            return analyze(parenPredicate.predicate(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.ComparisonPredicateContext comparisonPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(comparisonPredicate.expression(0), defaultQualifier),
                    analyze(comparisonPredicate.expression(1), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.LikePredicateContext likePredicate) {
            ExpressionAnalysis combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(likePredicate.expression(0), defaultQualifier),
                    analyze(likePredicate.expression(1), defaultQualifier));
            if (likePredicate.expression().size() > 2) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(likePredicate.expression(2), defaultQualifier));
            }
            return combined;
        }
        if (predicate instanceof MySqlRelationSqlParser.BetweenPredicateContext betweenPredicate) {
            ExpressionAnalysis lowerBound = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(betweenPredicate.expression(0), defaultQualifier),
                    analyze(betweenPredicate.expression(1), defaultQualifier));
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    lowerBound,
                    analyze(betweenPredicate.expression(2), defaultQualifier));
        }
        if (predicate instanceof MySqlRelationSqlParser.LiteralInPredicateContext literalInPredicate) {
            ExpressionAnalysis combined = analyze(literalInPredicate.expression(), defaultQualifier);
            for (MySqlRelationSqlParser.ExpressionContext item : literalInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item, defaultQualifier));
            }
            return combined;
        }
        if (predicate instanceof MySqlRelationSqlParser.IsNullPredicateContext isNullPredicate) {
            return analyze(isNullPredicate.expression(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.InSubqueryPredicateContext inSubqueryPredicate) {
            return analyze(inSubqueryPredicate.expression(), defaultQualifier);
        }
        if (predicate instanceof MySqlRelationSqlParser.TupleInSubqueryPredicateContext tupleInPredicate) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (MySqlRelationSqlParser.ExpressionContext item : tupleInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item, defaultQualifier));
            }
            return combined;
        }
        if (predicate instanceof MySqlRelationSqlParser.ExpressionPredicateContext expressionPredicate) {
            return analyze(expressionPredicate.expression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis analyze(
            MySqlRelationSqlParser.BooleanSelectExpressionContext expression,
            String defaultQualifier
    ) {
        if (expression instanceof MySqlRelationSqlParser.SelectAndBooleanContext andExpression) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(andExpression.booleanSelectExpression(0), defaultQualifier),
                    analyze(andExpression.booleanSelectExpression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectOrBooleanContext orExpression) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(orExpression.booleanSelectExpression(0), defaultQualifier),
                    analyze(orExpression.booleanSelectExpression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectNotBooleanContext notExpression) {
            return analyze(notExpression.booleanSelectExpression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.SelectComparisonBooleanContext comparisonExpression) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(comparisonExpression.expression(0), defaultQualifier),
                    analyze(comparisonExpression.expression(1), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectLikeBooleanContext likeExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(likeExpression.expression(0), defaultQualifier),
                    analyze(likeExpression.expression(1), defaultQualifier));
            if (likeExpression.expression().size() > 2) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(likeExpression.expression(2), defaultQualifier));
            }
            return combined;
        }
        if (expression instanceof MySqlRelationSqlParser.SelectBetweenBooleanContext betweenExpression) {
            ExpressionAnalysis lowerBound = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(betweenExpression.expression(0), defaultQualifier),
                    analyze(betweenExpression.expression(1), defaultQualifier));
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    lowerBound,
                    analyze(betweenExpression.expression(2), defaultQualifier));
        }
        if (expression instanceof MySqlRelationSqlParser.SelectIsNullBooleanContext isNullExpression) {
            return analyze(isNullExpression.expression(), defaultQualifier);
        }
        if (expression instanceof MySqlRelationSqlParser.SelectParenBooleanContext parenExpression) {
            return analyze(parenExpression.booleanSelectExpression(), defaultQualifier);
        }
        return ExpressionAnalysis.empty();
    }

    private Map<String, Object> attrs() {
        return emitter.attrs();
    }

    private void add(StructuredParseEventType type, ParserRuleContext ctx, Map<String, Object> attrs) {
        emitter.add(events, type, ctx, attrs);
    }

    private void addForeignKeyEvents(
            ParserRuleContext ctx,
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns
    ) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        emitter.addIndexEvent(events, ctx, table, column, role, kind);
    }

    private void addDdlColumnEvent(String table, String column, ParserRuleContext ctx) {
        emitter.addDdlColumnEvent(events, ctx, table, column);
    }

    private String targetAlias(MySqlRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof MySqlRelationSqlParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    private String rowsetAlias(MySqlRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof MySqlRelationSqlParser.NamedTablePrimaryContext named) {
            if (named.tableAlias() != null) {
                return clean(named.tableAlias().identifier().getText());
            }
            return baseName(qualifiedName(named.qualifiedName()));
        }
        if (primary instanceof MySqlRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.JsonTablePrimaryContext json && json.tableAlias() != null) {
            return clean(json.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.OdbcTablePrimaryContext odbc) {
            MySqlRelationSqlParser.TablePrimaryContext nested = firstTablePrimary(List.of(odbc.tableReference()));
            return nested == null ? "" : rowsetAlias(nested);
        }
        return "";
    }

    private String targetTable(MySqlRelationSqlParser.TablePrimaryContext primary) {
        if (primary instanceof MySqlRelationSqlParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof MySqlRelationSqlParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        if (primary instanceof MySqlRelationSqlParser.OdbcTablePrimaryContext odbc) {
            MySqlRelationSqlParser.TablePrimaryContext nested = firstTablePrimary(List.of(odbc.tableReference()));
            return nested == null ? "" : targetTable(nested);
        }
        return "";
    }

    private String singleProjectionQualifier(MySqlRelationSqlParser.FromClauseContext fromClause) {
        if (fromClause == null || fromClause.tableReference().size() != 1) {
            return "";
        }
        MySqlRelationSqlParser.TableReferenceContext reference = fromClause.tableReference(0);
        if (!reference.joinClause().isEmpty()) {
            return "";
        }
        return rowsetAlias(reference.tablePrimary());
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> identifiers(MySqlRelationSqlParser.IdentifierListContext ctx) {
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(MySqlRelationSqlParser.IndexPartListContext ctx) {
        List<String> columns = new ArrayList<>();
        for (MySqlRelationSqlParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) {
                columns.add(clean(part.identifier().getText()));
            }
        }
        return columns;
    }

    private String qualifiedName(MySqlRelationSqlParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    private List<String> parts(MySqlRelationSqlParser.QualifiedNameContext ctx) {
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

    private String joinKind(MySqlRelationSqlParser.JoinClauseContext join) {
        if (join.joinOperator() != null && "STRAIGHT_JOIN".equalsIgnoreCase(join.joinOperator().getText())) {
            return "STRAIGHT_JOIN";
        }
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

        static ExpressionAnalysis emptyControl() {
            return new ExpressionAnalysis(List.of(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
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
