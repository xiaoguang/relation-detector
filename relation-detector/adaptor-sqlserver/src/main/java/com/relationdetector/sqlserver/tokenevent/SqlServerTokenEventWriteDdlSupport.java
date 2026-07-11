package com.relationdetector.sqlserver.tokenevent;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.LineageFlowKind;
import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/** Projection, write mapping and DDL event traversal for SQL Server token-event SQL. */
abstract class SqlServerTokenEventWriteDdlSupport extends SqlServerTokenEventExpressionSupport {
    SqlServerTokenEventWriteDdlSupport(SqlStatementRecord statement, boolean ddlOnly) {
        super(statement, ddlOnly);
    }

    @Override
    public Void visitInsert_statement(SqlServerRelationSqlParser.Insert_statementContext ctx) {
        String targetTable = qualifiedName(ctx.ddl_object().full_table_name());
        List<String> targetColumns = identifiers(ctx.insert_column_name_list().column_name_list());
        visit(ctx.insert_statement_value());
        SqlServerRelationSqlParser.Query_specificationContext query =
                firstQuerySpecification(ctx.insert_statement_value().select_statement());
        SqlServerRelationSqlParser.Select_listContext selectList = query == null ? null : query.select_list();
        if (selectList == null) return null;
        List<SqlServerRelationSqlParser.Select_list_elemContext> items = selectList.select_list_elem();
        for (int index = 0; index < Math.min(targetColumns.size(), items.size()); index++) {
            SqlServerRelationSqlParser.ExpressionContext expression = items.get(index).expression();
            if (expression == null) continue;
            ExpressionAnalysis source = analyze(expression);
            if (!source.hasSources()) continue;
            if (!source.sources().isEmpty()) {
                emitter.addWrite(events, items.get(index), StructuredParseEventType.INSERT_SELECT_MAPPING,
                        "", "", "", "", targetTable, targetColumns.get(index), "INSERT_SELECT",
                        source.aliases(), source.columns(), source.transform(), source.flowKind());
            }
            emitControlMapping(StructuredParseEventType.INSERT_SELECT_MAPPING, items.get(index),
                    targetTable, targetColumns.get(index), source, "INSERT_SELECT");
        }
        return null;
    }

