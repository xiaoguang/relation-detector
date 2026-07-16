package com.relationdetector.postgres.tokenevent;

import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.ParserRuleContext;
import com.relationdetector.postgres.plpgsql.tokenevent.TokenEventPlPgSqlBodyParser;
import com.relationdetector.postgres.routine.PlPgSqlStringBody;
import com.relationdetector.postgres.routine.PostgresRoutineDescriptor;
import com.relationdetector.postgres.routine.PostgresRoutineBody;
import com.relationdetector.postgres.routine.PostgresRoutineLanguageDispatcher;
import com.relationdetector.postgres.routine.PostgresRoutineAttributes;
import com.relationdetector.postgres.routine.PostgresRoutineStatementFactory;
import com.relationdetector.postgres.routine.SqlAtomicBody;
import com.relationdetector.postgres.routine.SqlStringBody;
import com.relationdetector.postgres.routine.UnsupportedRoutineBody;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.parse.ExpressionSource;
import com.relationdetector.contracts.parse.PredicateGuard;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.postgres.common.PostgresSetProjectionLayout;

/**
 *
 * Typed traversal facade for the PostgreSQL token-event structural grammar.
 */
public final class PostgresTokenEventParseTreeVisitor extends PostgresTokenEventWriteDdlSupport {
    private final boolean allowRoutineDispatch;

