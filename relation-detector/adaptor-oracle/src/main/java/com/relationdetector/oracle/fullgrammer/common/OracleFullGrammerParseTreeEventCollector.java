package com.relationdetector.oracle.fullgrammer.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.oracle.routine.OracleRoutineScope;

/**
 * Shared Oracle full-grammer parse-tree event collector.
 *
 * <p>CN: Oracle 四个版本使用独立 generated parser class。本类只消费 ANTLR
 * parse-tree 的 typed context 和 child accessors，不委托 token-event，也不做 SQL 文本
 * 正则结构判断。
 */
public final class OracleFullGrammerParseTreeEventCollector {
    private final OracleSqlEventVisitorCore core;
    private final OracleRoutineScope routineScope = new OracleRoutineScope();
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargetTables = new ArrayDeque<>();
    private final ArrayDeque<String> writeTargetAliases = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private final Set<String> emittedRowsets = new LinkedHashSet<>();
    private int existsDepth;

    public OracleFullGrammerParseTreeEventCollector(SqlStatementRecord statement) {
        this.core = new OracleSqlEventVisitorCore(statement);
    }

    public List<StructuredSqlEvent> collect(ParseTree root) {
        visit(root);
        return core.events();
    }

    private void visit(ParseTree tree) {
        if (tree == null) {
            return;
        }
        if (!(tree instanceof ParserRuleContext ctx)) {
            return;
        }
        switch (contextName(ctx)) {
            case "Create_procedure_bodyContext",
                    "Create_function_bodyContext",
                    "Create_triggerContext" -> visitRoutineBody(ctx);
            case "Subquery_factoring_clauseContext" -> visitSubqueryFactoringClause(ctx);
            case "Create_tableContext" -> visitCreateTable(ctx);
            case "Alter_tableContext" -> visitAlterTable(ctx);
            case "Column_definitionContext", "Virtual_column_definitionContext" -> visitColumnDefinition(ctx);
            case "Out_of_line_constraintContext" -> visitOutOfLineConstraint(ctx);
            case "Foreign_key_clauseContext" -> emitForeignKey(ctx);
            case "Create_indexContext" -> visitCreateIndex(ctx);
            case "Query_blockContext" -> visitQueryBlock(ctx);
            case "Table_ref_auxContext" -> visitTableRefAux(ctx);
            case "General_table_refContext" -> visitGeneralTableRef(ctx);
            case "Selected_tableviewContext" -> visitSelectedTableview(ctx);
            case "Join_clauseContext" -> visitJoinClause(ctx);
            case "Join_using_partContext" -> visitJoinUsingPart(ctx);
            case "Relational_expressionContext" -> visitRelationalExpression(ctx);
            case "Compound_expressionContext" -> visitCompoundExpression(ctx);
            case "Quantified_expressionContext" -> visitQuantifiedExpression(ctx);
            case "Update_statementContext" -> visitUpdateStatement(ctx);
            case "Column_based_update_set_clauseContext" -> visitColumnBasedUpdateSetClause(ctx);
            case "Single_table_insertContext" -> visitSingleTableInsert(ctx);
            case "Merge_statementContext" -> visitMergeStatement(ctx);
            default -> visitChildren(ctx);
        }
    }

    private void visitRoutineBody(ParserRuleContext ctx) {
        routineScope.enterRoutine();
        visitChildren(ctx);
        routineScope.leaveRoutineEnd(false);
    }

    private void visitSubqueryFactoringClause(ParserRuleContext ctx) {
        String cte = name(child(ctx, "query_name"));
        if (cte.isBlank()) {
            visitChildren(ctx);
            return;
        }
        emitCteDeclaration(ctx, cte);
        projectionOwners.push(new ProjectionOwner(cte, columnsFromParenColumnList(child(ctx, "paren_column_list"))));
        visit(child(ctx, "subquery"));
        projectionOwners.pop();
    }

    private void visitCreateTable(ParserRuleContext ctx) {
        String table = qualifiedTable(child(ctx, "schema_name"), child(ctx, "table_name"));
        ddlTables.push(table);
        visitChildren(ctx);
        ddlTables.pop();
    }

    private void visitAlterTable(ParserRuleContext ctx) {
        ddlTables.push(name(child(ctx, "tableview_name")));
        visitChildren(ctx);
        ddlTables.pop();
    }

