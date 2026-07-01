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
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.oracle.fullgrammer.common.OracleColumnRead;
import com.relationdetector.oracle.fullgrammer.common.OracleExpressionAnalysis;
import com.relationdetector.oracle.fullgrammer.common.OracleSqlEventVisitorCore;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/**
 * Parse-tree visitor for the Oracle v26ai full-grammer generated from the
 * grammars-v4 PL/SQL grammar base.
 *
 * <p>CN: 本 visitor 只从 Oracle generated parse-tree 的 typed context 生成结构事件。
 * 它不委托 token-event，不用 regex/token-span scanner 判断 SQL 结构。
 *
 * <p>EN: Emits structured events from Oracle generated parser contexts. It does
 * not delegate to token-event and does not infer SQL structure through regex or
 * token-span scanning.
 */
public final class OracleFullGrammerParseTreeVisitor extends OracleFullGrammerParserBaseVisitor<Void> {
    private final OracleSqlEventVisitorCore core;
    private final OracleRoutineScope routineScope = new OracleRoutineScope();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargetTables = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargetAliases = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final Set<String> emittedRowsets = new LinkedHashSet<>();
    private int existsDepth;

    public OracleFullGrammerParseTreeVisitor(SqlStatementRecord statement) {
        this.core = new OracleSqlEventVisitorCore(statement);
    }

    public List<StructuredSqlEvent> collect(OracleFullGrammerParser.Sql_scriptContext root) {
        visit(root);
        return core.events();
    }

    @Override
    public Void visitCreate_procedure_body(OracleFullGrammerParser.Create_procedure_bodyContext ctx) {
        routineScope.enterRoutine();
        visitChildren(ctx);
        routineScope.leaveRoutineEnd(false);
        return null;
    }

    @Override
    public Void visitCreate_function_body(OracleFullGrammerParser.Create_function_bodyContext ctx) {
        routineScope.enterRoutine();
        visitChildren(ctx);
        routineScope.leaveRoutineEnd(false);
        return null;
    }

    @Override
    public Void visitCreate_trigger(OracleFullGrammerParser.Create_triggerContext ctx) {
        routineScope.enterRoutine();
        visitChildren(ctx);
        routineScope.leaveRoutineEnd(false);
        return null;
    }

