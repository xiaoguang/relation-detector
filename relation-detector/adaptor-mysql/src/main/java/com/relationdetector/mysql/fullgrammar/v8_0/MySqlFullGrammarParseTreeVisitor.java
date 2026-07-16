package com.relationdetector.mysql.fullgrammar.v8_0;

import com.relationdetector.core.fullgrammar.*;
import java.util.List;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlSqlEventVisitorCore;
import com.relationdetector.mysql.fullgrammar.common.MySqlSqlEventVisitorCore.ColumnParts;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter.ProjectionItem;
import com.relationdetector.mysql.fullgrammar.common.MySqlUpdateControlSupport;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.CommonTableExpressionContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.CaseValueExpressionContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.CreateTableContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.CreateTriggerContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.DeleteStatementContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.DerivedTableContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.EscapedTableReferenceContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.ExprAndContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.FunctionParameterContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.InsertStatementContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.JoinedTableContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.PredicateExprInContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.PredicateContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.PrimaryExprCompareContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.ProcedureParameterContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.QuerySpecificationContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.SelectItemContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.SimpleExprSubQueryContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.SingleTableContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.TableFunctionContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.TableReferenceContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.UpdateStatementContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.UpdateElementContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.VariableDeclarationContext;
import com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 *
 * MySQL 8.0 typed parse-tree traversal; shared fact semantics remain in core.
 */
final class MySqlFullGrammarParseTreeVisitor extends MySqlFullGrammarParserBaseVisitor<Void> {
    private final FullGrammarEventFacade sink;
    private final MySqlSqlEventVisitorCore core;
    private final MySqlExpressionContextAdapter adapter;
    private int ownedJoinPredicateDepth;

    MySqlFullGrammarParseTreeVisitor(SqlStatementRecord statement, List<?> visibleTokens) {
        this.adapter = new MySqlParseTreeAdapter();
        this.sink = new FullGrammarEventFacade(statement, new MySqlExpressionAnalyzer());
        this.core = new MySqlSqlEventVisitorCore(sink);
    }

