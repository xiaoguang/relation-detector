package com.relationdetector.mysql.tokenevent;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Typed traversal facade for the MySQL token-event structural grammar. */
public final class MySqlTokenEventParseTreeVisitor extends MySqlTokenEventWriteDdlSupport {
    public MySqlTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        super(statement);
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
            emitter.addRowset(events, ctx, StructuredParseEventType.CTE_DECLARATION,
                    "", name, name, "", name, "", "");
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", name, name, "", name, "", "CTE_DECLARATION");
        }
        List<String> columns = ctx.identifierList() == null ? List.of()
                : ctx.identifierList().identifier().stream().map(identifier -> clean(identifier.getText())).toList();
        projectionOwners.push(new ProjectionOwner(name, columns));
        visit(ctx.selectStatement());
        projectionOwners.pop();
        return null;
    }

    @Override
    public Void visitQuerySpecification(MySqlRelationSqlParser.QuerySpecificationContext ctx) {
        if (ctx.fromClause() != null) visit(ctx.fromClause());
        if (ctx.whereClause() != null) visit(ctx.whereClause());
        if (ctx.havingClause() != null) visit(ctx.havingClause());
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
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        emitter.addRowset(events, ctx, StructuredParseEventType.ROWSET_REFERENCE,
                "FROM", qualified, baseName(qualified), alias, "", "", "");
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
    public Void visitDerivedTablePrimary(MySqlRelationSqlParser.DerivedTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) {
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", alias, alias, "", alias, "", "DERIVED_TABLE");
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
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", "JSON_TABLE", "JSON_TABLE", alias, "JSON_TABLE", "", "FUNCTION_ROWSET");
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
        String defaultQualifier = enclosingSingleProjectionQualifier(ctx);
        ColumnRead left = singleColumn(ctx.expression(0), defaultQualifier);
        ColumnRead right = singleColumn(ctx.expression(1), defaultQualifier);
        if (left == null || right == null) {
            return visitChildren(ctx);
        }
        emitter.addPredicate(events, ctx,
                existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE
                        : StructuredParseEventType.PREDICATE_EQUALITY,
                left.alias(), left.column(), right.alias(), right.column(),
                existsDepth > 0 ? "EXISTS" : currentJoinKind());
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
        ColumnRead outer = singleColumn(ctx.expression(), enclosingSingleProjectionQualifier(ctx));
        ColumnRead inner = singleSelectColumn(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (outer != null && inner != null) {
            emitter.addInSubquery(events, ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                    List.of(outer.alias()), List.of(outer.column()),
                    List.of(inner.alias()), List.of(inner.column()), "");
        }
        return null;
    }

    @Override
    public Void visitTupleInSubqueryPredicate(MySqlRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
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

    @Override
    public Void visitLiteralInPredicate(MySqlRelationSqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(MySqlRelationSqlParser.LikePredicateContext ctx) {
        return null;
    }
}
