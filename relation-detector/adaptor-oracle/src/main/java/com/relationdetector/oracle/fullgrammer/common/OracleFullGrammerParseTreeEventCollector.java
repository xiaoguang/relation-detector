package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.oracle.routine.OracleRoutineScope;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseTreeAdapter.Role;

/**
 * Shared Oracle full-grammer parse-tree event collector.
 *
 * <p>CN: Oracle 四个版本使用独立 generated parser class。本类只消费 ANTLR
 * parse-tree 的 typed context 和 child accessors，不委托 token-event，也不做 SQL 文本
 * 正则结构判断。
 */
public final class OracleFullGrammerParseTreeEventCollector extends OracleFullGrammerParseTreeSupport {
    private final OracleRoutineScope routineScope = new OracleRoutineScope();
    private final OracleFullGrammerExpressionSupport expressionSupport;
    private final OracleFullGrammerDdlCollector ddlCollector;
    private final OracleFullGrammerEventEmitter emitter;
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private int existsDepth;

    public OracleFullGrammerParseTreeEventCollector(
            SqlStatementRecord statement,
            OracleFullGrammerParseTreeAdapter adapter
    ) {
        super(new OracleSqlEventVisitorCore(statement), adapter);
        this.expressionSupport = new OracleFullGrammerExpressionSupport(core, adapter, this::defaultColumnAlias);
        this.ddlCollector = new OracleFullGrammerDdlCollector(core, adapter, this::visit);
        this.emitter = new OracleFullGrammerEventEmitter(core, adapter, expressionSupport, this::registerCurrentRowset);
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
        if (hasRole(ctx, Role.ROUTINE_BODY)) {
            visitRoutineBody(ctx);
        } else if (hasRole(ctx, Role.CTE)) {
            visitSubqueryFactoringClause(ctx);
        } else if (hasRole(ctx, Role.CREATE_TABLE)) {
            ddlCollector.visitCreateTable(ctx);
        } else if (hasRole(ctx, Role.ALTER_TABLE)) {
            ddlCollector.visitAlterTable(ctx);
        } else if (hasRole(ctx, Role.COLUMN_DEFINITION)) {
            ddlCollector.visitColumnDefinition(ctx);
        } else if (hasRole(ctx, Role.OUT_OF_LINE_CONSTRAINT)) {
            ddlCollector.visitOutOfLineConstraint(ctx);
        } else if (hasRole(ctx, Role.FOREIGN_KEY)) {
            ddlCollector.emitForeignKey(ctx);
        } else if (hasRole(ctx, Role.CREATE_INDEX)) {
            ddlCollector.visitCreateIndex(ctx);
        } else if (hasRole(ctx, Role.QUERY_BLOCK)) {
            visitQueryBlock(ctx);
        } else if (hasRole(ctx, Role.TABLE_REF_AUX)) {
            visitTableRefAux(ctx);
        } else if (hasRole(ctx, Role.GENERAL_TABLE_REF)) {
            visitGeneralTableRef(ctx);
        } else if (hasRole(ctx, Role.SELECTED_TABLEVIEW)) {
            visitSelectedTableview(ctx);
        } else if (hasRole(ctx, Role.JOIN_CLAUSE)) {
            visitJoinClause(ctx);
        } else if (hasRole(ctx, Role.JOIN_USING)) {
            visitJoinUsingPart(ctx);
        } else if (hasRole(ctx, Role.RELATIONAL_EXPRESSION)) {
            visitRelationalExpression(ctx);
        } else if (hasRole(ctx, Role.COMPOUND_EXPRESSION)) {
            visitCompoundExpression(ctx);
        } else if (hasRole(ctx, Role.QUANTIFIED_EXPRESSION)) {
            visitQuantifiedExpression(ctx);
        } else if (hasRole(ctx, Role.UPDATE_STATEMENT)) {
            visitUpdateStatement(ctx);
        } else if (hasRole(ctx, Role.UPDATE_SET_CLAUSE)) {
            visitColumnBasedUpdateSetClause(ctx);
        } else if (hasRole(ctx, Role.SINGLE_TABLE_INSERT)) {
            visitSingleTableInsert(ctx);
        } else if (hasRole(ctx, Role.MERGE_STATEMENT)) {
            visitMergeStatement(ctx);
        } else {
            visitChildren(ctx);
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
        emitter.emitCteDeclaration(ctx, cte);
        projectionOwners.push(new ProjectionOwner(cte, columnNamesFromParenColumnList(child(ctx, "paren_column_list"))));
        visit(child(ctx, "subquery"));
        projectionOwners.pop();
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
                ProjectionOwner owner = projectionOwners.peek();
                emitter.emitProjectionItems(selectedList, owner.alias(), owner.columns());
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
            emitter.emitRowset(ctx, table, alias);
            visitChildren(ctx);
            return;
        }
        ParserRuleContext select = first(internal, Role.SELECT_STATEMENT);
        if (select != null && !alias.isBlank()) {
            emitter.emitIgnoredRowset(ctx, alias);
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
            emitter.emitRowset(ctx, table, alias);
        }
        visitChildren(ctx);
    }

