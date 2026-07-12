package com.relationdetector.mysql.tokenevent;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.PredicateGuard;

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
    public Void visitTriggerStartStatement(MySqlRelationSqlParser.TriggerStartStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        emitter.addRowset(events, ctx, StructuredParseEventType.TRIGGER_TARGET_TABLE,
                "ON", targetTable, baseName(targetTable), "", "", targetTable, "");
        for (String pseudo : List.of("NEW", "OLD")) {
            emitter.addRowset(events, ctx, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                    "", pseudo, pseudo, pseudo, pseudo, targetTable, "");
        }
        return null;
    }

    @Override
    public Void visitRoutineStartStatement(MySqlRelationSqlParser.RoutineStartStatementContext ctx) {
        ctx.routineParameterList().routineParameter().forEach(parameter ->
                nonColumnIdentifiers.add(normalize(parameter.identifier().getText())));
        return null;
    }

    @Override
    public Void visitDeclarationStatement(MySqlRelationSqlParser.DeclarationStatementContext ctx) {
        nonColumnIdentifiers.add(normalize(ctx.identifier().getText()));
        return null;
    }

    @Override
    public Void visitCursorDeclarationStatement(MySqlRelationSqlParser.CursorDeclarationStatementContext ctx) {
        nonColumnIdentifiers.add(normalize(ctx.identifier().getText()));
        return visit(ctx.selectStatement());
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
            visit(ctx.selectList());
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
        if (ctx.comparisonOperator().EQ() == null) {
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
    public Void visitAndPredicate(MySqlRelationSqlParser.AndPredicateContext ctx) {
        if (ctx.getParent() instanceof MySqlRelationSqlParser.AndPredicateContext) return visitChildren(ctx);
        List<PredicateGuard> guards = predicateGuards(ctx);
        withPredicateGuards(guards, 0, () -> visitChildren(ctx));
        return null;
    }

    private List<PredicateGuard> predicateGuards(MySqlRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof MySqlRelationSqlParser.AndPredicateContext and) {
            List<PredicateGuard> result = new java.util.ArrayList<>();
            result.addAll(predicateGuards(and.predicate(0)));
            result.addAll(predicateGuards(and.predicate(1)));
            return List.copyOf(result);
        }
        if (!(predicate instanceof MySqlRelationSqlParser.ComparisonPredicateContext comparison)
                || comparison.comparisonOperator().EQ() == null) return List.of();
        String defaultQualifier = enclosingSingleProjectionQualifier(comparison);
        ColumnRead left = singleColumn(comparison.expression(0), defaultQualifier);
        ColumnRead right = singleColumn(comparison.expression(1), defaultQualifier);
        String leftLiteral = literalValue(comparison.expression(0));
        String rightLiteral = literalValue(comparison.expression(1));
        if (left != null && rightLiteral != null) return List.of(new PredicateGuard(
                new ExpressionSource(left.alias(), left.column()), "EQUALS", rightLiteral));
        if (right != null && leftLiteral != null) return List.of(new PredicateGuard(
                new ExpressionSource(right.alias(), right.column()), "EQUALS", leftLiteral));
        return List.of();
    }

    private String literalValue(MySqlRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof MySqlRelationSqlParser.LiteralExpressionContext literal)
            return cleanLiteral(literal.literal().getText());
        if (expression instanceof MySqlRelationSqlParser.ParenExpressionContext paren)
            return literalValue(paren.expression());
        return null;
    }

    private String cleanLiteral(String raw) {
        String value = raw == null ? "" : raw.strip();
        return value.length() >= 2 && value.startsWith("'") && value.endsWith("'")
                ? value.substring(1, value.length() - 1).replace("''", "'") : value;
    }

    private void withPredicateGuards(List<PredicateGuard> guards, int index, Runnable visitor) {
        if (index >= guards.size()) { visitor.run(); return; }
        emitter.withPredicateGuard(guards.get(index),
                () -> withPredicateGuards(guards, index + 1, visitor));
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
