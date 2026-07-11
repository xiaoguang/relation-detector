package com.relationdetector.sqlserver.tokenevent;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Typed traversal facade for the SQL Server token-event structural grammar. */
public final class SqlServerTokenEventParseTreeVisitor extends SqlServerTokenEventWriteDdlSupport {
    public SqlServerTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        this(statement, false);
    }

    public SqlServerTokenEventParseTreeVisitor(SqlStatementRecord statement, boolean ddlOnly) {
        super(statement, ddlOnly);
    }

    public List<StructuredSqlEvent> collect(SqlServerRelationSqlParser.Tsql_fileContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitStatement(SqlServerRelationSqlParser.StatementContext ctx) {
        if (ctx.create_or_alter_procedure() != null || ctx.create_or_alter_trigger() != null) {
            return visitChildren(ctx);
        }
        statementScopes.push("stmt-" + nextStatementScope++);
        try {
            return visitChildren(ctx);
        } finally {
            statementScopes.pop();
        }
    }

    @Override
    public Void visitCommon_table_expression(SqlServerRelationSqlParser.Common_table_expressionContext ctx) {
        String name = clean(ctx.id_().getText());
        if (name.isBlank()) return visitChildren(ctx);
        emitter.addRowset(events, ctx, StructuredParseEventType.CTE_DECLARATION,
                "", name, name, "", name, "", "");
        emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", name, name, "", name, "", "CTE_DECLARATION");
        projectionOwners.push(name);
        visit(ctx.select_statement());
        projectionOwners.pop();
        return null;
    }

    @Override
    public Void visitQuery_specification(SqlServerRelationSqlParser.Query_specificationContext ctx) {
        if (ctx.table_sources() != null) visit(ctx.table_sources());
        if (ctx.search_condition_clause() != null) visit(ctx.search_condition_clause());
        if (!projectionOwners.isEmpty()) {
            emitProjectionItems(ctx.select_list(), projectionOwners.peek(),
                    singleProjectionQualifier(ctx.table_sources()));
        }
        return null;
    }

    @Override
    public Void visitTable_source(SqlServerRelationSqlParser.Table_sourceContext ctx) {
        visit(ctx.table_source_item());
        ctx.table_source_suffix().forEach(this::visit);
        return null;
    }

    @Override
    public Void visitJoin_on(SqlServerRelationSqlParser.Join_onContext ctx) {
        visit(ctx.table_source());
        joinKinds.push(joinKind(ctx.join_type()));
        visit(ctx.search_condition());
        joinKinds.pop();
        return null;
    }

    @Override
    public Void visitCross_join(SqlServerRelationSqlParser.Cross_joinContext ctx) {
        visit(ctx.table_source());
        return null;
    }

    @Override
    public Void visitApply_(SqlServerRelationSqlParser.Apply_Context ctx) {
        visit(ctx.table_source());
        return null;
    }

    @Override
    public Void visitTable_source_item(SqlServerRelationSqlParser.Table_source_itemContext ctx) {
        if (ctx.full_table_name() != null) {
            String qualified = qualifiedName(ctx.full_table_name());
            String table = baseName(qualified);
            String alias = ctx.as_table_alias() == null ? ""
                    : clean(ctx.as_table_alias().table_alias().getText());
            if (isTemp(table)) {
                emitter.addRowset(events, ctx, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                        "", qualified, table, "", "", "", "");
            } else {
                emitter.addRowset(events, ctx, StructuredParseEventType.ROWSET_REFERENCE,
                        "FROM", qualified, table, alias, "", "", "");
            }
            return null;
        }
        if (ctx.derived_table() != null) {
            String alias = ctx.as_table_alias() == null ? ""
                    : clean(ctx.as_table_alias().table_alias().getText());
            if (!alias.isBlank()) {
                emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                        "", alias, alias, "", alias, "", "DERIVED_TABLE");
                projectionOwners.push(alias);
                visit(ctx.derived_table());
                projectionOwners.pop();
            } else {
                visit(ctx.derived_table());
            }
            return null;
        }
        if (ctx.function_call() != null || ctx.LOCAL_ID() != null) {
            String alias = ctx.as_table_alias() == null ? ctx.getText()
                    : clean(ctx.as_table_alias().table_alias().getText());
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", alias, alias, "", alias, "", "FUNCTION_ROWSET");
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPredicate(SqlServerRelationSqlParser.PredicateContext ctx) {
        if (ctx.EXISTS() != null) {
            existsDepth++;
            visit(ctx.subquery());
            existsDepth--;
            return null;
        }
        List<SqlServerRelationSqlParser.ExpressionContext> expressions = ctx.expression();
        if (expressions.size() >= 2 && ctx.comparison_operator() != null
                && "=".equals(ctx.comparison_operator().getText())) {
            ColumnRead left = singleColumn(expressions.get(0));
            ColumnRead right = singleColumn(expressions.get(1));
            if (left != null && right != null) {
                emitter.addPredicate(events, ctx,
                        existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE
                                : StructuredParseEventType.PREDICATE_EQUALITY,
                        left.alias(), left.column(), right.alias(), right.column(),
                        existsDepth > 0 ? "EXISTS" : currentJoinKind());
                return null;
            }
        }
        if (!expressions.isEmpty() && ctx.IN() != null && ctx.subquery() != null) {
            ColumnRead outer = singleColumn(expressions.get(0));
            SqlServerRelationSqlParser.Select_statementContext select = ctx.subquery().select_statement();
            ColumnRead inner = singleSelectColumn(select);
            visit(ctx.subquery());
            if (outer != null && inner != null) {
                SqlServerRelationSqlParser.Query_specificationContext query = firstQuerySpecification(select);
                String innerTable = tableForAlias(query == null ? null : query.table_sources(), inner.alias());
                emitter.addInSubquery(events, ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                        List.of(outer.alias()), List.of(outer.column()),
                        List.of(inner.alias()), List.of(inner.column()), baseName(innerTable));
            }
            return null;
        }
        return visitChildren(ctx);
    }
}
