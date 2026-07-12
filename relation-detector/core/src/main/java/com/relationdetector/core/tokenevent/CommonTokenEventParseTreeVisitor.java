package com.relationdetector.core.tokenevent;

import java.util.List;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;

/** Typed traversal facade for the portable common token-event grammar. */
public final class CommonTokenEventParseTreeVisitor extends CommonTokenEventWriteDdlSupport {
    public CommonTokenEventParseTreeVisitor(SqlStatementRecord statement) {
        super(statement);
    }

    public List<StructuredSqlEvent> collect(CommonRelationSqlParser.ScriptContext root) {
        visit(root);
        return List.copyOf(events);
    }

    @Override
    public Void visitCommonTableExpression(CommonRelationSqlParser.CommonTableExpressionContext ctx) {
        String name = clean(ctx.identifier().getText());
        if (!name.isBlank()) {
            cteNames.add(normalize(name));
            emitter.addRowset(events, ctx, StructuredParseEventType.CTE_DECLARATION,
                    "", name, name, "", name, "", "");
            emitter.addRowset(events, ctx, StructuredParseEventType.IGNORED_ROWSET,
                    "", name, name, "", name, "", "CTE_DECLARATION");
        }
        List<String> outputColumns = ctx.identifierList() == null
                ? List.of()
                : ctx.identifierList().identifier().stream().map(identifier -> clean(identifier.getText())).toList();
        projectionOwners.push(new ProjectionOwner(name, outputColumns));
        visit(ctx.selectStatement());
        projectionOwners.pop();
        return null;
    }

    @Override
    public Void visitQuerySpecification(CommonRelationSqlParser.QuerySpecificationContext ctx) {
        if (ctx.fromClause() != null) {
            visit(ctx.fromClause());
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        if (ctx.havingClause() != null) {
            visit(ctx.havingClause());
        }
        if (!projectionOwners.isEmpty()) {
            emitProjectionItems(ctx.selectList(), projectionOwners.peek());
        }
        return null;
    }

    @Override
    public Void visitNamedTablePrimary(CommonRelationSqlParser.NamedTablePrimaryContext ctx) {
        String qualified = qualifiedName(ctx.qualifiedName());
        String alias = ctx.tableAlias() == null ? "" : clean(ctx.tableAlias().identifier().getText());
        emitter.addRowset(events, ctx, StructuredParseEventType.ROWSET_REFERENCE,
                "FROM", qualified, baseName(qualified), alias, "", "", "");
        return null;
    }

    @Override
    public Void visitTableReference(CommonRelationSqlParser.TableReferenceContext ctx) {
        visit(ctx.tablePrimary());
        String leftAlias = rowsetAlias(ctx.tablePrimary());
        for (CommonRelationSqlParser.JoinClauseContext join : ctx.joinClause()) {
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
    public Void visitDerivedTablePrimary(CommonRelationSqlParser.DerivedTablePrimaryContext ctx) {
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
    public Void visitComparisonPredicate(CommonRelationSqlParser.ComparisonPredicateContext ctx) {
        if (ctx.comparisonOperator().EQ() == null) {
            return visitChildren(ctx);
        }
        ColumnRead left = singleColumn(ctx.expression(0));
        ColumnRead right = singleColumn(ctx.expression(1));
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
    public Void visitExistsPredicate(CommonRelationSqlParser.ExistsPredicateContext ctx) {
        existsDepth++;
        visit(ctx.selectStatement());
        existsDepth--;
        return null;
    }

    @Override
    public Void visitInSubqueryPredicate(CommonRelationSqlParser.InSubqueryPredicateContext ctx) {
        ColumnRead outer = singleColumn(ctx.expression());
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
    public Void visitTupleInSubqueryPredicate(CommonRelationSqlParser.TupleInSubqueryPredicateContext ctx) {
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
    public Void visitLiteralInPredicate(CommonRelationSqlParser.LiteralInPredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitLikePredicate(CommonRelationSqlParser.LikePredicateContext ctx) {
        return null;
    }

    @Override
    public Void visitScalarSubqueryExpression(CommonRelationSqlParser.ScalarSubqueryExpressionContext ctx) {
        visit(ctx.selectStatement());
        return null;
    }

    @Override
    public Void visitInsertSelectStatement(CommonRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        visit(ctx.selectStatement());
        List<CommonRelationSqlParser.SelectItemContext> selectItems =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        for (int index = 0; index < Math.min(targetColumns.size(), selectItems.size()); index++) {
            CommonRelationSqlParser.SelectItemContext item = selectItems.get(index);
            if (item.expression() != null) {
                for (ExpressionAnalysis source : writeAnalyses(item.expression())) {
                    addWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, item, targetTable,
                            targetColumns.get(index), "", source, "INSERT_SELECT");
                }
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(CommonRelationSqlParser.UpdateStatementContext ctx) {
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        emitter.addWrite(events, ctx.tablePrimary(), StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        for (CommonRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression())) {
                addWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, targetTable,
                        targetColumn, assignmentAlias, source, "UPDATE_SET");
            }
        }
        if (ctx.whereClause() != null) {
            visit(ctx.whereClause());
        }
        return null;
    }
}
