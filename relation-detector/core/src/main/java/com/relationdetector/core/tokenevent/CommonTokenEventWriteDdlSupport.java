package com.relationdetector.core.tokenevent;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;

/**
 *
 * Projection/write emission and DDL inventory traversal for the common grammar.
 */
abstract class CommonTokenEventWriteDdlSupport extends CommonTokenEventExpressionSupport {
    CommonTokenEventWriteDdlSupport(SqlStatementRecord statement) {
        super(statement);
    }

    @Override
    public Void visitCreateTableStatement(CommonRelationSqlParser.CreateTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        for (CommonRelationSqlParser.TableElementContext element : ctx.tableElement()) {
            visit(element);
        }
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitAlterTableStatement(CommonRelationSqlParser.AlterTableStatementContext ctx) {
        ddlTables.push(qualifiedName(ctx.qualifiedName()));
        visit(ctx.tableForeignKey());
        ddlTables.pop();
        return null;
    }

    @Override
    public Void visitTableForeignKey(CommonRelationSqlParser.TableForeignKeyContext ctx) {
        addForeignKeyEvents(currentDdlTable(), identifiers(ctx.identifierList(0)),
                qualifiedName(ctx.qualifiedName()), identifiers(ctx.identifierList(1)), ctx);
        return null;
    }

    @Override
    public Void visitPrimaryKeyConstraint(CommonRelationSqlParser.PrimaryKeyConstraintContext ctx) {
        addIndexEvents(currentDdlTable(), identifiers(ctx.identifierList()),
                "TARGET_UNIQUE", "PRIMARY_KEY", ctx);
        return null;
    }

    @Override
    public Void visitUniqueConstraint(CommonRelationSqlParser.UniqueConstraintContext ctx) {
        addIndexEvents(currentDdlTable(), identifiers(ctx.identifierList()),
                "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", ctx);
        return null;
    }

    @Override
    public Void visitTableIndexConstraint(CommonRelationSqlParser.TableIndexConstraintContext ctx) {
        addIndexEvents(currentDdlTable(), safeIndexColumns(ctx.indexPartList()),
                "SOURCE_INDEX", "CREATE_TABLE_INDEX", ctx);
        return null;
    }

    @Override
    public Void visitColumnDefinition(CommonRelationSqlParser.ColumnDefinitionContext ctx) {
        String table = currentDdlTable();
        String column = clean(ctx.identifier().getText());
        addDdlColumnEvent(table, column, ctx);
        for (CommonRelationSqlParser.ColumnDefinitionPartContext part : ctx.columnDefinitionPart()) {
            CommonRelationSqlParser.InlineColumnConstraintContext constraint = part.inlineColumnConstraint();
            if (constraint == null) {
                continue;
            }
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
    public Void visitCreateIndexStatement(CommonRelationSqlParser.CreateIndexStatementContext ctx) {
        String table = qualifiedName(ctx.qualifiedName());
        String role = ctx.UNIQUE() == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = ctx.UNIQUE() == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        addIndexEvents(table, safeIndexColumns(ctx.indexPartList()), role, kind, ctx);
        return null;
    }

    protected void emitProjectionItems(CommonRelationSqlParser.SelectListContext ctx, ProjectionOwner owner) {
        List<CommonRelationSqlParser.SelectItemContext> items = ctx.selectItem();
        for (int index = 0; index < items.size(); index++) {
            CommonRelationSqlParser.SelectItemContext item = items.get(index);
            if (item.expression() == null) {
                continue;
            }
            String outputColumn = index < owner.columns().size() ? owner.columns().get(index) : outputColumn(item);
            if (outputColumn.isBlank()) {
                continue;
            }
            for (ExpressionAnalysis source : writeAnalyses(item.expression())) {
                if (!source.sources().isEmpty()) {
                    emitter.addProjection(events, item, StructuredParseEventType.PROJECTION_ITEM,
                            owner.alias(), outputColumn, source.aliases(), source.columns(),
                            source.transform(), source.flowKind());
                }
            }
        }
    }

    protected void addWriteMapping(
            StructuredParseEventType type,
            ParserRuleContext context,
            String targetTable,
            String targetColumn,
            String targetAlias,
            ExpressionAnalysis source,
            String mappingKind
    ) {
        if (!source.sources().isEmpty()) {
            emitter.addWrite(events, context, type, "", "", "", targetAlias,
                    targetTable, targetColumn, mappingKind, source.aliases(), source.columns(),
                    source.transform(), source.flowKind());
        }
    }

    private void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            ParserRuleContext ctx
    ) {
        emitter.addForeignKeyEvents(events, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(String table, String column, String role, String kind, ParserRuleContext ctx) {
        emitter.addIndexEvent(events, ctx, table, column, role, kind);
    }

    private void addIndexEvents(String table, List<String> columns, String role, String kind,
            ParserRuleContext ctx) {
        emitter.addIndexEvents(events, ctx, table, columns, role, kind);
    }

    private void addDdlColumnEvent(String table, String column, ParserRuleContext ctx) {
        emitter.addDdlColumnEvent(events, ctx, table, column);
    }

    private String currentDdlTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> identifiers(CommonRelationSqlParser.IdentifierListContext ctx) {
        return ctx == null ? List.of()
                : ctx.identifier().stream().map(identifier -> clean(identifier.getText())).toList();
    }

    private List<String> safeIndexColumns(CommonRelationSqlParser.IndexPartListContext ctx) {
        if (ctx == null) {
            return List.of();
        }
        List<String> columns = new ArrayList<>();
        for (CommonRelationSqlParser.IndexPartContext part : ctx.indexPart()) {
            if (part.identifier() != null) {
                columns.add(clean(part.identifier().getText()));
            }
        }
        return columns;
    }
}