    @Override
    public Void visitUpdate_statement(SqlServerRelationSqlParser.Update_statementContext ctx) {
        String targetAlias = qualifiedName(ctx.ddl_object().full_table_name());
        String targetTable = targetAlias;
        String qualifiedTargetTable = targetAlias;
        if (ctx.table_sources() != null) {
            visit(ctx.table_sources());
            qualifiedTargetTable = tableForAlias(ctx.table_sources(), targetAlias);
            targetTable = qualifiedTargetTable;
        }
        emitter.addWrite(events, ctx, StructuredParseEventType.WRITE_TARGET,
                baseName(qualifiedTargetTable), qualifiedTargetTable, targetAlias,
                "", "", "", "", List.of(), List.of(),
                LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        for (SqlServerRelationSqlParser.Update_elemContext elem : ctx.update_elem()) {
            emitUpdateMapping(elem, targetTable, false);
        }
        if (ctx.search_condition_clause() != null) visit(ctx.search_condition_clause());
        return null;
    }

    @Override
    public Void visitMerge_statement(SqlServerRelationSqlParser.Merge_statementContext ctx) {
        String targetTable = qualifiedName(ctx.ddl_object().full_table_name());
        String targetAlias = ctx.as_table_alias() == null ? ""
                : clean(ctx.as_table_alias().table_alias().getText());
        emitter.addWrite(events, ctx, StructuredParseEventType.WRITE_TARGET,
                baseName(targetTable), targetTable, targetAlias, "", "", "", "",
                List.of(), List.of(), LineageTransformType.UNKNOWN_EXPRESSION, LineageFlowKind.VALUE);
        visit(ctx.table_sources());
        joinKinds.push("MERGE_ON");
        visit(ctx.search_condition());
        joinKinds.pop();
        for (SqlServerRelationSqlParser.Merge_when_clauseContext clause : ctx.merge_when_clause()) {
            for (SqlServerRelationSqlParser.Update_elem_mergeContext elem : clause.update_elem_merge()) {
                emitMergeUpdateMapping(elem, targetTable);
            }
            if (clause.merge_not_matched() != null) emitMergeInsertMappings(clause.merge_not_matched(), targetTable);
        }
        return null;
    }

    @Override
    public Void visitCreate_or_alter_trigger(SqlServerRelationSqlParser.Create_or_alter_triggerContext ctx) {
        List<SqlServerRelationSqlParser.Full_table_nameContext> names = ctx.full_table_name();
        String targetTable = names.size() < 2 ? "" : qualifiedName(names.get(1));
        emitTriggerPseudoRowset(ctx, "inserted", targetTable);
        emitTriggerPseudoRowset(ctx, "deleted", targetTable);
        return visitChildren(ctx);
    }

    @Override
    public Void visitCreate_table(SqlServerRelationSqlParser.Create_tableContext ctx) {
        ddlTables.push(qualifiedName(ctx.table_name().full_table_name()));
        ctx.table_element().forEach(this::visit);
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlter_table(SqlServerRelationSqlParser.Alter_tableContext ctx) {
        SqlServerRelationSqlParser.Table_constraintContext constraint = ctx.table_constraint();
        if (constraint.FOREIGN() == null || constraint.foreign_key_options() == null) return null;
        String sourceTable = qualifiedName(ctx.table_name().full_table_name());
        String targetTable = qualifiedName(constraint.foreign_key_options().table_name().full_table_name());
        List<String> sourceColumns = identifiers(constraint.column_name_list());
        List<String> targetColumns = identifiers(constraint.foreign_key_options().column_name_list());
        sourceColumns.forEach(column -> emitter.addDdlColumnEvent(events, constraint, sourceTable, column));
        targetColumns.forEach(column -> emitter.addDdlColumnEvent(events, constraint, targetTable, column));
        addForeignKeyEvents(constraint, sourceTable, sourceColumns, targetTable, targetColumns);
        return null;
    }

    @Override
    public Void visitColumn_definition(SqlServerRelationSqlParser.Column_definitionContext ctx) {
        String column = clean(ctx.id_().getText());
        String table = currentDdlTable();
        emitter.addDdlColumnEvent(events, ctx, table, column);
        boolean primary = false;
        boolean unique = false;
        for (SqlServerRelationSqlParser.Column_attributeContext attr : ctx.column_attribute()) {
            if (attr.PRIMARY() != null) primary = true;
            if (attr.UNIQUE() != null) unique = true;
            if (attr.foreign_key_options() != null) {
                addForeignKeyEvents(attr, table, List.of(column),
                        qualifiedName(attr.foreign_key_options().table_name().full_table_name()),
                        identifiers(attr.foreign_key_options().column_name_list()));
            }
        }
        if (primary || unique) {
            addIndexEvent(table, column, "TARGET_UNIQUE",
                    primary ? "INLINE_PRIMARY_KEY" : "INLINE_UNIQUE", ctx);
        }
        return null;
    }

    @Override
    public Void visitTable_constraint(SqlServerRelationSqlParser.Table_constraintContext ctx) {
        if (ctx.FOREIGN() != null && ctx.foreign_key_options() != null) {
            addForeignKeyEvents(ctx, currentDdlTable(), identifiers(ctx.column_name_list()),
                    qualifiedName(ctx.foreign_key_options().table_name().full_table_name()),
                    identifiers(ctx.foreign_key_options().column_name_list()));
            return null;
        }
        if (ctx.PRIMARY() != null || ctx.UNIQUE() != null) {
            String kind = ctx.PRIMARY() != null ? "PRIMARY_KEY" : "UNIQUE_CONSTRAINT";
            for (String column : identifiers(ctx.column_name_list_with_order())) {
                addIndexEvent(currentDdlTable(), column, "TARGET_UNIQUE", kind, ctx);
            }
        }
        return null;
    }

    @Override
    public Void visitCreate_index(SqlServerRelationSqlParser.Create_indexContext ctx) {
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        String table = qualifiedName(ctx.table_name().full_table_name());
        for (String column : identifiers(ctx.column_name_list_with_order())) {
            addIndexEvent(table, column, role, kind, ctx);
        }
        return null;
    }

    protected void emitProjectionItems(SqlServerRelationSqlParser.Select_listContext selectList,
            String owner, String defaultQualifier) {
        for (SqlServerRelationSqlParser.Select_list_elemContext item : selectList.select_list_elem()) {
            SqlServerRelationSqlParser.ExpressionContext expression = item.expression();
            if (expression == null) continue;
            ExpressionAnalysis source = analyze(expression, defaultQualifier);
            if (!source.hasSources()) continue;
            String output = outputColumn(item);
            if (output.isBlank()) continue;
            if (!source.sources().isEmpty()) emitProjectionItem(item, owner, output,
                    source.aliases(), source.columns(), source.transform(), source.flowKind());
            if (!source.controlSources().isEmpty()) emitProjectionItem(item, owner, output,
                    source.controlAliases(), source.controlColumns(),
                    source.controlTransform(), LineageFlowKind.CONTROL);
        }
    }

    private void emitProjectionItem(ParserRuleContext ctx, String owner, String outputColumn,
            List<String> sourceAliases, List<String> sourceColumns,
            LineageTransformType transformType, LineageFlowKind flowKind) {
        emitter.addProjection(events, ctx, StructuredParseEventType.PROJECTION_ITEM,
                owner, outputColumn, sourceAliases, sourceColumns, transformType, flowKind);
    }

    private void emitUpdateMapping(
            SqlServerRelationSqlParser.Update_elemContext elem,
            String targetTable,
            boolean merge) {
        SqlServerRelationSqlParser.ExpressionContext expression = elem.expression();
        if (expression == null) return;
        String targetColumn = elem.full_column_name() == null
                ? clean(elem.id_().getText()) : lastPart(elem.full_column_name().getText());
        ExpressionAnalysis source = analyze(expression);
        if (!source.hasSources()) return;
        StructuredParseEventType type = merge ? StructuredParseEventType.MERGE_WRITE_MAPPING
                : StructuredParseEventType.UPDATE_ASSIGNMENT;
        String kind = merge ? "MERGE_UPDATE" : "UPDATE_SET";
        if (!source.sources().isEmpty()) {
            emitter.addWrite(events, elem, type, "", "", "", "", targetTable, targetColumn,
                    kind, source.aliases(), source.columns(), source.transform(), source.flowKind());
        }
        emitControlMapping(type, elem, targetTable, targetColumn, source, kind);
    }

    private void emitMergeUpdateMapping(
            SqlServerRelationSqlParser.Update_elem_mergeContext elem,
            String targetTable) {
        SqlServerRelationSqlParser.ExpressionContext expression = elem.expression();
        if (expression == null) return;
        String targetColumn = elem.full_column_name() == null
                ? clean(elem.id_().getText()) : lastPart(elem.full_column_name().getText());
        ExpressionAnalysis source = analyze(expression);
        if (!source.hasSources()) return;
        if (!source.sources().isEmpty()) {
            emitter.addWrite(events, elem, StructuredParseEventType.MERGE_WRITE_MAPPING,
                    "", "", "", "", targetTable, targetColumn, "MERGE_UPDATE",
                    source.aliases(), source.columns(), source.transform(), source.flowKind());
        }
        emitControlMapping(StructuredParseEventType.MERGE_WRITE_MAPPING,
                elem, targetTable, targetColumn, source, "MERGE_UPDATE");
    }

    private void emitMergeInsertMappings(
            SqlServerRelationSqlParser.Merge_not_matchedContext ctx,
            String targetTable) {
        List<String> columns = identifiers(ctx.column_name_list());
        List<SqlServerRelationSqlParser.ExpressionContext> values =
                ctx.values_clause().expression_list_().expression();
        for (int index = 0; index < Math.min(columns.size(), values.size()); index++) {
            ExpressionAnalysis source = analyze(values.get(index));
            if (!source.hasSources()) continue;
            if (!source.sources().isEmpty()) {
                emitter.addWrite(events, values.get(index), StructuredParseEventType.MERGE_WRITE_MAPPING,
                        "", "", "", "", targetTable, columns.get(index), "MERGE_INSERT",
                        source.aliases(), source.columns(), source.transform(), source.flowKind());
            }
            emitControlMapping(StructuredParseEventType.MERGE_WRITE_MAPPING,
                    values.get(index), targetTable, columns.get(index), source, "MERGE_INSERT");
        }
    }

    private void emitControlMapping(StructuredParseEventType type, ParserRuleContext ctx,
            String targetTable, String targetColumn, ExpressionAnalysis source, String mappingKind) {
        if (!source.controlSources().isEmpty()) {
            emitter.addWrite(events, ctx, type, "", "", "", "", targetTable,
                    targetColumn, mappingKind, source.controlAliases(), source.controlColumns(),
                    source.controlTransform(), LineageFlowKind.CONTROL);
        }
    }

    private void emitTriggerPseudoRowset(ParserRuleContext ctx, String name, String targetTable) {
        emitter.addRowset(events, ctx, StructuredParseEventType.TRIGGER_PSEUDO_ROWSET,
                "", "", "", "", name, clean(targetTable), "");
    }

    private void addForeignKeyEvents(ParserRuleContext ctx, String sourceTable,
            List<String> sourceColumns, String targetTable, List<String> targetColumns) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
        for (int index = 0; index < Math.min(sourceColumns.size(), targetColumns.size()); index++) {
            addIndexEvent(sourceTable, sourceColumns.get(index), "SOURCE_INDEX", "FK_SOURCE", ctx);
            addIndexEvent(targetTable, targetColumns.get(index), "TARGET_UNIQUE", "REFERENCED_KEY", ctx);
        }
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        emitter.addIndexEvent(events, ctx, table, column, role, kind);
    }

    private String currentDdlTable() { return ddlTables.isEmpty() ? "" : ddlTables.peek(); }
}