    @Override
    public Void visitCreate_table(OracleFullGrammerParser.Create_tableContext ctx) {
        String table = qualifiedTable(ctx.schema_name(), ctx.table_name());
        ddlTables.push(table);
        visitChildren(ctx);
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlter_table(OracleFullGrammerParser.Alter_tableContext ctx) {
        ddlTables.push(name(ctx.tableview_name()));
        visitChildren(ctx);
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitColumn_definition(OracleFullGrammerParser.Column_definitionContext ctx) {
        String table = currentDdlTable();
        String column = name(ctx.column_name());
        for (OracleFullGrammerParser.Inline_constraintContext constraint : ctx.inline_constraint()) {
            if (constraint.PRIMARY() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (constraint.UNIQUE() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (constraint.references_clause() != null) {
                List<String> targets = referenceColumns(constraint.references_clause());
                addForeignKeyEvents(table, List.of(column), name(constraint.references_clause().tableview_name()), targets, constraint);
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitOut_of_line_constraint(OracleFullGrammerParser.Out_of_line_constraintContext ctx) {
        if (ctx.foreign_key_clause() != null) {
            emitForeignKey(ctx.foreign_key_clause());
            return null;
        }
        if (ctx.PRIMARY() != null || ctx.UNIQUE() != null) {
            String kind = ctx.PRIMARY() != null ? "PRIMARY_KEY" : "UNIQUE_CONSTRAINT";
            for (String column : columns(ctx.column_name())) {
                addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", kind, ctx);
            }
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitForeign_key_clause(OracleFullGrammerParser.Foreign_key_clauseContext ctx) {
        emitForeignKey(ctx);
        return null;
    }

    @Override
    public Void visitCreate_index(OracleFullGrammerParser.Create_indexContext ctx) {
        if (ctx.table_index_clause() == null) {
            return visitChildren(ctx);
        }
        String table = name(ctx.table_index_clause().tableview_name());
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (OracleFullGrammerParser.Index_exprContext expr : ctx.table_index_clause().index_expr()) {
            if (expr.column_name() != null) {
                addIndexEvent(table, name(expr.column_name()), role, kind, ctx);
            }
        }
        return null;
    }

    @Override
    public Void visitQuery_block(OracleFullGrammerParser.Query_blockContext ctx) {
        if (ctx.from_clause() != null) {
            visit(ctx.from_clause());
        }
        if (ctx.where_clause() != null) {
            visit(ctx.where_clause());
        }
        for (OracleFullGrammerParser.Hierarchical_query_clauseContext clause : ctx.hierarchical_query_clause()) {
            visit(clause);
        }
        for (OracleFullGrammerParser.Group_by_clauseContext clause : ctx.group_by_clause()) {
            visit(clause);
        }
        if (ctx.model_clause() != null) {
            visit(ctx.model_clause());
        }
        if (!projectionOwners.isEmpty()) {
            emitProjectionItems(ctx.selected_list(), projectionOwners.peek());
        }
        return null;
    }

    @Override
    public Void visitTable_ref_aux(OracleFullGrammerParser.Table_ref_auxContext ctx) {
        String table = tableFrom(ctx.table_ref_aux_internal());
        String alias = ctx.table_alias() == null ? core.baseName(table) : name(ctx.table_alias());
        if (!table.isBlank()) {
            emitRowset(ctx, table, alias);
            return visitChildren(ctx);
        }
        OracleFullGrammerParser.Select_statementContext select = first(ctx.table_ref_aux_internal(), OracleFullGrammerParser.Select_statementContext.class);
        if (select != null && !alias.isBlank()) {
            emitIgnoredRowset(ctx, alias);
            projectionOwners.push(new ProjectionOwner(alias, List.of()));
            visit(select);
            projectionOwners.pop();
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitGeneral_table_ref(OracleFullGrammerParser.General_table_refContext ctx) {
        String table = tableFrom(ctx.dml_table_expression_clause());
        String alias = ctx.table_alias() == null ? core.baseName(table) : name(ctx.table_alias());
        if (!table.isBlank()) {
            emitRowset(ctx, table, alias);
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitSelected_tableview(OracleFullGrammerParser.Selected_tableviewContext ctx) {
        if (ctx.tableview_name() != null) {
            String table = name(ctx.tableview_name());
            String alias = ctx.table_alias() == null ? core.baseName(table) : name(ctx.table_alias());
            emitRowset(ctx, table, alias);
            return visitChildren(ctx);
        }
        if (ctx.select_statement() != null) {
            String alias = ctx.table_alias() == null ? "" : name(ctx.table_alias());
            if (!alias.isBlank()) {
                emitIgnoredRowset(ctx, alias);
                projectionOwners.push(new ProjectionOwner(alias, List.of()));
                visit(ctx.select_statement());
                projectionOwners.pop();
                return null;
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitJoin_clause(OracleFullGrammerParser.Join_clauseContext ctx) {
        joinKinds.push(joinKind(ctx));
        visitChildren(ctx);
        joinKinds.pop();
        return null;
    }

    @Override
    public Void visitJoin_using_part(OracleFullGrammerParser.Join_using_partContext ctx) {
        Map<String, Object> attrs = core.attrs();
        attrs.put("usingColumns", columns(ctx.paren_column_list().column_list().column_name()));
        core.add(StructuredParseEventType.JOIN_USING_COLUMNS, ctx, attrs);
        return null;
    }

    @Override
    public Void visitRelational_expression(OracleFullGrammerParser.Relational_expressionContext ctx) {
        if (ctx.relational_operator() != null && "=".equals(ctx.relational_operator().getText())) {
            List<OracleFullGrammerParser.Relational_expressionContext> parts = ctx.relational_expression();
            if (parts.size() == 2) {
                OracleColumnRead left = singleColumn(parts.get(0));
                OracleColumnRead right = singleColumn(parts.get(1));
                if (left != null && right != null) {
                    Map<String, Object> attrs = core.attrs();
                    attrs.put("leftAlias", left.alias());
                    attrs.put("leftColumn", left.column());
                    attrs.put("rightAlias", right.alias());
                    attrs.put("rightColumn", right.column());
                    attrs.put("joinKind", existsDepth > 0 ? "EXISTS" : currentJoinKind());
                    core.add(existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE : StructuredParseEventType.PREDICATE_EQUALITY, ctx, attrs);
                }
            }
        } else if (ctx.IN() != null && ctx.NOT() == null && ctx.in_elements() != null) {
            List<OracleFullGrammerParser.Relational_expressionContext> parts = ctx.relational_expression();
            if (parts.size() == 1 && ctx.in_elements().subquery() != null) {
                OracleColumnRead outer = singleColumn(parts.get(0));
                OracleColumnRead inner = singleSelectColumn(ctx.in_elements().subquery());
                if (outer != null && inner != null) {
                    Map<String, Object> attrs = core.attrs();
                    attrs.put("outerAlias", outer.alias());
                    attrs.put("outerColumn", outer.column());
                    attrs.put("innerAlias", inner.alias());
                    attrs.put("innerColumn", inner.column());
                    attrs.put("innerTable", "");
                    attrs.put("innerTableAlias", inner.alias());
                    attrs.put("verifiedColumnSubquery", true);
                    core.add(StructuredParseEventType.IN_SUBQUERY_PREDICATE, ctx, attrs);
                }
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQuantified_expression(OracleFullGrammerParser.Quantified_expressionContext ctx) {
        if (ctx.EXISTS() != null && ctx.select_only_statement() != null) {
            existsDepth++;
            visit(ctx.select_only_statement());
            existsDepth--;
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdate_statement(OracleFullGrammerParser.Update_statementContext ctx) {
        String table = tableFrom(ctx.general_table_ref().dml_table_expression_clause());
        String alias = ctx.general_table_ref().table_alias() == null ? core.baseName(table) : name(ctx.general_table_ref().table_alias());
        writeTargetTables.push(table);
        writeTargetAliases.push(alias);
        emitWriteTarget(ctx.general_table_ref(), alias, table);
        visitChildren(ctx);
        writeTargetAliases.pop();
        writeTargetTables.pop();
        return null;
    }

    @Override
    public Void visitColumn_based_update_set_clause(OracleFullGrammerParser.Column_based_update_set_clauseContext ctx) {
        if (ctx.column_name() != null && ctx.expression() != null && !writeTargetTables.isEmpty()) {
            emitAssignment(ctx, name(ctx.column_name()), ctx.expression(), StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET");
            return visit(ctx.expression());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitSingle_table_insert(OracleFullGrammerParser.Single_table_insertContext ctx) {
        if (ctx.select_statement() == null || ctx.insert_into_clause() == null || ctx.insert_into_clause().paren_column_list() == null) {
            return visitChildren(ctx);
        }
        String targetTable = tableFrom(ctx.insert_into_clause().general_table_ref().dml_table_expression_clause());
        List<String> targetColumns = columns(ctx.insert_into_clause().paren_column_list().column_list().column_name());
        visit(ctx.select_statement());
        List<OracleFullGrammerParser.Select_list_elementsContext> selectItems = selectItems(ctx.select_statement());
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            OracleFullGrammerParser.ExpressionContext expression = selectItems.get(index).expression();
            if (expression == null) {
                continue;
            }
            OracleExpressionAnalysis source = analyze(expression);
            if (source.sources().isEmpty()) {
                continue;
            }
            Map<String, Object> attrs = core.attrs();
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            attrs.put("mappingKind", "INSERT_SELECT");
            core.add(StructuredParseEventType.INSERT_SELECT_MAPPING, selectItems.get(index), attrs);
        }
        return null;
    }

    @Override
    public Void visitMerge_statement(OracleFullGrammerParser.Merge_statementContext ctx) {
        String targetTable = tableFrom(ctx.selected_tableview(0));
        String targetAlias = aliasFrom(ctx.selected_tableview(0), targetTable);
        writeTargetTables.push(targetTable);
        writeTargetAliases.push(targetAlias);
        emitWriteTarget(ctx.selected_tableview(0), targetAlias, targetTable);
        visit(ctx.selected_tableview(0));
        visit(ctx.selected_tableview(1));
        if (ctx.condition() != null) {
            visit(ctx.condition());
        }
        OracleFullGrammerParser.Merge_update_clauseContext update = ctx.merge_update_clause();
        if (update != null) {
            for (OracleFullGrammerParser.Merge_elementContext element : update.merge_element()) {
                emitAssignment(element, name(element.column_name()), element.expression(), StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE_SET");
            }
        }
        writeTargetAliases.pop();
        writeTargetTables.pop();
        return null;
    }

    private void emitForeignKey(OracleFullGrammerParser.Foreign_key_clauseContext ctx) {
        List<String> sourceColumns = columns(ctx.paren_column_list().column_list().column_name());
        OracleFullGrammerParser.References_clauseContext ref = ctx.references_clause();
        addForeignKeyEvents(currentDdlTable(), sourceColumns, name(ref.tableview_name()), referenceColumns(ref), ctx);
    }

    private void emitAssignment(ParserRuleContext ctx, String targetColumn, OracleFullGrammerParser.ExpressionContext expression, StructuredParseEventType type, String mappingKind) {
        OracleExpressionAnalysis source = analyze(expression);
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attrs = core.attrs();
        attrs.put("targetAlias", writeTargetAliases.peek());
        attrs.put("targetTable", writeTargetTables.peek());
        attrs.put("targetColumn", lastPart(targetColumn));
        attrs.put("sourceAliases", source.aliases());
        attrs.put("sourceColumns", source.columns());
        attrs.put("transformType", source.transform().name());
        attrs.put("flowKind", source.flowKind().name());
        attrs.put("mappingKind", mappingKind);
        core.add(type, ctx, attrs);
    }

    private void emitProjectionItems(OracleFullGrammerParser.Selected_listContext ctx, ProjectionOwner owner) {
        if (ctx == null) {
            return;
        }
        List<OracleFullGrammerParser.Select_list_elementsContext> items = ctx.select_list_elements();
        for (int index = 0; index < items.size(); index++) {
            OracleFullGrammerParser.Select_list_elementsContext item = items.get(index);
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
            Map<String, Object> attrs = core.attrs();
            attrs.put("outputAlias", owner.alias());
            attrs.put("outputColumn", outputColumn);
            attrs.put("sourceAliases", source.aliases());
            attrs.put("sourceColumns", source.columns());
            attrs.put("transformType", source.transform().name());
            attrs.put("flowKind", source.flowKind().name());
            core.add(StructuredParseEventType.PROJECTION_ITEM, item, attrs);
        }
    }

    private String outputColumn(OracleFullGrammerParser.Select_list_elementsContext item) {
        if (item.column_alias() != null) {
            return lastPart(name(item.column_alias()));
        }
        OracleColumnRead single = singleColumn(item.expression());
        return single == null ? "" : single.column();
    }

    private void addForeignKeyEvents(String sourceTable, List<String> sourceColumns, String targetTable, List<String> targetColumns, ParserRuleContext ctx) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int index = 0; index < count; index++) {
            Map<String, Object> attrs = core.attrs();
            attrs.put("sourceTable", sourceTable);
            attrs.put("sourceColumn", sourceColumns.get(index));
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("compositePosition", index + 1);
            attrs.put("compositeSize", count);
            core.add(StructuredParseEventType.DDL_FOREIGN_KEY, ctx, attrs);
        }
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        if (table.isBlank() || column.isBlank()) {
            return;
        }
        Map<String, Object> attrs = core.attrs();
        attrs.put("table", table);
        attrs.put("column", lastPart(column));
        attrs.put("role", role);
        attrs.put("kind", kind);
        core.add(StructuredParseEventType.DDL_INDEX, ctx, attrs);
    }

    private void emitWriteTarget(ParserRuleContext ctx, String alias, String table) {
        if (table.isBlank()) {
            return;
        }
        Map<String, Object> attrs = core.attrs();
        attrs.put("qualifiedTable", table);
        attrs.put("table", core.baseName(table));
        if (!alias.isBlank()) {
            attrs.put("alias", alias);
        }
        core.add(StructuredParseEventType.WRITE_TARGET, ctx, attrs);
    }

    private void emitIgnoredRowset(ParserRuleContext ctx, String alias) {
        Map<String, Object> attrs = core.attrs();
        attrs.put("name", alias);
        attrs.put("table", alias);
        attrs.put("qualifiedTable", alias);
        core.add(StructuredParseEventType.IGNORED_ROWSET, ctx, attrs);
    }

    private void emitRowset(ParserRuleContext ctx, String table, String alias) {
        if (table.isBlank()) {
            return;
        }
        String key = table + "|" + alias + "|" + core.line(ctx);
        if (!emittedRowsets.add(key)) {
            return;
        }
        Map<String, Object> attrs = core.attrs();
        attrs.put("keyword", "FROM");
        attrs.put("qualifiedTable", table);
        attrs.put("table", core.baseName(table));
        if (!alias.isBlank()) {
            attrs.put("alias", alias);
        }
        core.add(StructuredParseEventType.ROWSET_REFERENCE, ctx, attrs);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> referenceColumns(OracleFullGrammerParser.References_clauseContext ref) {
        if (ref == null || ref.paren_column_list() == null) {
            return List.of();
        }
        return columns(ref.paren_column_list().column_list().column_name());
    }

    private List<String> columns(List<OracleFullGrammerParser.Column_nameContext> contexts) {
        return contexts.stream().map(this::name).map(this::lastPart).filter(s -> !s.isBlank()).toList();
    }

    private String tableFrom(OracleFullGrammerParser.Table_ref_aux_internalContext ctx) {
        if (ctx instanceof OracleFullGrammerParser.Table_ref_aux_internal_oneContext one) {
            return tableFrom(one.dml_table_expression_clause());
        }
        if (ctx instanceof OracleFullGrammerParser.Table_ref_aux_internal_threContext only) {
            return tableFrom(only.dml_table_expression_clause());
        }
        return "";
    }

    private String tableFrom(OracleFullGrammerParser.Dml_table_expression_clauseContext ctx) {
        if (ctx == null || ctx.tableview_name() == null) {
            return "";
        }
        return name(ctx.tableview_name());
    }

    private String tableFrom(OracleFullGrammerParser.Selected_tableviewContext ctx) {
        if (ctx == null || ctx.tableview_name() == null) {
            return "";
        }
        return name(ctx.tableview_name());
    }

    private String aliasFrom(OracleFullGrammerParser.Selected_tableviewContext ctx, String table) {
        if (ctx == null || ctx.table_alias() == null) {
            return core.baseName(table);
        }
        return name(ctx.table_alias());
    }

    private String qualifiedTable(OracleFullGrammerParser.Schema_nameContext schema, OracleFullGrammerParser.Table_nameContext table) {
        String tableName = name(table);
        if (schema == null) {
            return tableName;
        }
        return name(schema) + "." + tableName;
    }

    private String name(ParserRuleContext ctx) {
        return ctx == null ? "" : core.clean(ctx.getText());
    }

    private String lastPart(String value) {
        String cleaned = core.clean(value);
        int dot = cleaned.lastIndexOf('.');
        return dot < 0 ? cleaned : cleaned.substring(dot + 1);
    }

    private OracleColumnRead singleSelectColumn(OracleFullGrammerParser.SubqueryContext subquery) {
        List<OracleFullGrammerParser.Select_list_elementsContext> items = selectItems(subquery);
        if (items.size() != 1 || items.get(0).expression() == null) {
            return null;
        }
        return singleColumn(items.get(0).expression());
    }

    private List<OracleFullGrammerParser.Select_list_elementsContext> selectItems(ParserRuleContext ctx) {
        OracleFullGrammerParser.Query_blockContext query = first(ctx, OracleFullGrammerParser.Query_blockContext.class);
        if (query == null || query.selected_list() == null) {
            return List.of();
        }
        return query.selected_list().select_list_elements();
    }

    private OracleColumnRead singleColumn(ParserRuleContext ctx) {
        List<OracleColumnRead> columns = columnReads(ctx);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private OracleExpressionAnalysis analyze(ParserRuleContext ctx) {
        List<OracleColumnRead> columns = columnReads(ctx);
        if (columns.isEmpty()) {
            return OracleExpressionAnalysis.empty();
        }
        LineageTransformType transform = transformFor(ctx);
        LineageFlowKind flow = transform == LineageTransformType.CASE_WHEN ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE;
        return new OracleExpressionAnalysis(columns, transform, flow);
    }

    private LineageTransformType transformFor(ParseTree tree) {
        String type = tree.getClass().getSimpleName();
        String text = tree.getText().toLowerCase(Locale.ROOT);
        if (type.contains("Case") || text.startsWith("case")) {
            return LineageTransformType.CASE_WHEN;
        }
        if (text.contains("||") || text.startsWith("concat") || text.startsWith("listagg")) {
            return LineageTransformType.CONCAT_FORMAT;
        }
        if (text.startsWith("sum(") || text.startsWith("avg(") || text.startsWith("count(") || text.startsWith("min(") || text.startsWith("max(")) {
            return LineageTransformType.AGGREGATE;
        }
        if (text.startsWith("coalesce(") || text.startsWith("nvl(")) {
            return LineageTransformType.COALESCE;
        }
        if (text.contains("+") || text.contains("-") || text.contains("*") || text.contains("/")) {
            return LineageTransformType.ARITHMETIC;
        }
        return LineageTransformType.DIRECT;
    }

    private List<OracleColumnRead> columnReads(ParseTree tree) {
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectColumnReads(tree, reads);
        return new ArrayList<>(reads.values());
    }

    private void collectColumnReads(ParseTree tree, Map<String, OracleColumnRead> reads) {
        if (tree == null) {
            return;
        }
        if (tree instanceof OracleFullGrammerParser.Column_nameContext column) {
            addColumnRead(name(column), reads);
            return;
        }
        if (tree instanceof OracleFullGrammerParser.Table_elementContext element) {
            addColumnRead(name(element), reads);
            return;
        }
        if (tree instanceof OracleFullGrammerParser.General_elementContext element) {
            String text = name(element);
            if (!text.contains("(") && text.contains(".")) {
                addColumnRead(text, reads);
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectColumnReads(tree.getChild(index), reads);
        }
    }

    private void addColumnRead(String raw, Map<String, OracleColumnRead> reads) {
        String value = core.clean(raw);
        int dot = value.lastIndexOf('.');
        if (dot <= 0 || dot == value.length() - 1) {
            return;
        }
        String alias = core.clean(value.substring(0, dot));
        String column = core.clean(value.substring(dot + 1));
        if (alias.isBlank() || column.isBlank()) {
            return;
        }
        String key = alias + "." + column;
        reads.putIfAbsent(key, new OracleColumnRead(alias, column));
    }

    private <T extends ParserRuleContext> T first(ParseTree tree, Class<T> type) {
        if (type.isInstance(tree)) {
            return type.cast(tree);
        }
        if (tree == null) {
            return null;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            T found = first(tree.getChild(index), type);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    private String joinKind(OracleFullGrammerParser.Join_clauseContext ctx) {
        String text = ctx.getText().toUpperCase(Locale.ROOT);
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
}
