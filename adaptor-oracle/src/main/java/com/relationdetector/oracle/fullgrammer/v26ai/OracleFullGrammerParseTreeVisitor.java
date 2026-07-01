package com.relationdetector.oracle.fullgrammer.v26ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * Parse-tree visitor for the Oracle v26ai scoped full-grammer.
 *
 * <p>CN: 本 visitor 只从 {@code OracleFullGrammerParser.g4} 的 typed context 生成
 * SQL 结构事件。它可以读取 identifier 原文和 source location，但不通过 regex、
 * token span scanner 或特殊表/列名判断 SQL 结构。
 *
 * <p>EN: Parse-tree visitor for the Oracle v26ai scoped full-grammer. It emits
 * structural events from typed grammar contexts, using token text only for
 * identifier spelling and source locations.
 */
public final class OracleFullGrammerParseTreeVisitor extends OracleFullGrammerParserBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> cteNames = new LinkedHashSet<>();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private int existsDepth;

    public OracleFullGrammerParseTreeVisitor(SqlStatementRecord statement) {
        this.statement = statement;
    }

    public List<StructuredSqlEvent> collect(OracleFullGrammerParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitCommonTableExpression(OracleFullGrammerParser.CommonTableExpressionContext ctx) {
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
    public Void visitQuerySpecification(OracleFullGrammerParser.QuerySpecificationContext ctx) {
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
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(OracleFullGrammerParser.NamedTablePrimaryContext ctx) {
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
    public Void visitTableReference(OracleFullGrammerParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (OracleFullGrammerParser.JoinClauseContext join : ctx.joinClause()) {
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
    public Void visitDerivedTablePrimary(OracleFullGrammerParser.DerivedTablePrimaryContext ctx) {
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
    public Void visitComparisonPredicate(OracleFullGrammerParser.ComparisonPredicateContext ctx) {
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
    public Void visitExistsPredicate(OracleFullGrammerParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(OracleFullGrammerParser.InSubqueryPredicateContext ctx) {
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
    public Void visitTupleInSubqueryPredicate(OracleFullGrammerParser.TupleInSubqueryPredicateContext ctx) {
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
    public Void visitLiteralInPredicate(OracleFullGrammerParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(OracleFullGrammerParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitInsertSelectStatement(OracleFullGrammerParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText()))
                .toList();
        visit(ctx.selectStatement());
        List<OracleFullGrammerParser.SelectItemContext> selectItems =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            OracleFullGrammerParser.SelectItemContext item = selectItems.get(index);
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
    public Void visitUpdateStatement(OracleFullGrammerParser.UpdateStatementContext ctx) {
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        emitWriteTarget(ctx.tablePrimary(), targetAlias, targetTable);
        for (OracleFullGrammerParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            emitAssignmentMapping(assignment, targetAlias, targetTable,
                    StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET");
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return null;
    }

    @Override
    public Void visitMergeStatement(OracleFullGrammerParser.MergeStatementContext ctx) {
        OracleFullGrammerParser.TablePrimaryContext target = ctx.tablePrimary(0);
        OracleFullGrammerParser.TablePrimaryContext source = ctx.tablePrimary(1);
        visit(target);
        visit(source);
        String targetAlias = targetAlias(target);
        String targetTable = targetTable(target);
        emitWriteTarget(target, targetAlias, targetTable);
        visit(ctx.predicate());
        for (OracleFullGrammerParser.MergeWhenClauseContext clause : ctx.mergeWhenClause()) {
            if (clause.mergeAction() instanceof OracleFullGrammerParser.MergeUpdateActionContext updateAction) {
                for (OracleFullGrammerParser.AssignmentContext assignment
                        : updateAction.assignmentList().assignment()) {
                    emitAssignmentMapping(assignment, targetAlias, targetTable,
                            StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE_SET");
                }
            }
        }
        return null;
    }

    @Override
    public Void visitCreateTableStatement(OracleFullGrammerParser.CreateTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        for (OracleFullGrammerParser.TableElementContext element : ctx.tableElement()) {
            visit(element);
        }
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(OracleFullGrammerParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(OracleFullGrammerParser.TableForeignKeyContext ctx) {
        String sourceTable = currentDdlTable();
        List<String> sourceColumns = identifiers(ctx.identifierList(0));
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = identifiers(ctx.identifierList(1));
        addForeignKeyEvents(sourceTable, sourceColumns, targetTable, targetColumns, ctx);
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(OracleFullGrammerParser.PrimaryKeyConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        }
        return null;
    }

    @Override
    public Void visitUniqueConstraint(OracleFullGrammerParser.UniqueConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        }
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(OracleFullGrammerParser.TableIndexConstraintContext ctx) {
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(currentDdlTable(), column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        }
        return null;
    }

    @Override
    public Void visitColumnDefinition(OracleFullGrammerParser.ColumnDefinitionContext ctx) {
        String table = currentDdlTable();
        String column = clean(ctx.identifier().getText());
        for (OracleFullGrammerParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            OracleFullGrammerParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
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
    public Void visitCreateIndexStatement(OracleFullGrammerParser.CreateIndexStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(table, column, role, kind, ctx);
        }
        return null;
    }

    private void emitProjectionItems(OracleFullGrammerParser.SelectListContext ctx, ProjectionOwner owner) {
        List<OracleFullGrammerParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            OracleFullGrammerParser.SelectItemContext item = items.get(index);
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

    private void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            ParserRuleContext ctx
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int index = 0; index < count; index++) {
            Map<String, Object> attrs = attrs();
            attrs.put("sourceTable", sourceTable);
            attrs.put("sourceColumn", sourceColumns.get(index));
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("compositePosition", index + 1);
            attrs.put("compositeSize", count);
            add(StructuredParseEventType.DDL_FOREIGN_KEY, ctx, attrs);
        }
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        if (table.isBlank() || column.isBlank()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("table", table);
        attrs.put("column", column);
        attrs.put("role", role);
        attrs.put("kind", kind);
        add(StructuredParseEventType.DDL_INDEX, ctx, attrs);
    }

    private void emitWriteTarget(
            OracleFullGrammerParser.TablePrimaryContext target,
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
            OracleFullGrammerParser.AssignmentContext assignment,
            String targetAlias,
            String targetTable,
            StructuredParseEventType eventType,
            String mappingKind
    ) {
        List<String> targetParts = parts(assignment.qualifiedName());
        String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
        String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
        ExpressionAnalysis source = analyze(assignment.expression());
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

    private List<String> identifiers(OracleFullGrammerParser.IdentifierListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(OracleFullGrammerParser.IndexPartListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (OracleFullGrammerParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) {
                columns.add(clean(part.identifier().getText()));
            }
        }
        return columns;
    }

    private String outputColumn(OracleFullGrammerParser.SelectItemContext item) {
        if (item.identifier() != null) {
            return clean(item.identifier().getText());
        }
        ColumnRead column = singleColumn(item.expression());
        return column == null ? "" : column.column();
    }

    private ColumnRead singleSelectColumn(OracleFullGrammerParser.SelectStatementContext select) {
        List<ColumnRead> columns = selectColumns(select);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private List<ColumnRead> selectColumns(OracleFullGrammerParser.SelectStatementContext select) {
        List<OracleFullGrammerParser.SelectItemContext> items = select.querySpecification().selectList().selectItem();
        List<ColumnRead> columns = new ArrayList<>();
        for (OracleFullGrammerParser.SelectItemContext item : items) {
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

    private ColumnRead singleColumn(OracleFullGrammerParser.ExpressionContext expression) {
        if (expression instanceof OracleFullGrammerParser.ColumnExpressionContext columnExpression) {
            List<String> parts = parts(columnExpression.qualifiedName());
            if (parts.size() == 1) {
                return new ColumnRead("", parts.get(0));
            }
            return new ColumnRead(parts.get(parts.size() - 2), parts.get(parts.size() - 1));
        }
        if (expression instanceof OracleFullGrammerParser.ParenExpressionContext paren) {
            return singleColumn(paren.expression());
        }
        return null;
    }

    private ExpressionAnalysis analyze(OracleFullGrammerParser.ExpressionContext expression) {
        if (expression instanceof OracleFullGrammerParser.ColumnExpressionContext columnExpression) {
            ColumnRead column = singleColumn(columnExpression);
            if (column == null) {
                return ExpressionAnalysis.empty();
            }
            return ExpressionAnalysis.of(column, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        if (expression instanceof OracleFullGrammerParser.ParenExpressionContext paren) {
            return analyze(paren.expression());
        }
        if (expression instanceof OracleFullGrammerParser.BinaryExpressionContext binary) {
            ExpressionAnalysis left = analyze(binary.expression(0));
            ExpressionAnalysis right = analyze(binary.expression(1));
            LineageTransformType transform = "||".equals(binary.arithmeticOperator().getText())
                    ? LineageTransformType.CONCAT_FORMAT
                    : LineageTransformType.ARITHMETIC;
            return ExpressionAnalysis.combine(transform, LineageFlowKind.VALUE, left, right);
        }
        if (expression instanceof OracleFullGrammerParser.FunctionExpressionContext function) {
            ExpressionAnalysis args = ExpressionAnalysis.empty();
            if (function.functionCall().expressionList() != null) {
                for (OracleFullGrammerParser.ExpressionContext argument : function.functionCall().expressionList().expression()) {
                    args = ExpressionAnalysis.combine(args.transform(), args.flowKind(), args, analyze(argument));
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
        if (expression instanceof OracleFullGrammerParser.CaseExpressionContext caseExpression) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (OracleFullGrammerParser.CaseWhenClauseContext whenClause : caseExpression.caseWhenClause()) {
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
                for (OracleFullGrammerParser.ExpressionContext part : caseExpression.expression()) {
                    combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL,
                            combined,
                            analyze(part));
                }
            }
            return new ExpressionAnalysis(combined.sources(), LineageTransformType.CASE_WHEN, LineageFlowKind.CONTROL);
        }
        if (expression instanceof OracleFullGrammerParser.ScalarSubqueryExpressionContext scalarSubquery) {
            visit(scalarSubquery.selectStatement());
            List<ColumnRead> columns = selectColumns(scalarSubquery.selectStatement());
            if (columns.isEmpty()) {
                return ExpressionAnalysis.empty();
            }
            return new ExpressionAnalysis(columns, LineageTransformType.DIRECT, LineageFlowKind.VALUE);
        }
        return ExpressionAnalysis.empty();
    }

    private ExpressionAnalysis analyze(OracleFullGrammerParser.PredicateContext predicate) {
        if (predicate instanceof OracleFullGrammerParser.AndPredicateContext andPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(andPredicate.predicate(0)),
                    analyze(andPredicate.predicate(1)));
        }
        if (predicate instanceof OracleFullGrammerParser.OrPredicateContext orPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(orPredicate.predicate(0)),
                    analyze(orPredicate.predicate(1)));
        }
        if (predicate instanceof OracleFullGrammerParser.NotPredicateContext notPredicate) {
            return analyze(notPredicate.predicate());
        }
        if (predicate instanceof OracleFullGrammerParser.ParenPredicateContext parenPredicate) {
            return analyze(parenPredicate.predicate());
        }
        if (predicate instanceof OracleFullGrammerParser.ComparisonPredicateContext comparisonPredicate) {
            return ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL,
                    analyze(comparisonPredicate.expression(0)),
                    analyze(comparisonPredicate.expression(1)));
        }
        if (predicate instanceof OracleFullGrammerParser.LikePredicateContext likePredicate) {
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
        if (predicate instanceof OracleFullGrammerParser.LiteralInPredicateContext literalInPredicate) {
            ExpressionAnalysis combined = analyze(literalInPredicate.expression());
            for (OracleFullGrammerParser.ExpressionContext item : literalInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof OracleFullGrammerParser.InSubqueryPredicateContext inSubqueryPredicate) {
            return analyze(inSubqueryPredicate.expression());
        }
        if (predicate instanceof OracleFullGrammerParser.TupleInSubqueryPredicateContext tupleInPredicate) {
            ExpressionAnalysis combined = ExpressionAnalysis.empty();
            for (OracleFullGrammerParser.ExpressionContext item : tupleInPredicate.expressionList().expression()) {
                combined = ExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL,
                        combined,
                        analyze(item));
            }
            return combined;
        }
        if (predicate instanceof OracleFullGrammerParser.ExpressionPredicateContext expressionPredicate) {
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

    private long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }

    private String targetAlias(OracleFullGrammerParser.TablePrimaryContext primary) {
        if (primary instanceof OracleFullGrammerParser.NamedTablePrimaryContext named && named.tableAlias() != null) {
            return clean(named.tableAlias().identifier().getText());
        }
        return targetTable(primary);
    }

    private String rowsetAlias(OracleFullGrammerParser.TablePrimaryContext primary) {
        if (primary instanceof OracleFullGrammerParser.NamedTablePrimaryContext named) {
            if (named.tableAlias() != null) {
                return clean(named.tableAlias().identifier().getText());
            }
            return baseName(qualifiedName(named.qualifiedName()));
        }
        if (primary instanceof OracleFullGrammerParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        return "";
    }

    private String targetTable(OracleFullGrammerParser.TablePrimaryContext primary) {
        if (primary instanceof OracleFullGrammerParser.NamedTablePrimaryContext named) {
            return qualifiedName(named.qualifiedName());
        }
        if (primary instanceof OracleFullGrammerParser.DerivedTablePrimaryContext derived && derived.tableAlias() != null) {
            return clean(derived.tableAlias().identifier().getText());
        }
        return "";
    }

    private String qualifiedName(OracleFullGrammerParser.QualifiedNameContext ctx) {
        return String.join(".", parts(ctx));
    }

    private List<String> parts(OracleFullGrammerParser.QualifiedNameContext ctx) {
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

    private String joinKind(OracleFullGrammerParser.JoinClauseContext join) {
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
