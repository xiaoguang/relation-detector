package com.relationdetector.oracle.tokenevent;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Typed traversal facade for the Oracle token-event structural grammar. */
public final class OracleTokenEventParseTreeVisitor extends OracleTokenEventWriteDdlSupport {
    public OracleTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        super(statement);
    }

    public List<StructuredSqlEvent> collect(OracleRelationSqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitRoutineStartStatement(OracleRelationSqlParser.RoutineStartStatementContext ctx) {
        routineScope.enterRoutine();
        return visitChildren(ctx);
    }

    @Override
    public Void visitBlockEndStatement(OracleRelationSqlParser.BlockEndStatementContext ctx) {
        routineScope.leaveRoutineEnd(ctx.IF() != null || ctx.LOOP() != null
                || ctx.WHILE() != null || ctx.REPEAT() != null);
        return null;
    }

    @Override
    public Void visitCommonTableExpression(OracleRelationSqlParser.CommonTableExpressionContext ctx) {
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
    public Void visitQuerySpecification(OracleRelationSqlParser.QuerySpecificationContext ctx) {
        queryScopes.push(new QueryScope());
        try {
            if (ctx.fromClause() != null) visit(ctx.fromClause());
            if (ctx.whereClause() != null) visit(ctx.whereClause());
            if (ctx.havingClause() != null) visit(ctx.havingClause());
            if (!projectionOwners.isEmpty()) emitProjectionItems(ctx.selectList(), projectionOwners.peek());
            else visit(ctx.selectList());
        } finally {
            queryScopes.pop();
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(OracleRelationSqlParser.NamedTablePrimaryContext ctx) {
        String qualified = qualifiedName(ctx.qualifiedName());
        String table = baseName(qualified);
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        emitter.addRowset(events, ctx, StructuredParseEventType.ROWSET_REFERENCE,
                "FROM", qualified, table, alias, "", "", "");
        registerCurrentRowset(alias.isBlank() ? table : alias, qualified);
        return null;
    }

    @Override
    public Void visitTableReference(OracleRelationSqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (OracleRelationSqlParser.JoinClauseContext join : ctx.joinClause()) {
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
    public Void visitDerivedTablePrimary(OracleRelationSqlParser.DerivedTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) {
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", alias, alias, "", alias, "", "DERIVED_TABLE");
            registerCurrentRowset(alias, alias);
            projectionOwners.push(new ProjectionOwner(alias, List.of()));
            visit(ctx.selectStatement());
            projectionOwners.pop();
        } else {
            visit(ctx.selectStatement());
        }
        return null;
    }

    @Override
    public Void visitComparisonPredicate(OracleRelationSqlParser.ComparisonPredicateContext ctx) {
        if (ctx.comparisonOperator().EQ() == null) return visitChildren(ctx);
        OracleColumnRead left = singleColumn(ctx.expression(0));
        OracleColumnRead right = singleColumn(ctx.expression(1));
        if (left == null || right == null) return visitChildren(ctx);
        emitter.addPredicate(events, ctx,
                existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE
                        : StructuredParseEventType.PREDICATE_EQUALITY,
                left.alias(), left.column(), right.alias(), right.column(),
                existsDepth > 0 ? "EXISTS" : currentJoinKind());
        return null;
    }

    @Override
    public Void visitExistsPredicate(OracleRelationSqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(OracleRelationSqlParser.InSubqueryPredicateContext ctx) {
        OracleColumnRead outer = singleColumn(ctx.expression());
        OracleColumnRead inner = singleSelectColumn(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (outer != null && inner != null) {
            emitter.addInSubquery(events, ctx, StructuredParseEventType.IN_SUBQUERY_PREDICATE,
                    List.of(outer.alias()), List.of(outer.column()),
                    List.of(inner.alias()), List.of(inner.column()), "");
        }
        return null;
    }

    @Override
    public Void visitTupleInSubqueryPredicate(OracleRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
        List<OracleColumnRead> outer = ctx.expressionList().expression().stream()
                .map(this::singleColumn).toList();
        List<OracleColumnRead> inner = selectColumns(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (!outer.isEmpty() && outer.size() == inner.size()
                && outer.stream().noneMatch(java.util.Objects::isNull)) {
            emitter.addInSubquery(events, ctx, StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                    outer.stream().map(OracleColumnRead::alias).toList(),
                    outer.stream().map(OracleColumnRead::column).toList(),
                    inner.stream().map(OracleColumnRead::alias).toList(),
                    inner.stream().map(OracleColumnRead::column).toList(), "");
        }
        return null;
    }

    @Override
    public Void visitLiteralInPredicate(OracleRelationSqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(OracleRelationSqlParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitScalarSubqueryExpression(OracleRelationSqlParser.ScalarSubqueryExpressionContext ctx) {
        visit(ctx.selectStatement());
        return null;
    }
}
