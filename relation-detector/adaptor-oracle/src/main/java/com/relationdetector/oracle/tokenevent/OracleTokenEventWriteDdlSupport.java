package com.relationdetector.oracle.tokenevent;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/** Projection, write mapping and DDL event traversal for Oracle token-event SQL. */
abstract class OracleTokenEventWriteDdlSupport extends OracleTokenEventControlSupport {
    OracleTokenEventWriteDdlSupport(SqlStatementRecord statement) {
        super(statement);
    }

    @Override
    public Void visitInsertSelectStatement(OracleRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        visit(ctx.selectStatement());
        List<OracleRelationSqlParser.SelectItemContext> items =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        for (int index = 0; index < Math.min(targetColumns.size(), items.size()); index++) {
            OracleRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) continue;
            for (OracleExpressionAnalysis source : writeAnalyses(item.expression())) {
                addWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, item, "", targetTable,
                        targetColumns.get(index), resolveCurrentScope(source), "INSERT_SELECT");
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(OracleRelationSqlParser.UpdateStatementContext ctx) {
        visit(ctx.tablePrimary());
        String targetAlias = targetAlias(ctx.tablePrimary());
        String targetTable = targetTable(ctx.tablePrimary());
        emitWriteTarget(ctx.tablePrimary(), targetAlias, targetTable);
        for (OracleRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            emitAssignmentMapping(assignment, targetAlias, targetTable,
                    StructuredParseEventType.UPDATE_ASSIGNMENT, "UPDATE_SET");
        }
        if (ctx.whereClause() != null) visit(ctx.whereClause());
        return null;
    }

    @Override
    public Void visitMergeStatement(OracleRelationSqlParser.MergeStatementContext ctx) {
        OracleRelationSqlParser.TablePrimaryContext target = ctx.tablePrimary(0);
        OracleRelationSqlParser.TablePrimaryContext source = ctx.tablePrimary(1);
        visit(target);
        visit(source);
        String targetAlias = targetAlias(target);
        String targetTable = targetTable(target);
        emitWriteTarget(target, targetAlias, targetTable);
        visit(ctx.predicate());
        for (OracleRelationSqlParser.MergeWhenClauseContext clause : ctx.mergeWhenClause()) {
            if (clause.mergeAction() instanceof OracleRelationSqlParser.MergeUpdateActionContext updateAction) {
                for (OracleRelationSqlParser.AssignmentContext assignment
                        : updateAction.assignmentList().assignment()) {
                    emitAssignmentMapping(assignment, targetAlias, targetTable,
                            StructuredParseEventType.MERGE_WRITE_MAPPING, "MERGE_UPDATE_SET");
                }
            }
        }
        return null;
    }

    @Override
    public Void visitCreateTableStatement(OracleRelationSqlParser.CreateTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        ctx.tableElement().forEach(this::visit);
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(OracleRelationSqlParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(OracleRelationSqlParser.TableForeignKeyContext ctx) {
        addForeignKeyEvents(currentDdlTable(), identifiers(ctx.identifierList(0)),
                qualifiedName(ctx.qualifiedName()), identifiers(ctx.identifierList(1)), ctx);
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(OracleRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        }
        return null;
    }

    @Override
    public Void visitUniqueConstraint(OracleRelationSqlParser.UniqueConstraintContext ctx) {
        for (String column : identifiers(ctx.identifierList())) {
            addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        }
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(OracleRelationSqlParser.TableIndexConstraintContext ctx) {
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(currentDdlTable(), column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        }
        return null;
    }

    @Override
    public Void visitColumnDefinition(OracleRelationSqlParser.ColumnDefinitionContext ctx) {
        String table = currentDdlTable();
        String column = clean(ctx.identifier().getText());
        addDdlColumnEvent(table, column, ctx);
        for (OracleRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            OracleRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
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
    public Void visitCreateIndexStatement(OracleRelationSqlParser.CreateIndexStatementContext ctx) {
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (String column : safeIndexColumns(ctx.indexPartList())) {
            addIndexEvent(qualifiedName(ctx.qualifiedName()), column, role, kind, ctx);
        }
        return null;
    }

    protected void emitProjectionItems(
            OracleRelationSqlParser.SelectListContext ctx,
            ProjectionOwner owner) {
        List<OracleRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            OracleRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) continue;
            String output = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (output.isBlank()) continue;
            for (OracleExpressionAnalysis raw : writeAnalyses(item.expression())) {
                OracleExpressionAnalysis source = resolveCurrentScope(raw);
                if (!source.sources().isEmpty()) {
                    emitter.addProjection(events, item, StructuredParseEventType.PROJECTION_ITEM,
                            owner.alias(), output, source.aliases(), source.columns(),
                            source.transform(), source.flowKind());
                }
            }
        }
    }

    private void emitWriteTarget(
            OracleRelationSqlParser.TablePrimaryContext target,
            String targetAlias,
            String targetTable) {
        emitter.addWrite(events, target, StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
    }

    private void emitAssignmentMapping(
            OracleRelationSqlParser.AssignmentContext assignment,
            String targetAlias,
            String targetTable,
            StructuredParseEventType eventType,
            String mappingKind) {
        List<String> targetParts = parts(assignment.qualifiedName());
        String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
        String assignmentAlias = targetParts.size() > 1
                ? targetParts.get(targetParts.size() - 2) : targetAlias;
        for (OracleExpressionAnalysis source : writeAnalyses(assignment.expression())) {
            addWriteMapping(eventType, assignment, assignmentAlias, targetTable, targetColumn,
                    resolveCurrentScope(source), mappingKind);
        }
    }

    private void addWriteMapping(
            StructuredParseEventType eventType,
            ParserRuleContext context,
            String targetAlias,
            String targetTable,
            String targetColumn,
            OracleExpressionAnalysis source,
            String mappingKind) {
        if (!source.sources().isEmpty()) {
            emitter.addWrite(events, context, eventType, "", "", "", targetAlias,
                    targetTable, targetColumn, mappingKind, source.aliases(), source.columns(),
                    source.transform(), source.flowKind());
        }
    }

    private void addForeignKeyEvents(String sourceTable, List<String> sourceColumns,
            String targetTable, List<String> targetColumns, ParserRuleContext ctx) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        emitter.addIndexEvent(events, ctx, table, column, role, kind);
    }

    private void addDdlColumnEvent(String table, String column, ParserRuleContext ctx) {
        emitter.addDdlColumnEvent(events, ctx, table, column);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> identifiers(OracleRelationSqlParser.IdentifierListContext ctx) {
        return ctx == null ? List.of()
                : ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(OracleRelationSqlParser.IndexPartListContext ctx) {
        if (ctx == null) return List.of();
        List<String> columns = new ArrayList<>();
        for (OracleRelationSqlParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) columns.add(clean(part.identifier().getText()));
        }
        return columns;
    }
}
