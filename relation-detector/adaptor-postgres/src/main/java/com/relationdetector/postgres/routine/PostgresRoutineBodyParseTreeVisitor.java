package com.relationdetector.postgres.routine;

import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Typed traversal facade for the independent PostgreSQL routine-body grammar. */
public final class PostgresRoutineBodyParseTreeVisitor extends PostgresRoutineWriteSupport {
    public PostgresRoutineBodyParseTreeVisitor(SqlStatementRecord statement) { super(statement); }

    public List<StructuredSqlEvent> collect(PostgresRoutineBodySqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitTerminal(TerminalNode node) {
        if (node.getSymbol() != null
                && node.getSymbol().getType() == PostgresRoutineBodySqlParser.DOLLAR_QUOTED_STRING) {
            collectRoutineBody(node.getText(), node.getSymbol().getLine());
        }
        return null;
    }

    @Override
    public Void visitCommonTableExpression(PostgresRoutineBodySqlParser.CommonTableExpressionContext ctx) {
        String name = clean(ctx.identifier().getText());
        if (!name.isBlank()) {
            cteNames.add(normalize(name));
            emitter.addRowset(events, ctx, StructuredParseEventType.CTE_DECLARATION,
                    "", name, name, "", name, "", "");
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", name, name, "", name, "", "CTE_DECLARATION");
        }
        List<String> columns = ctx.identifierList() == null ? List.of()
                : ctx.identifierList().identifier().stream()
                        .map(identifier -> clean(identifier.getText())).toList();
        projectionOwners.push(new ProjectionOwner(name, columns));
        visit(ctx.selectStatement());
        projectionOwners.pop();
        return null;
    }

    @Override
    public Void visitQuerySpecification(PostgresRoutineBodySqlParser.QuerySpecificationContext ctx) {
        queryScopes.push(new QueryScope());
        try {
            if (ctx.fromClause() != null) visit(ctx.fromClause());
            if (ctx.whereClause() != null) visit(ctx.whereClause());
            if (ctx.havingClause() != null) visit(ctx.havingClause());
            if (!projectionOwners.isEmpty()) emitProjectionItems(ctx.selectList(), projectionOwners.peek());
        } finally {
            queryScopes.pop();
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(PostgresRoutineBodySqlParser.NamedTablePrimaryContext ctx) {
        String qualified = qualifiedName(ctx.qualifiedName());
        String table = baseName(qualified);
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        emitter.addRowset(events, ctx, StructuredParseEventType.ROWSET_REFERENCE,
                "FROM", qualified, table, alias, "", "", "");
        registerCurrentRowset(alias.isBlank() ? table : alias);
        return null;
    }

    @Override
    public Void visitTableReference(PostgresRoutineBodySqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (PostgresRoutineBodySqlParser.JoinClauseContext join : ctx.joinClause()) {
            String rightAlias = rowsetAlias(join.tablePrimary());
            visit(join.tablePrimary());
            if (join.identifierList() != null) {
                emitter.addJoinUsing(events, join, leftAlias, rightAlias,
                        join.identifierList().identifier().stream()
                                .map(identifier -> clean(identifier.getText())).toList());
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
    public Void visitDerivedTablePrimary(PostgresRoutineBodySqlParser.DerivedTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) {
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", alias, alias, "", alias, "", "DERIVED_TABLE");
            registerCurrentRowset(alias);
            projectionOwners.push(new ProjectionOwner(alias, List.of()));
            visit(ctx.selectStatement());
            projectionOwners.pop();
        } else visit(ctx.selectStatement());
        return null;
    }

    @Override
    public Void visitRowsFromTablePrimary(PostgresRoutineBodySqlParser.RowsFromTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) registerCurrentRowset(alias);
        emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", "ROWS FROM", "ROWS", alias, "ROWS", "", "FUNCTION_ROWSET");
        return null;
    }

    @Override
    public Void visitFunctionRowsetPrimary(PostgresRoutineBodySqlParser.FunctionRowsetPrimaryContext ctx) {
        String function = baseName(qualifiedName(ctx.qualifiedName())).toUpperCase(Locale.ROOT);
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) registerCurrentRowset(alias);
        emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", qualifiedName(ctx.qualifiedName()), function, alias,
                function, "", "FUNCTION_ROWSET");
        return null;
    }

    @Override
    public Void visitComparisonPredicate(PostgresRoutineBodySqlParser.ComparisonPredicateContext ctx) {
        if (ctx.comparisonOperator().EQ() == null) return visitChildren(ctx);
        emitDirectEquality(ctx, ctx.expression(0), ctx.expression(1));
        return null;
    }

    @Override
    public Void visitIsNotDistinctPredicate(PostgresRoutineBodySqlParser.IsNotDistinctPredicateContext ctx) {
        emitDirectEquality(ctx, ctx.expression(0), ctx.expression(1));
        return null;
    }

    @Override
    public Void visitIsDistinctPredicate(PostgresRoutineBodySqlParser.IsDistinctPredicateContext ctx) {
        return null;
    }

    private void emitDirectEquality(
            org.antlr.v4.runtime.ParserRuleContext context,
            PostgresRoutineBodySqlParser.ExpressionContext leftExpression,
            PostgresRoutineBodySqlParser.ExpressionContext rightExpression
    ) {
        ColumnRead left = singleColumn(leftExpression);
        ColumnRead right = singleColumn(rightExpression);
        if (left == null || right == null) return;
        emitter.addPredicate(events, context,
                existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE
                        : StructuredParseEventType.PREDICATE_EQUALITY,
                left.alias(), left.column(), right.alias(), right.column(),
                existsDepth > 0 ? "EXISTS" : currentJoinKind());
    }

    @Override
    public Void visitExistsPredicate(PostgresRoutineBodySqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(PostgresRoutineBodySqlParser.InSubqueryPredicateContext ctx) {
        ColumnRead outer = singleColumn(ctx.expression());
        ColumnRead inner = singleSelectColumn(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (outer != null && inner != null) emitter.addInSubquery(events, ctx,
                StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                List.of(outer.alias()), List.of(outer.column()),
                List.of(inner.alias()), List.of(inner.column()), "");
        return null;
    }

    @Override
    public Void visitTupleInSubqueryPredicate(PostgresRoutineBodySqlParser.TupleInSubqueryPredicateContext ctx) {
        List<ColumnRead> outer = ctx.expressionList().expression().stream().map(this::singleColumn).toList();
        List<ColumnRead> inner = selectColumns(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (!outer.isEmpty() && outer.size() == inner.size()
                && outer.stream().noneMatch(java.util.Objects::isNull)) {
            emitter.addInSubquery(events, ctx, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                    outer.stream().map(ColumnRead::alias).toList(),
                    outer.stream().map(ColumnRead::column).toList(),
                    inner.stream().map(ColumnRead::alias).toList(),
                    inner.stream().map(ColumnRead::column).toList(), "");
        }
        return null;
    }

    @Override public Void visitLiteralInPredicate(
            PostgresRoutineBodySqlParser.LiteralInPredicateContext ctx) { return null; }
    @Override public Void visitLikePredicate(
            PostgresRoutineBodySqlParser.LikePredicateContext ctx) { return null; }
}
