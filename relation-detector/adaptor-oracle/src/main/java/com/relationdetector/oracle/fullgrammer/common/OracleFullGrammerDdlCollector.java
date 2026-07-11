package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Per-parse Oracle DDL column, key, foreign-key and index collector. */
final class OracleFullGrammerDdlCollector extends OracleFullGrammerParseTreeSupport {
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final Consumer<ParseTree> visitor;

    OracleFullGrammerDdlCollector(
            OracleSqlEventVisitorCore core,
            OracleFullGrammerParseTreeAdapter adapter,
            Consumer<ParseTree> visitor
    ) {
        super(core, adapter);
        this.visitor = visitor;
    }

    void visitCreateTable(ParserRuleContext ctx) {
        String table = qualifiedTable(child(ctx, "schema_name"), child(ctx, "table_name"));
        ddlTables.push(table);
        visitChildren(ctx);
        ddlTables.pop();
    }

    void visitAlterTable(ParserRuleContext ctx) {
        ddlTables.push(name(child(ctx, "tableview_name")));
        visitChildren(ctx);
        ddlTables.pop();
    }

    void visitColumnDefinition(ParserRuleContext ctx) {
        String table = currentTable();
        String column = name(child(ctx, "column_name"));
        OracleDdlEventVisitorCore.addColumnEvent(core, ctx, table, column);
        for (ParserRuleContext constraint : children(ctx, "inline_constraint")) {
            if (node(constraint, "PRIMARY") != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (node(constraint, "UNIQUE") != null) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (child(constraint, "references_clause") != null) {
                ParserRuleContext ref = child(constraint, "references_clause");
                addForeignKeyEvents(table, List.of(column), name(child(ref, "tableview_name")),
                        referenceColumns(ref), constraint);
            }
        }
        visitChildren(ctx);
    }

    void visitOutOfLineConstraint(ParserRuleContext ctx) {
        ParserRuleContext foreignKey = child(ctx, "foreign_key_clause");
        if (foreignKey != null) {
            emitForeignKey(foreignKey);
            return;
        }
        if (node(ctx, "PRIMARY") != null || node(ctx, "UNIQUE") != null) {
            String kind = node(ctx, "PRIMARY") != null ? "PRIMARY_KEY" : "UNIQUE_CONSTRAINT";
            for (String column : columns(children(ctx, "column_name"))) {
                addIndexEvent(currentTable(), column, "TARGET_UNIQUE", kind, ctx);
            }
            return;
        }
        visitChildren(ctx);
    }

    void emitForeignKey(ParserRuleContext ctx) {
        List<String> sourceColumns = columnsFromParenColumnList(child(ctx, "paren_column_list"));
        ParserRuleContext ref = child(ctx, "references_clause");
        addForeignKeyEvents(currentTable(), sourceColumns,
                name(child(ref, "tableview_name")), referenceColumns(ref), ctx);
    }

    void visitCreateIndex(ParserRuleContext ctx) {
        ParserRuleContext tableIndex = child(ctx, "table_index_clause");
        if (tableIndex == null) {
            visitChildren(ctx);
            return;
        }
        String table = name(child(tableIndex, "tableview_name"));
        String role = node(ctx, "UNIQUE") == null ? "SOURCE_INDEX" : "TARGET_UNIQUE";
        String kind = node(ctx, "UNIQUE") == null ? "CREATE_INDEX" : "CREATE_UNIQUE_INDEX";
        for (ParserRuleContext expr : children(tableIndex, "index_expr")) {
            ParserRuleContext column = child(expr, "column_name");
            if (column != null) {
                addIndexEvent(table, name(column), role, kind, ctx);
            }
        }
    }

    private void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            ParserRuleContext ctx
    ) {
        OracleDdlEventVisitorCore.addForeignKeyEvents(
                core, ctx, sourceTable, sourceColumns, targetTable, targetColumns);
    }

    private void addIndexEvent(
            String table,
            String column,
            String role,
            String kind,
            ParserRuleContext ctx
    ) {
        OracleDdlEventVisitorCore.addIndexEvent(core, ctx, table, lastPart(column), role, kind);
    }

    private String currentTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> referenceColumns(ParserRuleContext ref) {
        return columnsFromParenColumnList(child(ref, "paren_column_list"));
    }

    private List<String> columnsFromParenColumnList(ParserRuleContext parenColumnList) {
        if (parenColumnList == null) {
            return List.of();
        }
        ParserRuleContext columnList = child(parenColumnList, "column_list");
        return columnList == null ? List.of() : columns(children(columnList, "column_name"));
    }

    private List<String> columns(List<ParserRuleContext> contexts) {
        return contexts.stream().map(this::name).map(this::lastPart).filter(s -> !s.isBlank()).toList();
    }

    private void visitChildren(ParseTree tree) {
        if (tree == null) {
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            visitor.accept(tree.getChild(index));
        }
    }
}
