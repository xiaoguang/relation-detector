package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.core.fullgrammer.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.CommonTableExpressionContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.CreateTableContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.CreateTriggerContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.DerivedTableContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.EscapedTableReferenceContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.FieldsContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.FunctionParameterContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.InsertStatementContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.JoinedTableContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.PredicateExprInContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.PredicateContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.PrimaryExprCompareContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.ProcedureParameterContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.QuerySpecificationContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.SelectItemContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.SimpleExprSubQueryContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.SingleTableContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.TableFunctionContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.TableReferenceContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.UpdateElementContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.UpdateStatementContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.VariableDeclarationContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParserBaseVisitor;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.RuleNode;

/**
 * MySQL 8.0 full-grammer SQL parse-tree visitor。
 *
 * <p>CN: visitor 从具体 MySQL grammar context 生成 StructuredSqlEvent。token helper
 * 仅用于 source text/location 和 identifier 读取；relationship / lineage 语义仍在 core。
 *
 * <p>EN: MySQL 8.0 full-grammer SQL parse-tree visitor. It emits
 * StructuredSqlEvent records from concrete MySQL grammar contexts. Token helpers
 * are limited to source text/location and identifier reading; relationship and
 * lineage semantics remain in core.
 */