    List<StructuredSqlEvent> extract(ParseTree tree) {
        if (tree != null) {
            visit(tree);
        }
        return core.mergedEvents();
    }
    @Override
    public Void visitSingleTable(SingleTableContext ctx) {
        String table = ctx.tableRef() == null ? "" : ctx.tableRef().getText();
        String alias = ctx.tableAlias() == null ? "" : sink.firstIdentifier(ctx.tableAlias());
        sink.rowset(ctx, "FROM", table, alias);
        core.bindPhysicalRowset(table, alias);
        core.rememberRowset(alias.isBlank() ? sink.baseName(table) : alias);
        return visitChildren(ctx);
    }
    @Override
    public Void visitDerivedTable(DerivedTableContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : sink.firstIdentifier(ctx.tableAlias());
        if (!alias.isBlank()) {
            sink.ignoredRowset(ctx, alias, "DERIVED_TABLE");
            sink.rowset(ctx, "FROM", alias, alias);
            core.bindDerivedRowset(alias);
            core.rememberRowset(alias);
            int rowsetScopeMark = sink.rowsetScopeMark();
            sink.withProjectionOwner(alias, () -> {
                if (ctx.subquery() != null) {
                    visit(ctx.subquery());
                    emitTopLevelProjectionItems(alias, ctx.subquery());
                }
            });
            sink.restoreRowsetScope(rowsetScopeMark);
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTableFunction(TableFunctionContext ctx) {
        String alias = ctx.tableAlias() == null ? "JSON_TABLE" : sink.firstIdentifier(ctx.tableAlias());
        sink.ignoredRowset(ctx, alias, "FUNCTION_ROWSET");
        sink.rowset(ctx, "JOIN", alias, alias);
        core.rememberRowset(alias);
        return null;
    }

    @Override
    public Void visitProcedureParameter(ProcedureParameterContext ctx) {
        rememberNonColumnParameter(ctx.functionParameter());
        return visitChildren(ctx);
    }

    @Override
    public Void visitFunctionParameter(FunctionParameterContext ctx) {
        rememberNonColumnParameter(ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitVariableDeclaration(VariableDeclarationContext ctx) {
        if (ctx.identifierList() != null) {
            ctx.identifierList().identifier().forEach(identifier -> core.markNonColumnIdentifier(identifier.getText()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQuerySpecification(QuerySpecificationContext ctx) {
        rememberSelectIntoVariables(ctx.intoClause());
        core.withQueryScope(() -> sink.withSelectScope(() -> {
            if (ctx.fromClause() != null) {
                visit(ctx.fromClause());
            }
            if (ctx.whereClause() != null) {
                visit(ctx.whereClause());
            }
            if (ctx.groupByClause() != null) {
                visit(ctx.groupByClause());
            }
            if (ctx.havingClause() != null) {
                visit(ctx.havingClause());
            }
            if (ctx.selectItemList() != null) {
                visit(ctx.selectItemList());
            }
            if (ctx.windowClause() != null) {
                visit(ctx.windowClause());
            }
            if (ctx.qualifyClause() != null) {
                visit(ctx.qualifyClause());
            }
        }));
        return null;
    }

    private void rememberSelectIntoVariables(
            com.relationdetector.mysql.fullgrammar.v8_0.MySqlFullGrammarParser.IntoClauseContext ctx
    ) {
        if (ctx == null || ctx.OUTFILE_SYMBOL() != null || ctx.DUMPFILE_SYMBOL() != null) {
            return;
        }
        ctx.textOrIdentifier().forEach(identifier -> core.markNonColumnIdentifier(identifier.getText()));
        ctx.userVariable().forEach(variable -> core.markNonColumnIdentifier(variable.getText()));
    }

    @Override
    public Void visitCommonTableExpression(CommonTableExpressionContext ctx) {
        String name = ctx.identifier() == null ? "" : ctx.identifier().getText();
        sink.cte(ctx, name);
        sink.withProjectionOwner(name, () -> {
            if (ctx.subquery() != null) {
                visit(ctx.subquery());
                emitTopLevelProjectionItems(name, ctx.subquery());
            }
        });
        return null;
    }

    @Override
    public Void visitJoinedTable(JoinedTableContext ctx) {
        String left = core.lastRowsetAlias();
        if (ctx.tableReference() != null) {
            visit(ctx.tableReference());
        } else if (ctx.tableFactor() != null) {
            visit(ctx.tableFactor());
        }
        String right = core.lastRowsetAlias();
        if (ctx.expr() != null) {
            sink.predicateEqualities(ctx.expr(), ctx.expr(), adapter.joinKind(ctx));
            ownedJoinPredicateDepth++;
            try {
                visit(ctx.expr());
            } finally {
                ownedJoinPredicateDepth--;
            }
        }
        if (ctx.identifierListWithParentheses() != null && !left.isBlank() && !right.isBlank()) {
            sink.joinUsing(ctx, left, right, sink.identifiers(ctx.identifierListWithParentheses()));
        }
        return null;
    }

    @Override
    public Void visitSimpleExprSubQuery(SimpleExprSubQueryContext ctx) {
        if (ctx.EXISTS_SYMBOL() == null) {
            return visitChildren(ctx);
        }
        core.enterExists();
        try {
            return visitChildren(ctx);
        } finally {
            core.leaveExists();
        }
    }

    @Override
    public Void visitPredicateExprIn(PredicateExprInContext ctx) {
        return visitChildren(ctx);
    }

    @Override
    public Void visitExprAnd(ExprAndContext ctx) {
        if (ctx.getParent() instanceof ExprAndContext) {
            return visitChildren(ctx);
        }
        sink.withPredicateGuards(ctx, () -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitPredicate(PredicateContext ctx) {
        if (ctx.predicateOperations() instanceof PredicateExprInContext in && in.subquery() != null) {
            sink.inSubqueryPredicate(ctx, ctx.bitExpr(0), in.subquery());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPrimaryExprCompare(PrimaryExprCompareContext ctx) {
        if (ctx.compOp() != null && ctx.compOp().EQUAL_OPERATOR() != null) {
            if (core.inExists()) {
                sink.existsPredicateEquality(ctx, ctx.boolPri(), ctx.predicate());
            } else if (ownedJoinPredicateDepth == 0) {
                sink.predicateEquality(ctx, ctx.boolPri(), ctx.predicate(), "WHERE_OR_UNKNOWN");
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitUpdateStatement(UpdateStatementContext ctx) {
        String target = adapter.firstTableName(ctx.tableReferenceList());
        sink.writeTarget(ctx, target, "");
        sink.withWriteTarget(target, () -> visitChildren(ctx));
        MySqlUpdateControlSupport.emit(ctx.whereClause(),
                ctx.whereClause() == null ? null : ctx.whereClause().expr(), target,
                ctx.updateList().updateElement(),
                update -> update.columnRef() == null ? "" : update.columnRef().getText(), sink, core);
        return null;
    }

    @Override
    public Void visitDeleteStatement(DeleteStatementContext ctx) {
        if (ctx.tableRef() != null) {
            String table = ctx.tableRef().getText();
            String alias = ctx.tableAlias() == null ? "" : sink.firstIdentifier(ctx.tableAlias());
            sink.rowset(ctx, "DELETE", table, alias);
            core.rememberRowset(alias.isBlank() ? sink.baseName(table) : alias);
            if (ctx.whereClause() != null) {
                visit(ctx.whereClause());
            }
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertStatement(InsertStatementContext ctx) {
        String target = ctx.tableRef() == null ? "" : ctx.tableRef().getText();
        sink.writeTarget(ctx, target, "");
        if (ctx.insertFromConstructor() != null) {
            List<String> targets = adapter.insertTargets(ctx.insertFromConstructor().fields());
            com.relationdetector.mysql.fullgrammar.common.MySqlInsertValuesSupport.emit(sink, target, targets,
                    ctx.insertFromConstructor().insertValues().valueList().values().stream()
                            .map(values -> values.expr()).toList());
            return visitChildren(ctx.insertFromConstructor());
        }
        if (ctx.insertQueryExpression() == null) {
            return visitChildren(ctx);
        }
        visit(ctx.insertQueryExpression());
        List<String> targets = adapter.insertTargets(ctx.insertQueryExpression().fields());
        List<ProjectionItem> selectItems = adapter.selectItems(ctx.insertQueryExpression());
        int count = Math.min(targets.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            ProjectionItem selectItem = selectItems.get(index);
            sink.insertSelect(selectItem.context(), "", target, targets.get(index), selectItem.expression());
        }
        return null;
    }

    @Override
    public Void visitUpdateElement(UpdateElementContext ctx) {
        ParseTree valueExpression = updateValueExpression(ctx);
        if (ctx.columnRef() == null || valueExpression == null) {
            return visitChildren(ctx);
        }
        ColumnParts target = core.columnParts(ctx.columnRef().getText());
        String targetTable = target.qualifier().isBlank()
                ? sink.currentWriteTarget()
                : sink.tableForAlias(target.qualifier());
        sink.updateAssignment(ctx, target.qualifier(), targetTable, target.column(), valueExpression);
        return visitChildren(ctx);
    }

    private ParseTree updateValueExpression(UpdateElementContext ctx) {
        if (ctx.expr() != null) {
            return ctx.expr();
        }
        CaseValueExpressionContext caseExpression = ctx.caseValueExpression();
        return caseExpression == null ? null : caseExpression;
    }

    @Override
    public Void visitSelectItem(SelectItemContext ctx) {
        if (ctx.expr() == null || sink.currentProjectionOwner().isBlank()) {
            return visitChildren(ctx);
        }
        String outputColumn = ctx.selectAlias() == null
                ? core.projectedColumnName(ctx.expr())
                : sink.firstIdentifier(ctx.selectAlias());
        emitProjection(ctx, sink.currentProjectionOwner(), outputColumn, ctx.expr());
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTrigger(CreateTriggerContext ctx) {
        if (ctx.tableRef() != null) {
            sink.triggerTarget(ctx, ctx.tableRef().getText());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreateTable(CreateTableContext ctx) {
        if (ctx.TEMPORARY_SYMBOL() != null && ctx.tableName() != null) {
            sink.localTempTable(ctx, ctx.tableName().getText());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTableReference(TableReferenceContext ctx) {
        if (ctx.tableFactor() != null) {
            visit(ctx.tableFactor());
        } else if (ctx.escapedTableReference() != null) {
            visit(ctx.escapedTableReference());
        }
        for (JoinedTableContext joined : ctx.joinedTable()) {
            visit(joined);
        }
        return null;
    }

    @Override
    public Void visitEscapedTableReference(EscapedTableReferenceContext ctx) {
        if (ctx.tableFactor() != null) {
            visit(ctx.tableFactor());
        }
        for (JoinedTableContext joined : ctx.joinedTable()) {
            visit(joined);
        }
        return null;
    }

    private void emitTopLevelProjectionItems(String alias, ParseTree tree) {
        for (ProjectionItem item : adapter.topLevelProjectionItems(tree)) {
            String outputColumn = item.explicitAlias() == null
                    ? core.projectedColumnName(item.expression())
                    : sink.firstIdentifier(item.explicitAlias());
            emitProjection(item.context(), alias, outputColumn, item.expression());
        }
    }

    private void emitProjection(
            ParserRuleContext context, String outputAlias, String outputColumn, ParseTree expression
    ) {
        adapter.directProjectionColumn(expression)
                .flatMap(source -> core.physicalTableForAlias(source.qualifier())
                        .map(table -> new ResolvedProjection(table, source.column())))
                .ifPresentOrElse(
                        source -> sink.directProjection(
                                context, outputAlias, outputColumn, source.table(), source.column()),
                        () -> sink.projection(context, outputAlias, outputColumn, expression));
    }

    private record ResolvedProjection(String table, String column) { }

    private void rememberNonColumnParameter(FunctionParameterContext ctx) {
        if (ctx == null || ctx.parameterName() == null || ctx.parameterName().identifier() == null) return;
        core.markNonColumnIdentifier(ctx.parameterName().identifier().getText());
    }

}
