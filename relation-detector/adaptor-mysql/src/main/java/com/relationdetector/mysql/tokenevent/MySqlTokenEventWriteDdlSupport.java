package com.relationdetector.mysql.tokenevent;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.mysql.ddl.MySqlIndexSemantics;
import com.relationdetector.mysql.ddl.MySqlIndexSemantics.IndexVisibility;
import com.relationdetector.mysql.ddl.MySqlIndexSemantics.Member;

/**
 *
 * Write/projection and DDL event traversal for MySQL token-event SQL.
 */
abstract class MySqlTokenEventWriteDdlSupport extends MySqlTokenEventControlSupport {
    MySqlTokenEventWriteDdlSupport(SqlStatementRecord statement) {
        super(statement);
    }

    @Override
    public Void visitInsertSelectStatement(MySqlRelationSqlParser.InsertSelectStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        visit(ctx.selectStatement());
        List<MySqlRelationSqlParser.SelectItemContext> items =
                ctx.selectStatement().querySpecification().selectList().selectItem();
        for (int index = 0; index < Math.min(targetColumns.size(), items.size()); index++) {
            MySqlRelationSqlParser.SelectItemContext item = items.get(index);
            for (ExpressionAnalysis source : writeAnalyses(item, "")) {
                emitWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, item, "", targetTable,
                        targetColumns.get(index), source, "INSERT_SELECT");
            }
        }
        return null;
    }

    @Override
    public Void visitInsertValuesStatement(MySqlRelationSqlParser.InsertValuesStatementContext ctx) {
        String targetTable = qualifiedName(ctx.qualifiedName());
        List<String> targetColumns = ctx.identifierList().identifier().stream()
                .map(identifier -> clean(identifier.getText())).toList();
        for (MySqlRelationSqlParser.ValueRowContext row : ctx.valueRow()) {
            if (row.expressionList() == null) {
                continue;
            }
            List<MySqlRelationSqlParser.ExpressionContext> values = row.expressionList().expression();
            int count = Math.min(targetColumns.size(), values.size());
            for (int index = 0; index < count; index++) {
                MySqlRelationSqlParser.ExpressionContext value = values.get(index);
                visit(value);
                for (ExpressionAnalysis source : writeAnalyses(value, "")) {
                    emitWriteMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, value, "",
                            targetTable, targetColumns.get(index), source, "INSERT_VALUES");
                }
            }
        }
        return null;
    }

    @Override
    public Void visitUpdateStatement(MySqlRelationSqlParser.UpdateStatementContext ctx) {
        if (ctx.withClause() != null) {
            visit(ctx.withClause());
        }
        for (MySqlRelationSqlParser.TableReferenceContext tableReference : ctx.tableReference()) {
            visit(tableReference);
        }
        MySqlRelationSqlParser.TablePrimaryContext targetPrimary = firstTablePrimary(ctx.tableReference());
        String targetAlias = targetPrimary == null ? "" : targetAlias(targetPrimary);
        String targetTable = targetPrimary == null ? "" : targetTable(targetPrimary);
        emitter.addWrite(events, targetPrimary == null ? ctx : targetPrimary,
                StructuredParseEventType.WRITE_TARGET, baseName(targetTable), targetTable,
                targetAlias, "", "", "", "", List.of(), List.of(),
                LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        List<UpdateTarget> targets = new ArrayList<>();
        for (MySqlRelationSqlParser.AssignmentContext assignment : ctx.assignmentList().assignment()) {
            List<String> targetParts = parts(assignment.qualifiedName());
            String targetColumn = targetParts.isEmpty() ? "" : targetParts.get(targetParts.size() - 1);
            String assignmentAlias = targetParts.size() > 1
                    ? targetParts.get(targetParts.size() - 2) : targetAlias;
            targets.add(new UpdateTarget(targetColumn, assignmentAlias));
            for (ExpressionAnalysis source : writeAnalyses(assignment.expression(), "")) {
                emitWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, assignment, assignmentAlias,
                        targetTable, targetColumn, source, "UPDATE_SET");
            }
        }
        if (ctx.whereClause() != null) {
            ExpressionAnalysis locator = locatorControl(ctx.whereClause().predicate(), targetAlias);
            for (UpdateTarget target : targets) {
                emitWriteMapping(StructuredParseEventType.UPDATE_ASSIGNMENT, ctx.whereClause(), target.alias(),
                        targetTable, target.column(), locator, "UPDATE_LOCATOR");
            }
            visit(ctx.whereClause());
        }
        return null;
    }

    private record UpdateTarget(String column, String alias) {
    }

    @Override
    public Void visitCreateTableStatement(MySqlRelationSqlParser.CreateTableStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        if (ctx.tableModifier().stream().anyMatch(modifier -> modifier.TEMPORARY() != null)) {
            emitter.addRowset(events, ctx, StructuredParseEventType.LOCAL_TEMP_TABLE_DECLARATION,
                    "", table, baseName(table), "", "", "", "");
        }
        ddlTables.push(table);
        ctx.tableElement().forEach(this::visit);
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(MySqlRelationSqlParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(MySqlRelationSqlParser.TableForeignKeyContext ctx) {
        addForeignKeyEvents(ctx, currentDdlTable(), identifiers(ctx.identifierList(0)),
                qualifiedName(ctx.qualifiedName()), identifiers(ctx.identifierList(1)));
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(MySqlRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        emitIndexEvidence(currentDdlTable(), identifiers(ctx.identifierList()).stream()
                .map(Member::fullColumn).toList(), true, IndexVisibility.VISIBLE, "PRIMARY_KEY", ctx);
        return null;
    }

    @Override
    public Void visitUniqueConstraint(MySqlRelationSqlParser.UniqueConstraintContext ctx) {
        emitIndexEvidence(currentDdlTable(), indexMembers(ctx.indexPartList()),
                true, visibility(ctx.indexVisibility()), "UNIQUE_CONSTRAINT", ctx);
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(MySqlRelationSqlParser.TableIndexConstraintContext ctx) {
        emitIndexEvidence(currentDdlTable(), indexMembers(ctx.indexPartList()),
                false, visibility(ctx.indexVisibility()), "CREATE_TABLE_INDEX", ctx);
        return null;
    }

    @Override
    public Void visitColumnDefinition(MySqlRelationSqlParser.ColumnDefinitionContext ctx) {
        String column = clean(ctx.identifier().getText());
        String table = currentDdlTable();
        addDdlColumnEvent(table, column, ctx);
        for (MySqlRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            MySqlRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
            if (constraint == null) {
                continue;
            }
            if (constraint.PRIMARY() != null) {
                emitIndexEvidence(table, List.of(Member.fullColumn(column)),
                        true, IndexVisibility.VISIBLE, "INLINE_PRIMARY_KEY", constraint);
            } else if (constraint.UNIQUE() != null) {
                emitIndexEvidence(table, List.of(Member.fullColumn(column)),
                        true, IndexVisibility.VISIBLE, "INLINE_UNIQUE", constraint);
            } else if (constraint.REFERENCES() != null) {
                List<String> targetColumns = constraint.identifierList() == null
                        ? List.of("id") : identifiers(constraint.identifierList());
                addForeignKeyEvents(ctx, table, List.of(column), qualifiedName(constraint.qualifiedName()),
                        targetColumns);
            }
        }
        return null;
    }

    @Override
    public Void visitCreateIndexStatement(MySqlRelationSqlParser.CreateIndexStatementContext ctx) {
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        emitIndexEvidence(qualifiedName(ctx.qualifiedName()), indexMembers(ctx.indexPartList()),
                ctx.UNIQUE() != null, visibility(ctx.createIndexTail()), kind, ctx);
        return null;
    }

    protected void emitProjectionItems(
            MySqlRelationSqlParser.SelectListContext ctx,
            ProjectionOwner owner,
            String defaultQualifier
    ) {
        List<MySqlRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            MySqlRelationSqlParser.SelectItemContext item = items.get(index);
            String outputColumn = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            for (ExpressionAnalysis source : writeAnalyses(item, defaultQualifier)) {
                if (!source.sources().isEmpty()) {
                    emitter.addProjection(events, item, StructuredParseEventType.PROJECTION_ITEM,
                            owner.alias(), outputColumn, source.aliases(), source.columns(),
                            source.transform(), source.flowKind());
                }
            }
        }
    }

    protected void emitWriteMapping(
            StructuredParseEventType eventType,
            ParserRuleContext ctx,
            String targetAlias,
            String targetTable,
            String targetColumn,
            ExpressionAnalysis source,
            String mappingKind
    ) {
        if (!source.sources().isEmpty()) {
            emitter.addWrite(events, ctx, eventType, "", "", "", targetAlias,
                    targetTable, targetColumn, mappingKind, source.aliases(), source.columns(),
                    source.transform(), source.flowKind());
        }
    }

    private void addForeignKeyEvents(
            ParserRuleContext ctx,
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns
    ) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void emitIndexEvidence(
            String table,
            List<Member> members,
            boolean unique,
            IndexVisibility visibility,
            String kind,
            ParserRuleContext ctx
    ) {
        for (MySqlIndexSemantics.IndexEvidence evidence
                : MySqlIndexSemantics.evidence(members, unique, visibility, kind)) {
            emitter.addIndexEvent(events, ctx, table, evidence.column(), evidence.role(), evidence.kind());
        }
    }

    private void addDdlColumnEvent(String table, String column, ParserRuleContext ctx) {
        emitter.addDdlColumnEvent(events, ctx, table, column);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> identifiers(MySqlRelationSqlParser.IdentifierListContext ctx) {
        return ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<Member> indexMembers(MySqlRelationSqlParser.IndexPartListContext ctx) {
        List<Member> members = new ArrayList<>();
        for (MySqlRelationSqlParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) {
                String column = clean(part.identifier().getText());
                members.add(part.NUMBER() == null
                        ? Member.fullColumn(column)
                        : Member.prefixColumn(column));
            } else {
                members.add(Member.expression());
            }
        }
        return List.copyOf(members);
    }

    private IndexVisibility visibility(MySqlRelationSqlParser.IndexVisibilityContext ctx) {
        return ctx != null && ctx.INVISIBLE() != null
                ? IndexVisibility.INVISIBLE
                : IndexVisibility.VISIBLE;
    }

    private IndexVisibility visibility(List<MySqlRelationSqlParser.CreateIndexTailContext> options) {
        boolean invisible = options.stream()
                .map(MySqlRelationSqlParser.CreateIndexTailContext::indexVisibility)
                .anyMatch(option -> option != null && option.INVISIBLE() != null);
        return invisible ? IndexVisibility.INVISIBLE : IndexVisibility.VISIBLE;
    }
}