final class MySqlTokenEventParseTreeVisitor extends MySqlFullGrammerParserBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final FullGrammerTypedSqlEventSink sink;
    private final List<String> rowsetAliases = new ArrayList<>();

    MySqlTokenEventParseTreeVisitor(SqlStatementRecord statement, List<?> visibleTokens) {
        this.statement = statement;
        this.sink = new FullGrammerTypedSqlEventSink(statement, new MySqlExpressionAnalyzer());
    }

    /**
     * 访问 parse tree 并返回该 SQL 的结构事件。
     *
     * <p>EN: Visits the parse tree and returns structured events for the SQL.
     */
    List<StructuredSqlEvent> extract(ParseTree tree) {
        if (tree != null) {
            visit(tree);
        }
        return FullGrammerEventMerger.merge(sink.events(), List.of(), FullGrammerNativeEventTypes.MYSQL_NATIVE_EVENTS);
    }

    @Override
    public Void visitSingleTable(SingleTableContext ctx) {
        String table = ctx.tableRef() == null ? "" : ctx.tableRef().getText();
        String alias = ctx.tableAlias() == null ? "" : sink.firstIdentifier(ctx.tableAlias());
        sink.rowset(ctx, "FROM", table, alias);
        rememberRowset(alias.isBlank() ? sink.baseName(table) : alias);
        return visitChildren(ctx);
    }

    @Override
    public Void visitDerivedTable(DerivedTableContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : sink.firstIdentifier(ctx.tableAlias());
        if (!alias.isBlank()) {
            sink.ignoredRowset(ctx, alias, "DERIVED_TABLE");
            sink.rowset(ctx, "FROM", alias, alias);
            rememberRowset(alias);
            sink.withProjectionOwner(alias, () -> {
                if (ctx.subquery() != null) {
                    visit(ctx.subquery());
                }
            });
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTableFunction(TableFunctionContext ctx) {
        String alias = ctx.tableAlias() == null ? "JSON_TABLE" : sink.firstIdentifier(ctx.tableAlias());
        sink.ignoredRowset(ctx, alias, "FUNCTION_ROWSET");
        sink.rowset(ctx, "JOIN", alias, alias);
        rememberRowset(alias);
        return null;
    }

    @Override
    public Void visitProcedureParameter(ProcedureParameterContext ctx) {
        if (ctx.functionParameter() != null) {
            rememberNonColumnParameter(ctx.functionParameter());
        }
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
            ctx.identifierList().identifier().forEach(identifier -> sink.nonColumnIdentifier(identifier.getText()));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitQuerySpecification(QuerySpecificationContext ctx) {
        rememberSelectIntoVariables(ctx.intoClause());
        sink.withSelectScope(() -> {
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
        });
        return null;
    }

    private void rememberSelectIntoVariables(
            com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.IntoClauseContext ctx
    ) {
        if (ctx == null || ctx.OUTFILE_SYMBOL() != null || ctx.DUMPFILE_SYMBOL() != null) {
            return;
        }
        ctx.textOrIdentifier().forEach(identifier -> sink.nonColumnIdentifier(identifier.getText()));
        ctx.userVariable().forEach(variable -> sink.nonColumnIdentifier(variable.getText()));
    }

    @Override
    public Void visitCommonTableExpression(CommonTableExpressionContext ctx) {
        String name = ctx.identifier() == null ? "" : ctx.identifier().getText();
        sink.cte(ctx, name);
        sink.withProjectionOwner(name, () -> {
            if (ctx.subquery() != null) {
                visit(ctx.subquery());
            }
        });
        return null;
    }

    @Override
    public Void visitJoinedTable(JoinedTableContext ctx) {
        String left = lastRowsetAlias();
        if (ctx.tableReference() != null) {
            visit(ctx.tableReference());
        } else if (ctx.tableFactor() != null) {
            visit(ctx.tableFactor());
        }
        String right = lastRowsetAlias();
        if (ctx.expr() != null) {
            sink.predicateEqualities(ctx, ctx.expr(), joinKind(ctx));
            visit(ctx.expr());
        }
        if (ctx.identifierListWithParentheses() != null && !left.isBlank() && !right.isBlank()) {
            sink.joinUsing(ctx, left, right, sink.identifiers(ctx.identifierListWithParentheses()));
        }
        return null;
    }

    @Override
    public Void visitSimpleExprSubQuery(SimpleExprSubQueryContext ctx) {
        sink.subqueryPredicates(ctx, ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitPredicateExprIn(PredicateExprInContext ctx) {
        sink.subqueryPredicates(ctx, ctx);
        return visitChildren(ctx);
    }

    @Override
    public Void visitPredicate(PredicateContext ctx) {
        Void result = visitChildren(ctx);
        if (ctx.predicateOperations() instanceof PredicateExprInContext in && in.subquery() != null) {
            sink.inSubqueryPredicate(ctx, ctx.bitExpr(0), in.subquery());
        }
        return result;
    }

    @Override
    public Void visitPrimaryExprCompare(PrimaryExprCompareContext ctx) {
        if (ctx.compOp() != null && "=".equals(ctx.compOp().getText())) {
            sink.predicateEquality(ctx, ctx.boolPri(), ctx.predicate(), "WHERE_OR_UNKNOWN");
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitChildren(RuleNode node) {
        Void result = super.visitChildren(node);
        if (node instanceof ParserRuleContext ctx && isExpressionContext(ctx)) {
            sink.predicateEqualities(ctx, ctx, "WHERE_OR_UNKNOWN");
            sink.subqueryPredicates(ctx, ctx);
        }
        return result;
    }

    @Override
    public Void visitUpdateStatement(UpdateStatementContext ctx) {
        String target = firstTableName(ctx.tableReferenceList());
        sink.writeTarget(ctx, target, "");
        sink.withWriteTarget(target, () -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitInsertStatement(InsertStatementContext ctx) {
        String target = ctx.tableRef() == null ? "" : ctx.tableRef().getText();
        sink.writeTarget(ctx, target, "");
        if (ctx.insertQueryExpression() == null) {
            return visitChildren(ctx);
        }
        visit(ctx.insertQueryExpression());
        List<String> targets = insertTargets(ctx.insertQueryExpression().fields());
        List<SelectItemContext> selectItems = selectItems(ctx.insertQueryExpression());
        int count = Math.min(targets.size(), selectItems.size());
        for (int index = 0; index < count; index++) {
            SelectItemContext selectItem = selectItems.get(index);
            if (selectItem.expr() != null) {
                sink.insertSelect(selectItem, "", target, targets.get(index), selectItem.expr());
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateElement(UpdateElementContext ctx) {
        if (ctx.columnRef() == null || ctx.expr() == null) {
            return visitChildren(ctx);
        }
        ColumnParts target = columnParts(ctx.columnRef().getText());
        String targetTable = target.qualifier().isBlank()
                ? sink.currentWriteTarget()
                : sink.tableForAlias(target.qualifier());
        sink.updateAssignment(ctx, target.qualifier(), targetTable, target.column(), ctx.expr());
        return visitChildren(ctx);
    }

    @Override
    public Void visitSelectItem(SelectItemContext ctx) {
        if (ctx.expr() == null || sink.currentProjectionOwner().isBlank()) {
            return visitChildren(ctx);
        }
        String outputColumn = ctx.selectAlias() == null
                ? projectedColumnName(ctx.expr())
                : sink.firstIdentifier(ctx.selectAlias());
        sink.projection(ctx, sink.currentProjectionOwner(), outputColumn, ctx.expr());
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
        if (ctx.getText().toLowerCase(java.util.Locale.ROOT).contains("temporary") && ctx.tableName() != null) {
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

    private String joinKind(JoinedTableContext ctx) {
        if (ctx.outerJoinType() != null) {
            return ctx.outerJoinType().getText().toUpperCase(java.util.Locale.ROOT);
        }
        if (ctx.innerJoinType() != null) {
            return ctx.innerJoinType().getText().toUpperCase(java.util.Locale.ROOT);
        }
        if (ctx.naturalJoinType() != null) {
            return ctx.naturalJoinType().getText().toUpperCase(java.util.Locale.ROOT);
        }
        return "JOIN_ON";
    }

    private String firstTableName(ParseTree tree) {
        if (tree == null) {
            return "";
        }
        if (tree instanceof SingleTableContext single && single.tableRef() != null) {
            return single.tableRef().getText();
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            String found = firstTableName(tree.getChild(index));
            if (!found.isBlank()) {
                return found;
            }
        }
        return "";
    }

    private List<String> insertTargets(FieldsContext fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.insertIdentifier().stream()
                .map(ParseTree::getText)
                .map(sink::clean)
                .filter(column -> !column.isBlank())
                .toList();
    }

    private List<SelectItemContext> selectItems(ParseTree tree) {
        List<SelectItemContext> result = new ArrayList<>();
        collectSelectItems(tree, result);
        return result;
    }

    private void collectSelectItems(ParseTree tree, List<SelectItemContext> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof SelectItemContext item) {
            result.add(item);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectSelectItems(tree.getChild(index), result);
        }
    }

    private String projectedColumnName(ParseTree expression) {
        List<String> identifiers = sink.identifiers(expression);
        return identifiers.isEmpty() ? "" : identifiers.get(identifiers.size() - 1);
    }

    private void rememberNonColumnParameter(FunctionParameterContext ctx) {
        if (ctx == null || ctx.parameterName() == null || ctx.parameterName().identifier() == null) {
            return;
        }
        sink.nonColumnIdentifier(ctx.parameterName().identifier().getText());
    }

    private void rememberRowset(String aliasOrTable) {
        String clean = sink.clean(aliasOrTable);
        if (!clean.isBlank()) {
            rowsetAliases.add(clean);
        }
    }

    private String lastRowsetAlias() {
        return rowsetAliases.isEmpty() ? "" : rowsetAliases.get(rowsetAliases.size() - 1);
    }

    private ColumnParts columnParts(String raw) {
        String clean = sink.clean(raw);
        int dot = clean.lastIndexOf('.');
        if (dot < 0) {
            return new ColumnParts("", clean);
        }
        return new ColumnParts(sink.clean(clean.substring(0, dot)), sink.clean(clean.substring(dot + 1)));
    }

    private record ColumnParts(String qualifier, String column) {
    }

    private boolean isExpressionContext(ParserRuleContext ctx) {
        String name = ctx.getClass().getSimpleName();
        return name.startsWith("Expr")
                || name.startsWith("PrimaryExpr")
                || name.startsWith("PredicateExpr")
                || name.startsWith("SimpleExpr")
                || name.equals("PredicateContext")
                || name.equals("BoolPriContext")
                || name.equals("BitExprContext");
    }
}