    private void visitColumnDefinition(ParserRuleContext ctx) {
        String table = currentDdlTable();
        String column = name(child(ctx, "column_name"));
        OracleDdlEventVisitorCore.addColumnEvent(core, ctx, table, column);
        for (ParserRuleContext constraint : children(ctx, "inline_constraint")) {
            if (node(constraint, "PRIMARY") != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (node(constraint, "UNIQUE") != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (child(constraint, "references_clause") != null) {
                ParserRuleContext ref = child(constraint, "references_clause");
                addForeignKeyEvents(table, List.of(column), name(child(ref, "tableview_name")), referenceColumns(ref), constraint);
            }
        }
        visitChildren(ctx);
    }

    private void visitOutOfLineConstraint(ParserRuleContext ctx) {
        ParserRuleContext foreignKey = child(ctx, "foreign_key_clause");
        if (foreignKey != null) {
            emitForeignKey(foreignKey);
            return;
        }
        if (node(ctx, "PRIMARY") != null || node(ctx, "UNIQUE") != null) {
            String kind = node(ctx, "PRIMARY") != null ? "PRIMARY_KEY" : "UNIQUE_CONSTRAINT";
            for (String column : columns(children(ctx, "column_name"))) {
                addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", kind, ctx);
            }
            return;
        }
        visitChildren(ctx);
    }

    private void visitCreateIndex(ParserRuleContext ctx) {
        ParserRuleContext tableIndex = child(ctx, "table_index_clause");
        if (tableIndex == null) {
            visitChildren(ctx);
            return;
        }
        String table = name(child(tableIndex, "tableview_name"));
        String role = node(ctx, "UNIQUE") == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = node(ctx, "UNIQUE") == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (ParserRuleContext expr : children(tableIndex, "index_expr")) {
            ParserRuleContext column = child(expr, "column_name");
            if (column != null) {
                addIndexEvent(table, name(column), role, kind, ctx);
            }
        }
    }

    private void visitQueryBlock(ParserRuleContext ctx) {
        queryScopes.push(new QueryScope());
        try {
            ParserRuleContext selectedList = child(ctx, "selected_list");
            visit(child(ctx, "from_clause"));
            visit(child(ctx, "where_clause"));
            for (ParserRuleContext clause : children(ctx, "hierarchical_query_clause")) {
                visit(clause);
            }
            for (ParserRuleContext clause : children(ctx, "group_by_clause")) {
                visit(clause);
            }
            visit(child(ctx, "model_clause"));
            // Scalar subqueries in SELECT projections contain their own typed
            // rowsets and predicates. Visit them even when this query is not
            // currently materializing a CTE/derived projection.
            visit(selectedList);
            if (!projectionOwners.isEmpty()) {
                emitProjectionItems(selectedList, projectionOwners.peek());
            }
        } finally {
            queryScopes.pop();
        }
    }

    private void visitTableRefAux(ParserRuleContext ctx) {
        ParserRuleContext internal = child(ctx, "table_ref_aux_internal");
        String table = tableFrom(internal);
        String alias = child(ctx, "table_alias") == null ? core.baseName(table) : name(child(ctx, "table_alias"));
        if (!table.isBlank()) {
            emitRowset(ctx, table, alias);
            visitChildren(ctx);
            return;
        }
        ParserRuleContext select = first(internal, "Select_statementContext");
        if (select != null && !alias.isBlank()) {
            emitIgnoredRowset(ctx, alias);
            projectionOwners.push(new ProjectionOwner(alias, List.of()));
            visit(select);
            projectionOwners.pop();
            return;
        }
        visitChildren(ctx);
    }

    private void visitGeneralTableRef(ParserRuleContext ctx) {
        String table = tableFrom(child(ctx, "dml_table_expression_clause"));
        String alias = child(ctx, "table_alias") == null ? core.baseName(table) : name(child(ctx, "table_alias"));
        if (!table.isBlank()) {
            emitRowset(ctx, table, alias);
        }
        visitChildren(ctx);
    }

    private void visitSelectedTableview(ParserRuleContext ctx) {
        if (child(ctx, "tableview_name") != null) {
            String table = name(child(ctx, "tableview_name"));
            String alias = child(ctx, "table_alias") == null ? core.baseName(table) : name(child(ctx, "table_alias"));
            emitRowset(ctx, table, alias);
            visitChildren(ctx);
            return;
        }
        ParserRuleContext select = child(ctx, "select_statement");
        if (select != null) {
            String alias = child(ctx, "table_alias") == null ? "" : name(child(ctx, "table_alias"));
            if (!alias.isBlank()) {
                emitIgnoredRowset(ctx, alias);
                projectionOwners.push(new ProjectionOwner(alias, List.of()));
                visit(select);
                projectionOwners.pop();
                return;
            }
        }
        visitChildren(ctx);
    }

    private void visitJoinClause(ParserRuleContext ctx) {
        joinKinds.push(joinKind(ctx));
        visitChildren(ctx);
        joinKinds.pop();
    }

    private void visitJoinUsingPart(ParserRuleContext ctx) {
        Map<String, Object> attrs = core.attrs();
        attrs.put("usingColumns", columnsFromParenColumnList(child(ctx, "paren_column_list")));
        core.add(StructuredParseEventType.JOIN_USING_COLUMNS, ctx, attrs);
    }

    private void visitRelationalExpression(ParserRuleContext ctx) {
        ParserRuleContext op = child(ctx, "relational_operator");
        if (op != null && "=".equals(op.getText())) {
            List<ParserRuleContext> parts = children(ctx, "relational_expression");
            if (parts.size() == 2) {
                OracleColumnRead left = singleDirectColumn(parts.get(0));
                OracleColumnRead right = singleDirectColumn(parts.get(1));
                if (left != null && right != null) {
                    Map<String, Object> attrs = core.attrs();
                    attrs.put("leftAlias", left.alias());
                    attrs.put("leftColumn", left.column());
                    attrs.put("rightAlias", right.alias());
                    attrs.put("rightColumn", right.column());
                    attrs.put("joinKind", existsDepth > 0 ? "EXISTS" : currentJoinKind());
                    core.add(existsDepth > 0
                            ? StructuredParseEventType.EXISTS_PREDICATE
                            : StructuredParseEventType.PREDICATE_EQUALITY, ctx, attrs);
                }
            }
        } else if (node(ctx, "IN") != null && node(ctx, "NOT") == null && child(ctx, "in_elements") != null) {
            List<ParserRuleContext> parts = children(ctx, "relational_expression");
            ParserRuleContext inElements = child(ctx, "in_elements");
            if (parts.size() == 1 && child(inElements, "subquery") != null) {
                OracleColumnRead outer = singleColumn(parts.get(0));
                OracleColumnRead inner = singleSelectColumn(child(inElements, "subquery"));
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
        visitChildren(ctx);
    }

    private void visitCompoundExpression(ParserRuleContext ctx) {
        ParserRuleContext inElements = child(ctx, "in_elements");
        List<ParserRuleContext> concatenations = children(ctx, "concatenation");
        ParserRuleContext subquery = child(inElements, "subquery");
        if (node(ctx, "IN") != null && node(ctx, "NOT") == null
                && concatenations.size() == 1 && subquery != null) {
            OracleColumnRead outer = singleColumn(concatenations.get(0));
            OracleColumnRead inner = singleSelectColumn(subquery);
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
        visitChildren(ctx);
    }

    private void visitQuantifiedExpression(ParserRuleContext ctx) {
        if (node(ctx, "EXISTS") != null && child(ctx, "select_only_statement") != null) {
            existsDepth++;
            visit(child(ctx, "select_only_statement"));
            existsDepth--;
            return;
        }
        visitChildren(ctx);
    }

    private void visitUpdateStatement(ParserRuleContext ctx) {
        ParserRuleContext general = child(ctx, "general_table_ref");
        String table = tableFrom(child(general, "dml_table_expression_clause"));
        String alias = child(general, "table_alias") == null ? core.baseName(table) : name(child(general, "table_alias"));
        writeTargetTables.push(table);
        writeTargetAliases.push(alias);
        emitWriteTarget(general, alias, table);
        visitChildren(ctx);
        writeTargetAliases.pop();
        writeTargetTables.pop();
    }

    private void visitColumnBasedUpdateSetClause(ParserRuleContext ctx) {
        if (child(ctx, "column_name") != null && child(ctx, "expression") != null && !writeTargetTables.isEmpty()) {
            emitAssignment(ctx, name(child(ctx, "column_name")), child(ctx, "expression"),
                    StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET");
            visit(child(ctx, "expression"));
            return;
        }
        visitChildren(ctx);
    }

    private void visitSingleTableInsert(ParserRuleContext ctx) {
        ParserRuleContext select = child(ctx, "select_statement");
        ParserRuleContext insert = child(ctx, "insert_into_clause");
        if (select == null || insert == null || child(insert, "paren_column_list") == null) {
            visitChildren(ctx);
            return;
        }
        String targetTable = tableFrom(child(child(insert, "general_table_ref"), "dml_table_expression_clause"));
        List<String> targetColumns = columnsFromParenColumnList(child(insert, "paren_column_list"));
        visit(select);
        List<ParserRuleContext> selectItems = selectItems(select);
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            ParserRuleContext expression = child(selectItems.get(index), "expression");
            if (expression == null) {
                continue;
            }
            for (OracleExpressionAnalysis source : writeAnalyses(expression)) {
                emitWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, selectItems.get(index),
                        "", targetTable, targetColumns.get(index), source, "INSERT_SELECT");
            }
        }
    }

    private void visitMergeStatement(ParserRuleContext ctx) {
        List<ParserRuleContext> tableviews = children(ctx, "selected_tableview");
        if (tableviews.size() < 2) {
            visitChildren(ctx);
            return;
        }
        String targetTable = tableFrom(tableviews.get(0));
        String targetAlias = aliasFrom(tableviews.get(0), targetTable);
        writeTargetTables.push(targetTable);
        writeTargetAliases.push(targetAlias);
        emitWriteTarget(tableviews.get(0), targetAlias, targetTable);
        visit(tableviews.get(0));
        visit(tableviews.get(1));
        visit(child(ctx, "condition"));
        ParserRuleContext update = child(ctx, "merge_update_clause");
        if (update != null) {
            for (ParserRuleContext element : children(update, "merge_element")) {
                emitAssignment(element, name(child(element, "column_name")), child(element, "expression"),
                        StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE_SET");
            }
        }
        writeTargetAliases.pop();
        writeTargetTables.pop();
    }

    private void emitForeignKey(ParserRuleContext ctx) {
        List<String> sourceColumns = columnsFromParenColumnList(child(ctx, "paren_column_list"));
        ParserRuleContext ref = child(ctx, "references_clause");
        addForeignKeyEvents(currentDdlTable(), sourceColumns, name(child(ref, "tableview_name")), referenceColumns(ref), ctx);
    }

    private void emitAssignment(
            ParserRuleContext ctx,
            String targetColumn,
            ParserRuleContext expression,
            StructuredParseEventType type,
            String mappingKind
    ) {
        for (OracleExpressionAnalysis source : writeAnalyses(expression)) {
            emitWriteMapping(type, ctx, writeTargetAliases.peek(), writeTargetTables.peek(),
                    lastPart(targetColumn), source, mappingKind);
        }
    }

    private void emitProjectionItems(ParserRuleContext selectedList, ProjectionOwner owner) {
        if (selectedList == null) {
            return;
        }
        List<ParserRuleContext> items = children(selectedList, "select_list_elements");
        for (int index = 0; index < items.size(); index++) {
            ParserRuleContext item = items.get(index);
            ParserRuleContext expression = child(item, "expression");
            if (expression == null) {
                continue;
            }
            String outputColumn = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            for (OracleExpressionAnalysis source : writeAnalyses(expression)) {
                if (source.sources().isEmpty()) {
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
    }

    private void emitWriteMapping(
            StructuredParseEventType type,
            ParserRuleContext context,
            String targetAlias,
            String targetTable,
            String targetColumn,
            OracleExpressionAnalysis source,
            String mappingKind
    ) {
        if (source.sources().isEmpty()) {
            return;
        }
        Map<String, Object> attrs = core.attrs();
        if (targetAlias != null && !targetAlias.isBlank()) {
            attrs.put("targetAlias", targetAlias);
        }
        attrs.put("targetTable", targetTable);
        attrs.put("targetColumn", targetColumn);
        attrs.put("sourceAliases", source.aliases());
        attrs.put("sourceColumns", source.columns());
        attrs.put("transformType", source.transform().name());
        attrs.put("flowKind", source.flowKind().name());
        attrs.put("mappingKind", mappingKind);
        core.add(type, context, attrs);
    }

    private String outputColumn(ParserRuleContext item) {
        ParserRuleContext alias = child(item, "column_alias");
        if (alias != null) {
            return lastPart(columnAlias(alias));
        }
        OracleColumnRead single = singleColumn(child(item, "expression"));
        return single == null ? "" : single.column();
    }

    private String columnAlias(ParserRuleContext ctx) {
        if (ctx == null) {
            return "";
        }
        ParserRuleContext identifier = child(ctx, "identifier");
        if (identifier != null) {
            return name(identifier);
        }
        ParserRuleContext quoted = child(ctx, "quoted_string");
        if (quoted != null) {
            return core.clean(quoted.getText());
        }
        return "";
    }

    private void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            ParserRuleContext ctx
    ) {
        OracleDdlEventVisitorCore.addForeignKeyEvents(core, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        OracleDdlEventVisitorCore.addIndexEvent(core, ctx, table, lastPart(column), role, kind);
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
        registerCurrentRowset(alias);
    }

    private void emitCteDeclaration(ParserRuleContext ctx, String name) {
        Map<String, Object> attrs = core.attrs();
        attrs.put("name", name);
        attrs.put("table", name);
        attrs.put("qualifiedTable", name);
        core.add(StructuredParseEventType.CTE_DECLARATION, ctx, attrs);
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
        registerCurrentRowset(alias.isBlank() ? core.baseName(table) : alias);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> referenceColumns(ParserRuleContext ref) {
        return columnsFromParenColumnList(child(ref, "paren_column_list"));
    }

    private List<String> columnsFromParenColumnList(ParserRuleContext parenColumnList) {
        if (parenColumnList == null) {
            return List.of();
        }
        ParserRuleContext columnList = child(parenColumnList, "column_list");
        return columnList == null ? List.of() : columns(children(columnList, "column_name"));
    }

    private List<String> columns(List<ParserRuleContext> contexts) {
        return contexts.stream().map(this::name).map(this::lastPart).filter(s -> !s.isBlank()).toList();
    }

    private String tableFrom(ParserRuleContext ctx) {
        if (ctx == null) {
            return "";
        }
        String type = contextName(ctx);
        if (type.equals("Table_ref_aux_internal_oneContext") || type.equals("Table_ref_aux_internal_threContext")) {
            return tableFrom(child(ctx, "dml_table_expression_clause"));
        }
        ParserRuleContext dml = child(ctx, "dml_table_expression_clause");
        if (dml != null) {
            return tableFrom(dml);
        }
        ParserRuleContext tableview = child(ctx, "tableview_name");
        return tableview == null ? "" : name(tableview);
    }

    private String aliasFrom(ParserRuleContext selectedTableview, String table) {
        ParserRuleContext alias = child(selectedTableview, "table_alias");
        return alias == null ? core.baseName(table) : name(alias);
    }

    private String qualifiedTable(ParserRuleContext schema, ParserRuleContext table) {
        String tableName = name(table);
        return schema == null ? tableName : name(schema) + "." + tableName;
    }

    private String name(ParseTree ctx) {
        return ctx == null ? "" : core.clean(ctx.getText());
    }

    private String lastPart(String value) {
        String cleaned = core.clean(value);
        int dot = cleaned.lastIndexOf('.');
        return dot < 0 ? cleaned : cleaned.substring(dot + 1);
    }

    private OracleColumnRead singleSelectColumn(ParserRuleContext subquery) {
        List<ParserRuleContext> items = selectItems(subquery);
        if (items.size() != 1 || child(items.get(0), "expression") == null) {
            return null;
        }
        ParserRuleContext expression = child(items.get(0), "expression");
        ParserRuleContext general = first(expression, "General_elementContext");
        if (general != null && name(general).equals(name(expression)) && !name(general).contains(".")) {
            Set<String> aliases = new LinkedHashSet<>();
            collectPhysicalRowsetAliases(subquery, aliases);
            return aliases.size() == 1 ? new OracleColumnRead(aliases.iterator().next(), name(general)) : null;
        }
        return singleColumn(expression);
    }

    private void collectPhysicalRowsetAliases(ParseTree tree, Set<String> aliases) {
        if (tree == null) {
            return;
        }
        if ("Table_ref_auxContext".equals(contextName(tree)) && tree instanceof ParserRuleContext tableRef) {
            ParserRuleContext internal = child(tableRef, "table_ref_aux_internal");
            String table = tableFrom(internal);
            if (!table.isBlank()) {
                aliases.add(child(tableRef, "table_alias") == null
                        ? core.baseName(table)
                        : name(child(tableRef, "table_alias")));
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectPhysicalRowsetAliases(tree.getChild(index), aliases);
        }
    }

    private List<ParserRuleContext> selectItems(ParseTree ctx) {
        ParserRuleContext query = first(ctx, "Query_blockContext");
        ParserRuleContext selectedList = child(query, "selected_list");
        return selectedList == null ? List.of() : children(selectedList, "select_list_elements");
    }

    private OracleColumnRead singleColumn(ParseTree ctx) {
        List<OracleColumnRead> columns = columnReads(ctx);
        return columns.size() == 1 ? columns.get(0) : null;
    }

    private OracleColumnRead singleDirectColumn(ParseTree ctx) {
        return containsFunctionCall(ctx) ? null : singleColumn(ctx);
    }

    private boolean containsFunctionCall(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        String type = contextName(tree);
        if (type.toLowerCase(Locale.ROOT).contains("function")) {
            return true;
        }
        if ("General_elementContext".equals(type)) {
            for (ParserRuleContext part : children(tree, "general_element_part")) {
                if (!children(part, "function_argument").isEmpty()) {
                    return true;
                }
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsFunctionCall(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private OracleExpressionAnalysis analyze(ParseTree ctx) {
        List<OracleColumnRead> columns = columnReads(ctx);
        if (columns.isEmpty()) {
            return OracleExpressionAnalysis.empty();
        }
        LineageTransformType transform = transformFor(ctx);
        LineageFlowKind flow = transform == LineageTransformType.CASE_WHEN ? LineageFlowKind.CONTROL : LineageFlowKind.VALUE;
        return new OracleExpressionAnalysis(columns, transform, flow);
    }

    private List<OracleExpressionAnalysis> writeAnalyses(ParseTree expression) {
        ParserRuleContext scalarSubquery = first(expression, "SubqueryContext");
        if (scalarSubquery != null) {
            List<OracleExpressionAnalysis> projections = scalarProjectionAnalyses(scalarSubquery);
            OracleExpressionAnalysis control = scalarControlAnalysis(scalarSubquery);
            List<OracleExpressionAnalysis> result = new ArrayList<>(2);
            for (OracleExpressionAnalysis projection : projections) {
                if (projection.flowKind() == LineageFlowKind.VALUE && !projection.sources().isEmpty()) {
                    result.add(new OracleExpressionAnalysis(
                            projection.sources(),
                            dominantValueTransform(transformFor(expression), projection.transform()),
                            LineageFlowKind.VALUE));
                } else if (projection.flowKind() == LineageFlowKind.CONTROL) {
                    control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                            LineageFlowKind.CONTROL, control, projection);
                }
            }
            if (!control.sources().isEmpty()) {
                result.add(new OracleExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL));
            }
            return List.copyOf(result);
        }
        return expressionAnalyses(expression, columnReads(expression));
    }

    private List<OracleExpressionAnalysis> expressionAnalyses(
            ParseTree expression,
            List<OracleColumnRead> projectionSources
    ) {
        ParserRuleContext caseExpression = first(expression, "Case_expressionContext");
        if (caseExpression == null) {
            OracleExpressionAnalysis analysis = new OracleExpressionAnalysis(
                    projectionSources, transformFor(expression), LineageFlowKind.VALUE);
            return analysis.sources().isEmpty() ? List.of() : List.of(analysis);
        }
        List<OracleExpressionAnalysis> caseRoles = caseRoleAnalyses(caseExpression);
        Set<OracleColumnRead> caseValues = new LinkedHashSet<>();
        Set<OracleColumnRead> caseControls = new LinkedHashSet<>();
        for (OracleExpressionAnalysis role : caseRoles) {
            if (role.flowKind() == LineageFlowKind.VALUE) {
                caseValues.addAll(role.sources());
            } else {
                caseControls.addAll(role.sources());
            }
        }
        List<OracleColumnRead> values = projectionSources.stream()
                .filter(source -> !caseControls.contains(source) || caseValues.contains(source))
                .distinct()
                .toList();
        List<OracleExpressionAnalysis> result = new ArrayList<>(2);
        if (!values.isEmpty()) {
            LineageTransformType outer = transformFor(expression);
            LineageTransformType transform = outer == LineageTransformType.AGGREGATE
                    || outer == LineageTransformType.CUMULATIVE
                    ? outer
                    : LineageTransformType.CASE_WHEN;
            result.add(new OracleExpressionAnalysis(values, transform, LineageFlowKind.VALUE));
        }
        if (!caseControls.isEmpty()) {
            result.add(new OracleExpressionAnalysis(List.copyOf(caseControls), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    private List<OracleExpressionAnalysis> caseRoleAnalyses(ParserRuleContext caseExpression) {
        ParserRuleContext caseBody = child(caseExpression, "simple_case_expression");
        boolean simple = caseBody != null;
        if (caseBody == null) {
            caseBody = child(caseExpression, "searched_case_expression");
        }
        if (caseBody == null) {
            return List.of();
        }

        OracleExpressionAnalysis value = OracleExpressionAnalysis.empty();
        OracleExpressionAnalysis control = OracleExpressionAnalysis.empty();
        if (simple) {
            ParserRuleContext selector = child(caseBody, "expression");
            if (selector != null) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(selector));
            }
        }
        for (ParserRuleContext whenPart : children(caseBody, "case_when_part_expression")) {
            List<ParserRuleContext> expressions = children(whenPart, "expression");
            if (!expressions.isEmpty()) {
                control = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.CONTROL, control, analyze(expressions.get(0)));
            }
            if (expressions.size() > 1) {
                value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                        LineageFlowKind.VALUE, value, analyze(expressions.get(1)));
            }
        }
        ParserRuleContext elsePart = child(caseBody, "case_else_part_expression");
        if (elsePart != null && child(elsePart, "expression") != null) {
            value = OracleExpressionAnalysis.combine(LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE, value, analyze(child(elsePart, "expression")));
        }
        List<OracleExpressionAnalysis> result = new ArrayList<>(2);
        if (!value.sources().isEmpty()) {
            result.add(new OracleExpressionAnalysis(value.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.VALUE));
        }
        if (!control.sources().isEmpty()) {
            result.add(new OracleExpressionAnalysis(control.sources(), LineageTransformType.CASE_WHEN,
                    LineageFlowKind.CONTROL));
        }
        return List.copyOf(result);
    }

    private List<OracleExpressionAnalysis> scalarProjectionAnalyses(ParserRuleContext subquery) {
        List<ParserRuleContext> items = selectItems(subquery);
        if (items.size() != 1 || child(items.get(0), "expression") == null) {
            return List.of();
        }
        ParseTree expression = child(items.get(0), "expression");
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectProjectionColumnReads(expression, reads);
        return expressionAnalyses(expression, new ArrayList<>(reads.values()));
    }

    private OracleExpressionAnalysis scalarControlAnalysis(ParserRuleContext subquery) {
        ParserRuleContext query = first(subquery, "Query_blockContext");
        if (query == null) {
            return OracleExpressionAnalysis.empty();
        }
        Map<String, OracleColumnRead> reads = new LinkedHashMap<>();
        collectDirectScopeColumns(query, "Join_on_partContext", reads);
        collectDirectScopeColumns(query, "Where_clauseContext", reads);
        collectDirectScopeColumns(query, "Group_by_elementsContext", reads);
        collectDirectScopeColumns(query, "Having_clauseContext", reads);
        for (ParserRuleContext item : selectItems(subquery)) {
            ParserRuleContext expression = child(item, "expression");
            if (expression == null) {
                continue;
            }
            for (ParserRuleContext nested : directScalarSubqueries(expression)) {
                for (OracleColumnRead source : scalarControlAnalysis(nested).sources()) {
                    reads.putIfAbsent(source.alias() + "." + source.column(), source);
                }
            }
        }
        return new OracleExpressionAnalysis(new ArrayList<>(reads.values()), LineageTransformType.CASE_WHEN,
                LineageFlowKind.CONTROL);
    }

    private void collectProjectionColumnReads(ParseTree tree, Map<String, OracleColumnRead> reads) {
        if (tree == null) {
            return;
        }
        if ("SubqueryContext".equals(contextName(tree)) && tree instanceof ParserRuleContext subquery) {
            for (OracleExpressionAnalysis analysis : scalarProjectionAnalyses(subquery)) {
                if (analysis.flowKind() != LineageFlowKind.VALUE) {
                    continue;
                }
                for (OracleColumnRead source : analysis.sources()) {
                    reads.putIfAbsent(source.alias() + "." + source.column(), source);
                }
            }
            return;
        }
        String type = contextName(tree);
        if (type.equals("Column_nameContext") || type.equals("Table_elementContext")) {
            addColumnRead(name(tree), reads);
            return;
        }
        if (type.equals("General_elementContext")) {
            String text = name(tree);
            if (!text.contains("(") && text.contains(".")) {
                addColumnRead(text, reads);
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectProjectionColumnReads(tree.getChild(index), reads);
        }
    }

    private List<ParserRuleContext> directScalarSubqueries(ParseTree tree) {
        List<ParserRuleContext> result = new ArrayList<>();
        collectDirectScalarSubqueries(tree, result);
        return result;
    }

    private void collectDirectScalarSubqueries(ParseTree tree, List<ParserRuleContext> result) {
        if (tree == null) {
            return;
        }
        if ("SubqueryContext".equals(contextName(tree)) && tree instanceof ParserRuleContext subquery) {
            result.add(subquery);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDirectScalarSubqueries(tree.getChild(index), result);
        }
    }

    private void collectDirectScopeColumns(
            ParseTree tree,
            String targetContext,
            Map<String, OracleColumnRead> reads
    ) {
        if (tree == null) {
            return;
        }
        if ("SubqueryContext".equals(contextName(tree))) {
            return;
        }
        if (targetContext.equals(contextName(tree))) {
            columnReads(tree).forEach(source -> reads.putIfAbsent(
                    source.alias() + "." + source.column(), source));
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDirectScopeColumns(tree.getChild(index), targetContext, reads);
        }
    }

    private LineageTransformType transformFor(ParseTree tree) {
        String type = tree == null ? "" : tree.getClass().getSimpleName();
        String text = tree == null ? "" : tree.getText().toLowerCase(Locale.ROOT);
        LineageTransformType functionTransform = nestedFunctionTransform(tree);
        if (functionTransform == LineageTransformType.CUMULATIVE
                || functionTransform == LineageTransformType.AGGREGATE
                || functionTransform == LineageTransformType.WINDOW_DERIVED) {
            return functionTransform;
        }
        if (type.contains("Case") || text.startsWith("case")) {
            return LineageTransformType.CASE_WHEN;
        }
        if (containsConcatenationOperator(tree) || functionTransform == LineageTransformType.CONCAT_FORMAT) {
            return LineageTransformType.CONCAT_FORMAT;
        }
        if (containsArithmeticOperator(tree)) {
            return LineageTransformType.ARITHMETIC;
        }
        if (functionTransform != LineageTransformType.DIRECT) {
            return functionTransform;
        }
        return LineageTransformType.DIRECT;
    }

    private LineageTransformType nestedFunctionTransform(ParseTree tree) {
        if (tree == null) {
            return LineageTransformType.DIRECT;
        }
        LineageTransformType transform = LineageTransformType.DIRECT;
        String functionName = typedFunctionName(tree);
        if (!functionName.isBlank()) {
            transform = LineageTransformClassifier.classifyFunction(functionName, false, Map.of(
                    "nvl", LineageTransformType.COALESCE,
                    "listagg", LineageTransformType.CONCAT_FORMAT));
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            transform = dominantValueTransform(transform, nestedFunctionTransform(tree.getChild(index)));
        }
        return transform;
    }

    private String typedFunctionName(ParseTree tree) {
        String type = contextName(tree).toLowerCase(Locale.ROOT);
        boolean functionContext = type.contains("function");
        if (!functionContext && "generalelementcontext".equals(type.replace("_", ""))) {
            for (ParserRuleContext part : children(tree, "general_element_part")) {
                if (!children(part, "function_argument").isEmpty()) {
                    functionContext = true;
                    break;
                }
            }
        }
        if (!functionContext) {
            return "";
        }
        String text = core.clean(tree.getText());
        int paren = text.indexOf('(');
        if (paren <= 0) {
            return "";
        }
        String prefix = text.substring(0, paren);
        int dot = prefix.lastIndexOf('.');
        return core.clean(dot < 0 ? prefix : prefix.substring(dot + 1));
    }

    private boolean containsArithmeticOperator(ParseTree tree) {
        return containsOperator(tree, "+")
                || containsOperator(tree, "-")
                || containsOperator(tree, "*")
                || containsOperator(tree, "/");
    }

    private boolean containsConcatenationOperator(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if ("ConcatenationContext".equals(contextName(tree))) {
            int directBars = 0;
            for (int index = 0; index < tree.getChildCount(); index++) {
                if (tree.getChild(index) instanceof TerminalNode terminal && "|".equals(terminal.getText())) {
                    directBars++;
                }
            }
            if (directBars >= 2) {
                return true;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsConcatenationOperator(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOperator(ParseTree tree, String operator) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof TerminalNode terminal && operator.equals(terminal.getText())) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsOperator(tree.getChild(index), operator)) {
                return true;
            }
        }
        return false;
    }

    private LineageTransformType dominantValueTransform(
            LineageTransformType left,
            LineageTransformType right
    ) {
        if (left == LineageTransformType.CUMULATIVE || right == LineageTransformType.CUMULATIVE) {
            return LineageTransformType.CUMULATIVE;
        }
        if (left == LineageTransformType.AGGREGATE || right == LineageTransformType.AGGREGATE) {
            return LineageTransformType.AGGREGATE;
        }
        return LineageTransformClassifier.dominant(left, right);
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
        String type = contextName(tree);
        if (type.equals("Column_nameContext") || type.equals("Table_elementContext")) {
            addColumnRead(name(tree), reads);
            return;
        }
        if (type.equals("General_elementContext")) {
            String text = name(tree);
            if (!text.contains("(") && text.contains(".")) {
                addColumnRead(text, reads);
                return;
            }
            List<ParserRuleContext> parts = children(tree, "general_element_part");
            if (!text.contains("(") && parts.size() == 1 && children(parts.get(0), "function_argument").isEmpty()) {
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
        if (dot < 0) {
            String defaultAlias = defaultColumnAlias();
            if (defaultAlias.isBlank()) {
                return;
            }
            String column = core.clean(value);
            if (column.isBlank()) {
                return;
            }
            reads.putIfAbsent(defaultAlias + "." + column, new OracleColumnRead(defaultAlias, column));
            return;
        }
        if (dot == 0 || dot == value.length() - 1) {
            return;
        }
        String alias = core.clean(value.substring(0, dot));
        String column = core.clean(value.substring(dot + 1));
        if (alias.isBlank() || column.isBlank()) {
            return;
        }
        reads.putIfAbsent(alias + "." + column, new OracleColumnRead(alias, column));
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

    private ParserRuleContext first(ParseTree tree, String contextName) {
        if (tree == null) {
            return null;
        }
        if (contextName(tree).equals(contextName) && tree instanceof ParserRuleContext ctx) {
            return ctx;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParserRuleContext found = first(tree.getChild(index), contextName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void visitChildren(ParseTree tree) {
        if (tree == null) {
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            visit(tree.getChild(index));
        }
    }

    private String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    private String joinKind(ParserRuleContext ctx) {
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

    private ParserRuleContext child(Object target, String methodName) {
        List<ParserRuleContext> children = children(target, methodName);
        return children.isEmpty() ? null : children.get(0);
    }

    private List<ParserRuleContext> children(Object target, String methodName) {
        if (target == null) {
            return List.of();
        }
        Object value = invoke(target, methodName);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .filter(ParserRuleContext.class::isInstance)
                    .map(ParserRuleContext.class::cast)
                    .toList();
        }
        if (value instanceof ParserRuleContext context) {
            return List.of(context);
        }
        return List.of();
    }

    private Object node(Object target, String methodName) {
        return target == null ? null : invoke(target, methodName);
    }

    private Object invoke(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to read Oracle parse context " + methodName, exception);
        }
    }

    private String contextName(Object tree) {
        return tree == null ? "" : tree.getClass().getSimpleName();
    }

    private record ProjectionOwner(String alias, List<String> columns) {
    }

    private record QueryScope(Set<String> rowsetAliases) {
        QueryScope() {
            this(new LinkedHashSet<>());
        }
    }
}
