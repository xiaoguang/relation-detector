package com.relationdetector.oracle.fullgrammar.common;

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
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Symbol;

/**
 * Shared Oracle full-grammar parse-tree event collector.
 *
 * <p>CN: Oracle 四个版本使用独立 generated parser class。本类只消费 ANTLR
 * parse-tree 的 typed context 和 child accessors，不委托 token-event，也不做 SQL 文本
 * 正则结构判断。
 */
public final class OracleFullGrammarParseTreeEventCollector extends OracleFullGrammarParseTreeSupport {
    private final OracleRoutineScope routineScope = new OracleRoutineScope();
    private final OracleFullGrammarExpressionSupport expressionSupport;
    private final OracleFullGrammarDdlCollector ddlCollector;
    private final OracleFullGrammarEventEmitter emitter;
    private final ArrayDeque<ProjectionOwner> projectionOwners = new ArrayDeque<>();
    private final ArrayDeque<QueryScope> queryScopes = new ArrayDeque<>();
    private final ArrayDeque<String> joinKinds = new ArrayDeque<>();
    private int existsDepth;

    public OracleFullGrammarParseTreeEventCollector(
            SqlStatementRecord statement,
            OracleFullGrammarParseTreeAdapter adapter
    ) {
        super(new OracleSqlEventVisitorCore(statement), adapter);
        this.expressionSupport = new OracleFullGrammarExpressionSupport(core, adapter, this::defaultColumnAlias);
        this.ddlCollector = new OracleFullGrammarDdlCollector(core, adapter, this::visit);
        this.emitter = new OracleFullGrammarEventEmitter(core, adapter, expressionSupport, this::registerCurrentRowset);
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
        String cte = name(child(ctx, Role.QUERY_NAME));
        if (cte.isBlank()) {
            visitChildren(ctx);
            return;
        }
        emitter.emitCteDeclaration(ctx, cte);
        projectionOwners.push(new ProjectionOwner(cte, columnNamesFromParenColumnList(child(ctx, Role.PAREN_COLUMN_LIST))));
        visit(child(ctx, Role.SUBQUERY));
        projectionOwners.pop();
    }

