package com.relationdetector.postgres.tokenevent;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/** INSERT/UPDATE/MERGE mappings, projections and DDL inventory for PostgreSQL token-event SQL. */
abstract class PostgresTokenEventWriteDdlSupport extends PostgresTokenEventExpressionSupport {
    PostgresTokenEventWriteDdlSupport(SqlStatementRecord statement) { super(statement); }

    @Override
    public Void visitInsertSelectStatement(PostgresRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targets = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        visit(ctx.selectStatement());
        List<PostgresRelationSqlParser.SelectItemContext> items =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        for (int index = 0; index < Math.min(targets.size(), items.size()); index++) {
            PostgresRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() != null) {
                for (ExpressionAnalysis source : writeAnalyses(item)) {
                    addWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, item, "",
                            targetTable, targets.get(index), source, "INSERT_SELECT");
                }
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(PostgresRelationSqlParser.UpdateStatementContext ctx) {
        if (ctx.withClause() != null) visit(ctx.withClause());
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        emitter.addWrite(events, ctx.tablePrimary(), StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        if (ctx.fromClause() != null) visit(ctx.fromClause());
        for (PostgresRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String column = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String alias = targetParts.size() > 1 ? targetParts.get(targetParts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression())) {
                addWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, alias,
                        targetTable, column, source, "UPDATE_SET");
            }
        }
        if (ctx.whereClause() != null) visit(ctx.whereClause());
        return null;
    }

    @Override
    public Void visitMergeStatement(PostgresRelationSqlParser.MergeStatementContext ctx) {
        if (ctx.withClause() != null) visit(ctx.withClause());
        PostgresRelationSqlParser.TablePrimaryContext target = ctx.tablePrimary(0);
        visit(target);
        String targetAlias = targetAlias(target);
        String targetTable = targetTable(target);
        emitter.addWrite(events, target, StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        visit(ctx.tablePrimary(1));
        joinKinds.push("MERGE_OR_USING");
        visit(ctx.predicate());
        joinKinds.pop();
        for (PostgresRelationSqlParser.MergeWhenClauseContext clause : ctx.mergeWhenClause()) {
            if (clause.mergeAction() instanceof PostgresRelationSqlParser.MergeUpdateActionContext action) {
                emitMergeUpdateMappings(action.assignmentList(), targetAlias, targetTable);
            } else if (clause.mergeAction() instanceof PostgresRelationSqlParser.MergeInsertActionContext action) {
                emitMergeInsertMappings(action, targetAlias, targetTable);
            }
        }
        return null;
    }

    @Override
    public Void visitCreateTableStatement(PostgresRelationSqlParser.CreateTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        ctx.tableElement().forEach(this::visit);
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(PostgresRelationSqlParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(PostgresRelationSqlParser.TableForeignKeyContext ctx) {
        addForeignKeyEvents(currentDdlTable(), identifiers(ctx.identifierList(0)),
                qualifiedName(ctx.qualifiedName()), identifiers(ctx.identifierList(1)), ctx);
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(PostgresRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList()))
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        return null;
    }

    @Override
    public Void visitUniqueConstraint(PostgresRelationSqlParser.UniqueConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList()))
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(PostgresRelationSqlParser.TableIndexConstraintContext ctx) {
        for (String column : safeIndexColumns(ctx.indexPartList()))
            addIndexEvent(currentDdlTable(), column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        return null;
    }

    @Override
    public Void visitColumnDefinition(PostgresRelationSqlParser.ColumnDefinitionContext ctx) {
        String table = currentDdlTable();
        String column = clean(ctx.identifier().getText());
        emitter.addDdlColumnEvent(events, ctx, table, column);
        for (PostgresRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            PostgresRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
            if (constraint == null) continue;
            if (constraint.PRIMARY() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (constraint.UNIQUE() != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (constraint.REFERENCES() != null) {
                addForeignKeyEvents(table, List.of(column), qualifiedName(constraint.qualifiedName()),
                        identifiers(constraint.identifierList()), constraint);
            }
        }
        return null;
    }

    @Override
    public Void visitCreateIndexStatement(PostgresRelationSqlParser.CreateIndexStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (String column : safeIndexColumns(ctx.indexPartList())) addIndexEvent(table, column, role, kind, ctx);
        return null;
    }

    private void emitMergeUpdateMappings(PostgresRelationSqlParser.AssignmentListContext assignments,
            String targetAlias, String targetTable) {
        for (PostgresRelationSqlParser.AssignmentContext assignment : assignments.assignment()) {
            List<String> parts = parts(assignment.qualifiedName());
            String column = parts.isEmpty() ? "" : parts.get(parts.size() - 1);
            String alias = parts.size() > 1 ? parts.get(parts.size() - 2) : targetAlias;
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression()))
                addWriteMapping(StructuredParseEventType.MERGE_WRITE_MAPPING, assignment, alias,
                        targetTable, column, source, "MERGE_UPDATE");
        }
    }

    private void emitMergeInsertMappings(PostgresRelationSqlParser.MergeInsertActionContext action,
            String targetAlias, String targetTable) {
        List<String> columns = action.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        List<PostgresRelationSqlParser.ExpressionContext> expressions = action.expressionList().expression();
        for (int index = 0; index < Math.min(columns.size(), expressions.size()); index++) {
            for (ExpressionAnalysis source : writeAnalyses(expressions.get(index)))
                addWriteMapping(StructuredParseEventType.MERGE_WRITE_MAPPING, action, targetAlias,
                        targetTable, columns.get(index), source, "MERGE_INSERT");
        }
    }

    protected void emitProjectionItems(PostgresRelationSqlParser.SelectListContext ctx, ProjectionOwner owner) {
        List<PostgresRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            PostgresRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) continue;
            String column = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (column.isBlank()) continue;
            for (ExpressionAnalysis source : writeAnalyses(item)) {
                if (!source.sources().isEmpty()) emitter.addProjection(events, item,
                        StructuredParseEventType.PROJECTION_ITEM, owner.alias(), column,
                        source.aliases(), source.columns(), source.transform(), source.flowKind());
            }
        }
    }

    private void addWriteMapping(StructuredParseEventType type, ParserRuleContext context,
            String targetAlias, String targetTable, String targetColumn,
            ExpressionAnalysis source, String mappingKind) {
        if (!source.sources().isEmpty()) emitter.addWrite(events, context, type, "", "", "", targetAlias,
                targetTable, targetColumn, mappingKind, source.aliases(), source.columns(),
                source.transform(), source.flowKind());
    }

    private String currentDdlTable() { return ddlTables.isEmpty() ? "" : ddlTables.peek(); }

    private List<String> identifiers(PostgresRelationSqlParser.IdentifierListContext ctx) {
        return ctx == null ? List.of()
                : ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(PostgresRelationSqlParser.IndexPartListContext ctx) {
        if (ctx == null) return List.of();
        List<String> columns = new ArrayList<>();
        for (PostgresRelationSqlParser.IndexPartContext part : ctx.indexPart())
            if (part.identifier() != null) columns.add(clean(part.identifier().getText()));
        return columns;
    }

    private void addForeignKeyEvents(String sourceTable, List<String> sourceColumns,
            String targetTable, List<String> targetColumns, ParserRuleContext ctx) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        emitter.addIndexEvent(events, ctx, table, column, role, kind);
    }
}