    public PostgresTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        this(statement, true);
    }

    PostgresTokenEventParseTreeVisitor(SqlStatementRecord statement, boolean allowRoutineDispatch) {
        super(statement);
        this.allowRoutineDispatch = allowRoutineDispatch;
    }

    public List<StructuredSqlEvent> collect(PostgresRelationSqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    public List<WarningMessage> warnings() {
        return List.copyOf(warnings);
    }

    @Override
    public Void visitSelectStatement(PostgresRelationSqlParser.SelectStatementContext ctx) {
        if (ctx.withClause() != null) visit(ctx.withClause());
        List<PostgresRelationSqlParser.QuerySpecificationContext> branches = queryBranches(ctx);
        if (ctx.setOperation().isEmpty() || projectionOwners.isEmpty()) {
            branches.forEach(this::visit);
            return null;
        }

        ProjectionOwner owner = projectionOwners.pop();
        List<String> firstColumns = branches.get(0).selectList().selectItem().stream()
                .map(this::outputColumn).toList();
        PostgresSetProjectionLayout layout = PostgresSetProjectionLayout.resolve(
                owner.columns(), firstColumns,
                branches.stream().map(this::setBranchArity).toList());
        projectionOwners.push(new ProjectionOwner(owner.alias(), layout.columns()));
        try {
            if (!layout.arityMatches()) {
                warnings.add(WarningMessage.warn(WarningType.PARSE_WARNING,
                        "POSTGRES_SET_OPERATION_ARITY_MISMATCH",
                        "PostgreSQL set-operation branches do not share one projection arity",
                        statement.sourceName(), emitter.line(ctx)));
            }
            branches.forEach(this::visit);
        } finally {
            projectionOwners.pop();
            projectionOwners.push(owner);
        }
        return null;
    }

    private int setBranchArity(PostgresRelationSqlParser.QuerySpecificationContext branch) {
        var items = branch.selectList().selectItem();
        return PostgresSetProjectionLayout.branchArity(
                items.size(), items.stream().anyMatch(item -> item.STAR() != null));
    }

    @Override
    public Void visitRoutineDeclarationStatement(PostgresRelationSqlParser.RoutineDeclarationStatementContext ctx) {
        if (!allowRoutineDispatch) return null;
        String quotedBody = ctx.routineDeclarationElement().stream()
                .filter(element -> element.DOLLAR_QUOTED_STRING() != null)
                .map(element -> element.DOLLAR_QUOTED_STRING().getText())
                .findFirst().orElse("");
        String language = ctx.routineDeclarationElement().stream()
                .filter(element -> element.LANGUAGE() != null && element.identifier() != null)
                .map(element -> clean(element.identifier().getText()).toLowerCase(Locale.ROOT))
                .findFirst().orElse("");
        String objectType = ctx.PROCEDURE() == null ? "FUNCTION" : "PROCEDURE";
        String objectName = qualifiedName(ctx.qualifiedName());
        String body = unquoteDollarBody(quotedBody);
        int stringBodyLine = Math.toIntExact(statement.startLine() + Math.max(0,
                ctx.routineDeclarationElement().stream()
                        .filter(element -> element.DOLLAR_QUOTED_STRING() != null)
                        .mapToInt(element -> element.getStart().getLine()).findFirst().orElse(1) - 1));
        PostgresRoutineBody routineBody;
        if (ctx.routineSqlAtomicBody() != null) {
            var statements = ctx.routineSqlAtomicBody().atomicSqlStatement().stream()
                    .map(item -> PostgresRoutineStatementFactory.fromContext(statement, item))
                    .toList();
            int bodyLine = Math.toIntExact(statement.startLine()
                    + ctx.routineSqlAtomicBody().getStart().getLine() - 1L);
            routineBody = new SqlAtomicBody(statements, bodyLine);
        } else if (!body.isBlank()) {
            routineBody = switch (language) {
                case "plpgsql" -> new PlPgSqlStringBody(body, stringBodyLine);
                case "sql", "" -> new SqlStringBody(body, stringBodyLine);
                default -> new UnsupportedRoutineBody(language, stringBodyLine);
            };
        } else {
            return null;
        }
        var outcome = new PostgresRoutineLanguageDispatcher(new TokenEventPlPgSqlBodyParser()).dispatch(
                new PostgresRoutineDescriptor(language, routineBody, objectType, objectName,
                        statement.attributes()),
                PostgresRoutineAttributes.withNonColumnIdentifiers(statement,
                        ctx.routineParameterList().routineParameter().stream()
                                .map(parameter -> clean(parameter.identifier().getText())).toList()),
                null, new PostgresTokenEventStructuredSqlParser(false));
        events.addAll(outcome.events());
        warnings.addAll(outcome.warnings());
        return null;
    }

    @Override
    public Void visitCommonTableExpression(PostgresRelationSqlParser.CommonTableExpressionContext ctx) {
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
    public Void visitQuerySpecification(PostgresRelationSqlParser.QuerySpecificationContext ctx) {
        queryScopes.push(new QueryScope());
        try {
            if (ctx.fromClause() != null) visit(ctx.fromClause());
            if (ctx.whereClause() != null) visit(ctx.whereClause());
            if (ctx.groupByClause() != null) visit(ctx.groupByClause());
            if (ctx.havingClause() != null) visit(ctx.havingClause());
            if (ctx.orderByClause() != null) visit(ctx.orderByClause());
            if (!projectionOwners.isEmpty()) {
                emitProjectionItems(ctx.selectList(), projectionOwners.peek());
                // Projection mapping can be absent (for example an unaliased expression in a
                // UNION branch), but nested query structure must still be traversed.
                visit(ctx.selectList());
            } else visit(ctx.selectList());
        } finally {
            queryScopes.pop();
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(PostgresRelationSqlParser.NamedTablePrimaryContext ctx) {
        String qualified = qualifiedName(ctx.qualifiedName());
        String table = baseName(qualified);
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        emitter.addRowset(events, ctx, StructuredParseEventType.ROWSET_REFERENCE,
                "FROM", qualified, table, alias, "", "", "");
        registerCurrentRowset(alias.isBlank() ? table : alias);
        return null;
    }

    @Override
    public Void visitTableReference(PostgresRelationSqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (PostgresRelationSqlParser.JoinClauseContext join : ctx.joinClause()) {
            String rightAlias = rowsetAlias(join.tablePrimary());
            visit(join.tablePrimary());
            if (join.identifierList() != null) emitter.addJoinUsing(events, join, leftAlias, rightAlias,
                    join.identifierList().identifier().stream().map(identifier -> clean(identifier.getText())).toList());
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
    public Void visitDerivedTablePrimary(PostgresRelationSqlParser.DerivedTablePrimaryContext ctx) {
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
    public Void visitRowsFromTablePrimary(PostgresRelationSqlParser.RowsFromTablePrimaryContext ctx) {
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) registerCurrentRowset(alias);
        emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", "ROWS FROM", "ROWS", alias, "ROWS", "", "FUNCTION_ROWSET");
        return null;
    }

    @Override
    public Void visitFunctionRowsetPrimary(PostgresRelationSqlParser.FunctionRowsetPrimaryContext ctx) {
        String function = baseName(qualifiedName(ctx.qualifiedName())).toUpperCase(Locale.ROOT);
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        if (!alias.isBlank()) registerCurrentRowset(alias);
        emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                "", qualifiedName(ctx.qualifiedName()), function, alias, function, "", "FUNCTION_ROWSET");
        return null;
    }

    @Override
    public Void visitComparisonPredicate(PostgresRelationSqlParser.ComparisonPredicateContext ctx) {
        if (ctx.comparisonOperator().EQ() == null) return visitChildren(ctx);
        emitColumnEquality(ctx.expression(0), ctx.expression(1), ctx);
        return null;
    }

    @Override
    public Void visitAndPredicate(PostgresRelationSqlParser.AndPredicateContext ctx) {
        if (ctx.getParent() instanceof PostgresRelationSqlParser.AndPredicateContext) {
            return visitChildren(ctx);
        }
        List<PredicateGuard> guards = predicateGuards(ctx);
        withPredicateGuards(guards, 0, () -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitCaseExpression(PostgresRelationSqlParser.CaseExpressionContext ctx) {
        ColumnRead selector = ctx.expression().isEmpty() ? null : singleColumn(ctx.expression(0));
        if (selector != null) {
            visit(ctx.expression(0));
        }
        for (PostgresRelationSqlParser.CaseWhenClauseContext clause : ctx.caseWhenClause()) {
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

    private List<PredicateGuard> predicateGuards(PostgresRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof PostgresRelationSqlParser.AndPredicateContext and) {
            List<PredicateGuard> result = new java.util.ArrayList<>();
            result.addAll(predicateGuards(and.predicate(0)));
            result.addAll(predicateGuards(and.predicate(1)));
            return List.copyOf(result);
        }
        if (!(predicate instanceof PostgresRelationSqlParser.ComparisonPredicateContext comparison)
                || comparison.comparisonOperator().EQ() == null) {
            return List.of();
        }
        ColumnRead left = singleColumn(comparison.expression(0));
        ColumnRead right = singleColumn(comparison.expression(1));
        String leftLiteral = literalValue(comparison.expression(0));
        String rightLiteral = literalValue(comparison.expression(1));
        if (left != null && rightLiteral != null) {
            return List.of(new PredicateGuard(new ExpressionSource(left.alias(), left.column()),
                    "EQUALS", rightLiteral));
        }
        if (right != null && leftLiteral != null) {
            return List.of(new PredicateGuard(new ExpressionSource(right.alias(), right.column()),
                    "EQUALS", leftLiteral));
        }
        return List.of();
    }

    private String literalValue(PostgresRelationSqlParser.PredicateContext predicate) {
        if (predicate instanceof PostgresRelationSqlParser.ExpressionPredicateContext expression) {
            return literalValue(expression.expression());
        }
        if (predicate instanceof PostgresRelationSqlParser.ParenPredicateContext paren) {
            return literalValue(paren.predicate());
        }
        return null;
    }

    private String literalValue(PostgresRelationSqlParser.ExpressionContext expression) {
        if (expression instanceof PostgresRelationSqlParser.LiteralExpressionContext literal) {
            return cleanLiteral(literal.literal().getText());
        }
        if (expression instanceof PostgresRelationSqlParser.ParenExpressionContext paren) {
            return literalValue(paren.expression());
        }
        return null;
    }

    private String cleanLiteral(String raw) {
        String value = raw == null ? "" : raw.strip();
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'")) {
            return value.substring(1, value.length() - 1).replace("''", "'");
        }
        return value;
    }

    private void withPredicateGuards(List<PredicateGuard> guards, int index, Runnable visitor) {
        if (index >= guards.size()) {
            visitor.run();
            return;
        }
        emitter.withPredicateGuard(guards.get(index),
                () -> withPredicateGuards(guards, index + 1, visitor));
    }

    @Override
    public Void visitIsNotDistinctPredicate(PostgresRelationSqlParser.IsNotDistinctPredicateContext ctx) {
        emitColumnEquality(ctx.expression(0), ctx.expression(1), ctx);
        return null;
    }

    private void emitColumnEquality(PostgresRelationSqlParser.ExpressionContext leftExpression,
            PostgresRelationSqlParser.ExpressionContext rightExpression, ParserRuleContext ctx) {
        ColumnRead left = singleColumn(leftExpression);
        ColumnRead right = singleColumn(rightExpression);
        if (left != null && right != null) emitter.addPredicate(events, ctx,
                existsDepth > 0 ? StructuredParseEventType.EXISTS_PREDICATE
                        : StructuredParseEventType.PREDICATE_EQUALITY,
                left.alias(), left.column(), right.alias(), right.column(),
                existsDepth > 0 ? "EXISTS" : currentJoinKind());
    }

    @Override
    public Void visitExistsPredicate(PostgresRelationSqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(PostgresRelationSqlParser.InSubqueryPredicateContext ctx) {
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
    public Void visitTupleInSubqueryPredicate(PostgresRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
        List<ColumnRead> outer = ctx.expressionList().expression().stream().map(this::singleColumn).toList();
        List<ColumnRead> inner = selectColumns(ctx.selectStatement());
        visit(ctx.selectStatement());
        if (!outer.isEmpty() && outer.size() == inner.size()
                && outer.stream().noneMatch(java.util.Objects::isNull)) emitter.addInSubquery(events, ctx,
                StructuredParseEventType.TUPLE_IN_SUBQUERY_PREDICATE,
                outer.stream().map(ColumnRead::alias).toList(), outer.stream().map(ColumnRead::column).toList(),
                inner.stream().map(ColumnRead::alias).toList(), inner.stream().map(ColumnRead::column).toList(), "");
        return null;
    }

    @Override public Void visitLiteralInPredicate(PostgresRelationSqlParser.LiteralInPredicateContext ctx) { return null; }
    @Override public Void visitLikePredicate(PostgresRelationSqlParser.LikePredicateContext ctx) { return null; }

    @Override
    public Void visitScalarSubqueryExpression(PostgresRelationSqlParser.ScalarSubqueryExpressionContext ctx) {
        visit(ctx.selectStatement());
        return null;
    }
}
