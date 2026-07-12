package com.relationdetector.postgres.tokenevent;

import java.util.List;
import java.util.Locale;

import org.antlr.v4.runtime.ParserRuleContext;
import com.relationdetector.postgres.plpgsql.tokenevent.TokenEventPlPgSqlBodyParser;
import com.relationdetector.postgres.routine.PostgresRoutineBodyKind;
import com.relationdetector.postgres.routine.PostgresRoutineDescriptor;
import com.relationdetector.postgres.routine.PostgresRoutineLanguageDispatcher;
import com.relationdetector.postgres.routine.PostgresRoutineAttributes;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.model.WarningMessage;

/** Typed traversal facade for the PostgreSQL token-event structural grammar. */
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
        String body = unquoteDollarBody(quotedBody);
        if (body.isBlank()) return null;
        PostgresRoutineBodyKind kind = switch (language) {
            case "plpgsql" -> PostgresRoutineBodyKind.PLPGSQL;
            case "sql", "" -> PostgresRoutineBodyKind.SQL_STRING;
            default -> PostgresRoutineBodyKind.UNSUPPORTED_LANGUAGE;
        };
        String objectType = ctx.PROCEDURE() == null ? "FUNCTION" : "PROCEDURE";
        String objectName = qualifiedName(ctx.qualifiedName());
        int bodyLine = Math.toIntExact(statement.startLine() + Math.max(0,
                ctx.routineDeclarationElement().stream()
                        .filter(element -> element.DOLLAR_QUOTED_STRING() != null)
                        .mapToInt(element -> element.getStart().getLine()).findFirst().orElse(1) - 1));
        var outcome = new PostgresRoutineLanguageDispatcher(new TokenEventPlPgSqlBodyParser()).dispatch(
                new PostgresRoutineDescriptor(kind, language, body, bodyLine, objectType, objectName),
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
