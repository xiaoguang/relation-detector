package com.relationdetector.postgres.routine;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/** Projection and write-mapping traversal for PostgreSQL routine bodies. */
abstract class PostgresRoutineWriteSupport extends PostgresRoutineControlSupport {
    PostgresRoutineWriteSupport(SqlStatementRecord statement) { super(statement); }

    @Override
    public Void visitInsertSelectStatement(PostgresRoutineBodySqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        visit(ctx.selectStatement());
        List<PostgresRoutineBodySqlParser.SelectItemContext> items =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        for (int index = 0; index < Math.min(targetColumns.size(), items.size()); index++) {
            for (ExpressionAnalysis source : selectItemAnalyses(items.get(index))) {
                addWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, items.get(index),
                        "", targetTable, targetColumns.get(index), source, "INSERT_SELECT");
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(PostgresRoutineBodySqlParser.UpdateStatementContext ctx) {
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        emitter.addWrite(events, ctx.tablePrimary(), StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        if (ctx.fromClause() != null) visit(ctx.fromClause());
        for (PostgresRoutineBodySqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1
                    ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression())) {
                addWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, assignmentAlias,
                        targetTable, targetColumn, source, "UPDATE_SET");
            }
        }
        if (ctx.whereClause() != null) visit(ctx.whereClause());
        return null;
    }

    @Override
    public Void visitMergeStatement(PostgresRoutineBodySqlParser.MergeStatementContext ctx) {
        PostgresRoutineBodySqlParser.TablePrimaryContext target = ctx.tablePrimary(0);
        PostgresRoutineBodySqlParser.TablePrimaryContext source = ctx.tablePrimary(1);
        visit(target);
        String targetAlias = targetAlias(target);
        String targetTable = targetTable(target);
        emitter.addWrite(events, target, StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        visit(source);
        joinKinds.push("MERGE_OR_USING");
        visit(ctx.predicate());
        joinKinds.pop();
        for (PostgresRoutineBodySqlParser.MergeWhenClauseContext clause : ctx.mergeWhenClause()) {
            PostgresRoutineBodySqlParser.MergeActionContext action = clause.mergeAction();
            if (action instanceof PostgresRoutineBodySqlParser.MergeUpdateActionContext update) {
                emitMergeUpdateMappings(update.assignmentList(), targetAlias, targetTable);
            } else if (action instanceof PostgresRoutineBodySqlParser.MergeInsertActionContext insert) {
                emitMergeInsertMappings(insert, targetAlias, targetTable);
            }
        }
        return null;
    }

    private void emitMergeUpdateMappings(PostgresRoutineBodySqlParser.AssignmentListContext assignments,
            String targetAlias, String targetTable) {
        for (PostgresRoutineBodySqlParser.AssignmentContext assignment : assignments.assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1
                    ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression())) {
                addWriteMapping(StructuredParseEventType.MERGE_WRITE_MAPPING, assignment, assignmentAlias,
                        targetTable, targetColumn, source, "MERGE_UPDATE");
            }
        }
    }

    private void emitMergeInsertMappings(PostgresRoutineBodySqlParser.MergeInsertActionContext insert,
            String targetAlias, String targetTable) {
        List<String> targetColumns = insert.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        List<PostgresRoutineBodySqlParser.ExpressionContext> values = insert.expressionList().expression();
        for (int index = 0; index < Math.min(targetColumns.size(), values.size()); index++) {
            for (ExpressionAnalysis source : writeAnalyses(values.get(index))) {
                addWriteMapping(StructuredParseEventType.MERGE_WRITE_MAPPING, insert, targetAlias,
                        targetTable, targetColumns.get(index), source, "MERGE_INSERT");
            }
        }
    }

    protected void emitProjectionItems(
            PostgresRoutineBodySqlParser.SelectListContext ctx,
            ProjectionOwner owner) {
        List<PostgresRoutineBodySqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            PostgresRoutineBodySqlParser.SelectItemContext item = items.get(index);
            String output = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (output.isBlank()) continue;
            for (ExpressionAnalysis source : selectItemAnalyses(item)) {
                if (!source.sources().isEmpty()) {
                    emitter.addProjection(events, item, StructuredParseEventType.PROJECTION_ITEM,
                            owner.alias(), output, source.aliases(), source.columns(),
                            source.transform(), source.flowKind());
                }
            }
        }
    }

    @Override
    protected List<ExpressionAnalysis> selectItemAnalyses(PostgresRoutineBodySqlParser.SelectItemContext item) {
        if (item.expression() != null) return writeAnalyses(item.expression());
        if (item.booleanProjection() == null) return List.of();
        ExpressionAnalysis combined = ExpressionAnalysis.empty();
        for (PostgresRoutineBodySqlParser.ExpressionContext expression
                : item.booleanProjection().expression()) {
            combined = ExpressionAnalysis.combine(LineageTransformType.FUNCTION_CALL,
                    LineageFlowKind.VALUE, combined, analyze(expression));
        }
        return combined.sources().isEmpty() ? List.of() : List.of(new ExpressionAnalysis(
                combined.sources(), LineageTransformType.FUNCTION_CALL, LineageFlowKind.VALUE));
    }

    private void addWriteMapping(StructuredParseEventType type, ParserRuleContext context,
            String targetAlias, String targetTable, String targetColumn,
            ExpressionAnalysis source, String mappingKind) {
        if (!source.sources().isEmpty()) {
            emitter.addWrite(events, context, type, "", "", "", targetAlias,
                    targetTable, targetColumn, mappingKind, source.aliases(), source.columns(),
                    source.transform(), source.flowKind());
        }
    }
}
