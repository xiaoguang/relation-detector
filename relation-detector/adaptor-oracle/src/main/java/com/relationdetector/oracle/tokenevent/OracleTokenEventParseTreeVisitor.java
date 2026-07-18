package com.relationdetector.oracle.tokenevent;

import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.PredicateGuard;

/**
 * CN: 遍历 Oracle compact grammar 的 SQL、DDL、trigger 和 routine contexts，维护 per-parse routine scope 并发射 token events；不调用 full-grammar 或扫描 raw SQL。
 * EN: Traverses SQL, DDL, trigger, and routine contexts from the compact Oracle grammar, maintains per-parse routine scope, and emits token events without full-grammar delegation or raw-SQL scanning.
 */
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
        if (ctx.routineParameterList() != null) {
            ctx.routineParameterList().routineParameter().forEach(parameter ->
                    routineScope.declare(clean(parameter.identifier().getText())));
        }
        ctx.routineHeaderToken().stream()
                .map(OracleRelationSqlParser.RoutineHeaderTokenContext::routineLocalDeclaration)
                .filter(java.util.Objects::nonNull)
                .forEach(declaration -> routineScope.declare(clean(declaration.identifier().getText())));
        return visitChildren(ctx);
    }

    @Override
    public Void visitDeclarationStatement(OracleRelationSqlParser.DeclarationStatementContext ctx) {
        routineScope.enterBlock();
        ctx.routineLocalDeclaration().forEach(declaration ->
                routineScope.declare(clean(declaration.identifier().getText())));
        return null;
    }

    @Override
    public Void visitCreateTriggerStatement(OracleRelationSqlParser.CreateTriggerStatementContext ctx) {
        routineScope.enterRoutine();
        String targetTable = qualifiedName(ctx.qualifiedName(1));
        String targetName = baseName(targetTable);
        emitter.addRowset(events, ctx, StructuredParseEventType.TRIGGER_TARGET_TABLE,
                "TRIGGER", targetTable, targetName, "", "", targetTable, "");
        emitter.addRowset(events, ctx, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                "TRIGGER", targetTable, targetName, "NEW", "NEW", targetTable, "");
        emitter.addRowset(events, ctx, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                "TRIGGER", targetTable, targetName, "OLD", "OLD", targetTable, "");
        return null;
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
    public Void visitAndPredicate(OracleRelationSqlParser.AndPredicateContext ctx) {
        if (ctx.getParent() instanceof OracleRelationSqlParser.AndPredicateContext) return visitChildren(ctx);
        List<PredicateGuard> guards = predicateGuards(ctx);
        withPredicateGuards(guards, 0, () -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitCaseExpression(OracleRelationSqlParser.CaseExpressionContext ctx) {
        OracleColumnRead selector = ctx.expression().isEmpty() ? null : singleColumn(ctx.expression(0));
        if (selector != null) visit(ctx.expression(0));
        for (OracleRelationSqlParser.CaseWhenClauseContext clause : ctx.caseWhenClause()) {
            List<PredicateGuard> guards = selector == null
                    ? predicateGuards(clause.predicate())
                    : literalValue(clause.predicate()) == null ? List.of()
                            : List.of(new PredicateGuard(
                                    new ExpressionSource(selector.alias(), selector.column()),
                                    "EQUALS", literalValue(clause.predicate())));
            withPredicateGuards(guards, 0, () -> {
                visit(clause.predicate());
                visit(clause.expression());
            });
        }
        int outerStart = selector == null ? 0 : 1;
        for (int index = outerStart; index < ctx.expression().size(); index++) {
            visit(ctx.expression(index));
        }
        return null;
    }

    private List<PredicateGuard> predicateGuards(OracleRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof OracleRelationSqlParser.AndPredicateContext and) {
            List<PredicateGuard> result = new java.util.ArrayList<>();
            result.addAll(predicateGuards(and.predicate(0)));
            result.addAll(predicateGuards(and.predicate(1)));
            return List.copyOf(result);
        }
        if (!(predicate instanceof OracleRelationSqlParser.ComparisonPredicateContext comparison)
                || comparison.comparisonOperator().EQ() == null) return List.of();
        OracleColumnRead left = singleColumn(comparison.expression(0));
        OracleColumnRead right = singleColumn(comparison.expression(1));
        String leftLiteral = literalValue(comparison.expression(0));
        String rightLiteral = literalValue(comparison.expression(1));
        if (left != null && rightLiteral != null) return List.of(new PredicateGuard(
                new ExpressionSource(left.alias(), left.column()), "EQUALS", rightLiteral));
        if (right != null && leftLiteral != null) return List.of(new PredicateGuard(
                new ExpressionSource(right.alias(), right.column()), "EQUALS", leftLiteral));
        return List.of();
    }

    private String literalValue(OracleRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof OracleRelationSqlParser.LiteralExpressionContext literal)
            return cleanLiteral(literal.literal().getText());
        if (expression instanceof OracleRelationSqlParser.ParenExpressionContext paren)
            return literalValue(paren.expression());
        return null;
    }

    private String literalValue(OracleRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof OracleRelationSqlParser.ExpressionPredicateContext expression) {
            return literalValue(expression.expression());
        }
        if (predicate instanceof OracleRelationSqlParser.ParenPredicateContext paren) {
            return literalValue(paren.predicate());
        }
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