    private void visitQueryBlock(ParserRuleContext ctx) {
        queryScopes.push(new QueryScope());
        try {
            ParserRuleContext selectedList = child(ctx, Role.SELECTED_LIST);
            visit(child(ctx, Role.FROM_CLAUSE));
            visit(child(ctx, Role.WHERE_CLAUSE));
            for (ParserRuleContext clause : children(ctx, Role.HIERARCHICAL_QUERY_CLAUSE)) {
                visit(clause);
            }
            for (ParserRuleContext clause : children(ctx, Role.GROUP_BY_CLAUSE)) {
                visit(clause);
            }
            visit(child(ctx, Role.MODEL_CLAUSE));
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
        ParserRuleContext internal = child(ctx, Role.TABLE_REF_INTERNAL_WRAPPER);
        String table = tableFrom(internal);
        String alias = child(ctx, Role.TABLE_ALIAS) == null ? core.baseName(table) : name(child(ctx, Role.TABLE_ALIAS));
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
        String table = tableFrom(child(ctx, Role.DML_TABLE_EXPRESSION));
        String alias = child(ctx, Role.TABLE_ALIAS) == null ? core.baseName(table) : name(child(ctx, Role.TABLE_ALIAS));
        if (!table.isBlank()) {
            emitter.emitRowset(ctx, table, alias);
        }
        visitChildren(ctx);
    }

    private void visitSelectedTableview(ParserRuleContext ctx) {
        if (child(ctx, Role.TABLEVIEW_NAME) != null) {
            String table = name(child(ctx, Role.TABLEVIEW_NAME));
            String alias = child(ctx, Role.TABLE_ALIAS) == null ? core.baseName(table) : name(child(ctx, Role.TABLE_ALIAS));
            emitter.emitRowset(ctx, table, alias);
            visitChildren(ctx);
            return;
        }
        ParserRuleContext select = child(ctx, Role.SELECT_STATEMENT);
        if (select != null) {
            String alias = child(ctx, Role.TABLE_ALIAS) == null ? "" : name(child(ctx, Role.TABLE_ALIAS));
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
        core.joinUsing(ctx, columnNamesFromParenColumnList(child(ctx, Role.PAREN_COLUMN_LIST)));
    }

    private void visitRelationalExpression(ParserRuleContext ctx) {
        ParserRuleContext op = child(ctx, Role.RELATIONAL_OPERATOR);
        if (isDirectEquality(op)) {
            List<ParserRuleContext> parts = children(ctx, Role.RELATIONAL_EXPRESSION);
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
        } else if (hasSymbol(ctx, Symbol.IN) && !hasSymbol(ctx, Symbol.NOT) && child(ctx, Role.IN_ELEMENTS) != null) {
            List<ParserRuleContext> parts = children(ctx, Role.RELATIONAL_EXPRESSION);
            ParserRuleContext inElements = child(ctx, Role.IN_ELEMENTS);
            if (parts.size() == 1 && child(inElements, Role.SUBQUERY) != null) {
                OracleColumnRead outer = expressionSupport.singleColumn(parts.get(0));
                OracleColumnRead inner = expressionSupport.singleSelectColumn(child(inElements, Role.SUBQUERY));
                if (outer != null && inner != null) {
                    core.inSubquery(ctx, outer.alias(), outer.column(), inner.alias(), inner.column(), "");
                }
            }
        }
        visitChildren(ctx);
    }

    private void visitCompoundExpression(ParserRuleContext ctx) {
        ParserRuleContext inElements = child(ctx, Role.IN_ELEMENTS);
        List<ParserRuleContext> concatenations = children(ctx, Role.CONCATENATION);
        ParserRuleContext subquery = child(inElements, Role.SUBQUERY);
        if (hasSymbol(ctx, Symbol.IN) && !hasSymbol(ctx, Symbol.NOT)
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
        if (hasSymbol(ctx, Symbol.EXISTS) && child(ctx, Role.SELECT_ONLY_STATEMENT) != null) {
            existsDepth++;
            visit(child(ctx, Role.SELECT_ONLY_STATEMENT));
            existsDepth--;
            return;
        }
        visitChildren(ctx);
    }

    private void visitUpdateStatement(ParserRuleContext ctx) {
        ParserRuleContext general = child(ctx, Role.GENERAL_TABLE_REF);
        String table = tableFrom(child(general, Role.DML_TABLE_EXPRESSION));
        String alias = child(general, Role.TABLE_ALIAS) == null ? core.baseName(table) : name(child(general, Role.TABLE_ALIAS));
        emitter.beginWriteTarget(general, alias, table);
        visitChildren(ctx);
        emitter.endWriteTarget();
    }

    private void visitColumnBasedUpdateSetClause(ParserRuleContext ctx) {
        if (child(ctx, Role.COLUMN_NAME) != null && child(ctx, Role.EXPRESSION) != null && emitter.hasWriteTarget()) {
            emitter.emitAssignment(ctx, name(child(ctx, Role.COLUMN_NAME)), child(ctx, Role.EXPRESSION),
                    StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET");
            visit(child(ctx, Role.EXPRESSION));
            return;
        }
        visitChildren(ctx);
    }

    private void visitSingleTableInsert(ParserRuleContext ctx) {
        ParserRuleContext select = child(ctx, Role.SELECT_STATEMENT);
        ParserRuleContext insert = child(ctx, Role.INSERT_INTO_CLAUSE);
        if (select == null || insert == null || child(insert, Role.PAREN_COLUMN_LIST) == null) {
            visitChildren(ctx);
            return;
        }
        String targetTable = tableFrom(child(child(insert, Role.GENERAL_TABLE_REF), Role.DML_TABLE_EXPRESSION));
        List<String> targetColumns = columnNamesFromParenColumnList(child(insert, Role.PAREN_COLUMN_LIST));
        visit(select);
        List<ParserRuleContext> selectItems = expressionSupport.selectItems(select);
        int count = Math.min(targetColumns.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            ParserRuleContext expression = child(selectItems.get(index), Role.EXPRESSION);
            if (expression == null) {
                continue;
            }
            emitter.emitExpressionMappings(StructuredParseEventType.INSERT_SELECT_MAPPING,
                    selectItems.get(index), "", targetTable, targetColumns.get(index),
                    expression, "INSERT_SELECT");
        }
    }

    private void visitMergeStatement(ParserRuleContext ctx) {
        List<ParserRuleContext> tableviews = children(ctx, Role.SELECTED_TABLEVIEW);
        if (tableviews.size() < 2) {
            visitChildren(ctx);
            return;
        }
        String targetTable = tableFrom(tableviews.get(0));
        String targetAlias = aliasFrom(tableviews.get(0), targetTable);
        emitter.beginWriteTarget(tableviews.get(0), targetAlias, targetTable);
        visit(tableviews.get(0));
        visit(tableviews.get(1));
        visit(child(ctx, Role.CONDITION));
        ParserRuleContext update = child(ctx, Role.MERGE_UPDATE_CLAUSE);
        if (update != null) {
            for (ParserRuleContext element : children(update, Role.MERGE_ELEMENT)) {
                emitter.emitAssignment(element, name(child(element, Role.COLUMN_NAME)), child(element, Role.EXPRESSION),
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
        for (ParseTree child : typedChildren(tree)) {
            visit(child);
        }
    }

    private String currentJoinKind() {
        return joinKinds.isEmpty() ? "WHERE_OR_UNKNOWN" : joinKinds.peek();
    }

    private String joinKind(ParserRuleContext ctx) {
        if (hasSymbolInTree(ctx, Symbol.LEFT)) {
            return "LEFT_JOIN";
        }
        if (hasSymbolInTree(ctx, Symbol.RIGHT)) {
            return "RIGHT_JOIN";
        }
        if (hasSymbolInTree(ctx, Symbol.FULL)) {
            return "FULL_JOIN";
        }
        if (hasSymbolInTree(ctx, Symbol.CROSS)) {
            return "CROSS_JOIN";
        }
        return "JOIN";
    }

    private boolean hasSymbolInTree(ParseTree tree, Symbol symbol) {
        if (hasSymbol(tree, symbol)) {
            return true;
        }
        for (ParseTree child : typedChildren(tree)) {
            if (hasSymbolInTree(child, symbol)) {
                return true;
            }
        }
        return false;
    }

    private record ProjectionOwner(String alias, List<String> columns) {
    }

    private record QueryScope(Set<String> rowsetAliases) {
        QueryScope() {
            this(new LinkedHashSet<>());
        }
    }
}