    private void visitSelectedTableview(ParserRuleContext ctx) {
        if (child(ctx, "tableview_name") != null) {
            String table = name(child(ctx, "tableview_name"));
            String alias = child(ctx, "table_alias") == null ? core.baseName(table) : name(child(ctx, "table_alias"));
            emitter.emitRowset(ctx, table, alias);
            visitChildren(ctx);
            return;
        }
        ParserRuleContext select = child(ctx, "select_statement");
        if (select != null) {
            String alias = child(ctx, "table_alias") == null ? "" : name(child(ctx, "table_alias"));
            if (!alias.isBlank()) {
                emitter.emitIgnoredRowset(ctx, alias);
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
        core.joinUsing(ctx, columnNamesFromParenColumnList(child(ctx, "paren_column_list")));
    }

    private void visitRelationalExpression(ParserRuleContext ctx) {
        ParserRuleContext op = child(ctx, "relational_operator");
        if (op != null && "=".equals(op.getText())) {
            List<ParserRuleContext> parts = children(ctx, "relational_expression");
            if (parts.size() == 2) {
                OracleColumnRead left = expressionSupport.singleDirectColumn(parts.get(0));
                OracleColumnRead right = expressionSupport.singleDirectColumn(parts.get(1));
                if (left != null && right != null) {
                    core.predicate(ctx, existsDepth > 0
                            ? StructuredParseEventType.EXISTS_PREDICATE
                            : StructuredParseEventType.PREDICATE_EQUALITY,
                            left.alias(), left.column(), right.alias(), right.column(),
                            existsDepth > 0 ? "EXISTS" : currentJoinKind());
                }
            }
        } else if (node(ctx, "IN") != null && node(ctx, "NOT") == null && child(ctx, "in_elements") != null) {
            List<ParserRuleContext> parts = children(ctx, "relational_expression");
            ParserRuleContext inElements = child(ctx, "in_elements");
            if (parts.size() == 1 && child(inElements, "subquery") != null) {
                OracleColumnRead outer = expressionSupport.singleColumn(parts.get(0));
                OracleColumnRead inner = expressionSupport.singleSelectColumn(child(inElements, "subquery"));
                if (outer != null && inner != null) {
                    core.inSubquery(ctx, outer.alias(), outer.column(), inner.alias(), inner.column(), "");
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
            OracleColumnRead outer = expressionSupport.singleColumn(concatenations.get(0));
            OracleColumnRead inner = expressionSupport.singleSelectColumn(subquery);
            if (outer != null && inner != null) {
                core.inSubquery(ctx, outer.alias(), outer.column(), inner.alias(), inner.column(), "");
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
        emitter.beginWriteTarget(general, alias, table);
        visitChildren(ctx);
        emitter.endWriteTarget();
    }

    private void visitColumnBasedUpdateSetClause(ParserRuleContext ctx) {
        if (child(ctx, "column_name") != null && child(ctx, "expression") != null && emitter.hasWriteTarget()) {
            emitter.emitAssignment(ctx, name(child(ctx, "column_name")), child(ctx, "expression"),
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
        List<String> targetColumns = columnNamesFromParenColumnList(child(insert, "paren_column_list"));
        visit(select);
        List<ParserRuleContext> selectItems = expressionSupport.selectItems(select);
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            ParserRuleContext expression = child(selectItems.get(index), "expression");
            if (expression == null) {
                continue;
            }
            emitter.emitExpressionMappings(StructuredParseEventType.INSERT_SELECT_MAPPING,
                    selectItems.get(index), "", targetTable, targetColumns.get(index),
                    expression, "INSERT_SELECT");
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
        emitter.beginWriteTarget(tableviews.get(0), targetAlias, targetTable);
        visit(tableviews.get(0));
        visit(tableviews.get(1));
        visit(child(ctx, "condition"));
        ParserRuleContext update = child(ctx, "merge_update_clause");
        if (update != null) {
            for (ParserRuleContext element : children(update, "merge_element")) {
                emitter.emitAssignment(element, name(child(element, "column_name")), child(element, "expression"),
                        StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE_SET");
            }
        }
        emitter.endWriteTarget();
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

    private record ProjectionOwner(String alias, List<String> columns) {
    }

    private record QueryScope(Set<String> rowsetAliases) {
        QueryScope() {
            this(new LinkedHashSet<>());
        }
    }
}
