package com.relationdetector.oracle.fullgrammar.common;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Symbol;

/**
 * CN: 在一次 Oracle DDL parse 内遍历 adapter typed roles，按 ordinal 收集 columns、PK/UK/FK 和 indexes；输出交给 DDL core，不共享 mutable state。
 * EN: Traverses adapter-provided typed roles during one Oracle DDL parse and collects columns, PK/UK/FK groups, and indexes by ordinal. Output goes to the DDL core with no shared mutable state.
 */
final class OracleFullGrammarDdlCollector extends OracleFullGrammarParseTreeSupport {
    private final ArrayDeque<String> ddlTables = new ArrayDeque<>();
    private final Consumer<ParseTree> visitor;

    OracleFullGrammarDdlCollector(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter,
            Consumer<ParseTree> visitor
    ) {
        super(core, adapter);
        this.visitor = visitor;
    }

    void visitCreateTable(ParserRuleContext ctx) {
        String table = qualifiedTable(child(ctx, Role.SCHEMA_NAME), child(ctx, Role.TABLE_NAME));
        ddlTables.push(table);
        visitChildren(ctx);
        ddlTables.pop();
    }

    void visitAlterTable(ParserRuleContext ctx) {
        ddlTables.push(name(child(ctx, Role.TABLEVIEW_NAME)));
        visitChildren(ctx);
        ddlTables.pop();
    }

    void visitColumnDefinition(ParserRuleContext ctx) {
        String table = currentTable();
        String column = name(child(ctx, Role.COLUMN_NAME));
        OracleDdlEventVisitorCore.addColumnEvent(core, ctx, table, column);
        for (ParserRuleContext constraint : children(ctx, Role.INLINE_CONSTRAINT)) {
            if (hasSymbol(constraint, Symbol.PRIMARY)) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", constraint);
            } else if (hasSymbol(constraint, Symbol.UNIQUE)) {
                addIndexEvent(table, column, "TARGET_UNIQUE", "INLINE_UNIQUE", constraint);
            } else if (child(constraint, Role.REFERENCES_CLAUSE) != null) {
                ParserRuleContext ref = child(constraint, Role.REFERENCES_CLAUSE);
                addForeignKeyEvents(table, List.of(column), name(child(ref, Role.TABLEVIEW_NAME)),
                        referenceColumns(ref), constraint);
            }
        }
        visitChildren(ctx);
    }

    void visitOutOfLineConstraint(ParserRuleContext ctx) {
        ParserRuleContext foreignKey = child(ctx, Role.FOREIGN_KEY_CLAUSE);
        if (foreignKey != null) {
            emitForeignKey(foreignKey, ctx);
            return;
        }
        if (hasSymbol(ctx, Symbol.PRIMARY) || hasSymbol(ctx, Symbol.UNIQUE)) {
            String kind = hasSymbol(ctx, Symbol.PRIMARY) ? "PRIMARY_KEY" : "UNIQUE_CONSTRAINT";
            addIndexEvents(currentTable(), columns(children(ctx, Role.COLUMN_NAME)),
                    "TARGET_UNIQUE", kind, ctx);
            return;
        }
        visitChildren(ctx);
    }

    void emitForeignKey(ParserRuleContext ctx) {
        emitForeignKey(ctx, ctx);
    }

    private void emitForeignKey(ParserRuleContext ctx, ParserRuleContext provenance) {
        List<String> sourceColumns = columnsFromParenColumnList(child(ctx, Role.PAREN_COLUMN_LIST));
        ParserRuleContext ref = child(ctx, Role.REFERENCES_CLAUSE);
        addForeignKeyEvents(currentTable(), sourceColumns,
                name(child(ref, Role.TABLEVIEW_NAME)), referenceColumns(ref), provenance);
    }

    void visitCreateIndex(ParserRuleContext ctx) {
        ParserRuleContext tableIndex = child(ctx, Role.TABLE_INDEX_CLAUSE);
        if (tableIndex == null) {
            visitChildren(ctx);
            return;
        }
        String table = name(child(tableIndex, Role.TABLEVIEW_NAME));
        String role = hasSymbol(ctx, Symbol.UNIQUE) ? "TARGET_UNIQUE" : "SOURCE_INDEX";
        String kind = hasSymbol(ctx, Symbol.UNIQUE) ? "CREATE_UNIQUE_INDEX" : "CREATE_INDEX";
        addIndexEvents(table, children(tableIndex, Role.INDEX_EXPRESSION).stream()
                        .map(expr -> child(expr, Role.COLUMN_NAME))
                        .filter(java.util.Objects::nonNull)
                        .map(this::name)
                        .toList(),
                role, kind, ctx);
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

    private void addIndexEvents(
            String table,
            List<String> columns,
            String role,
            String kind,
            ParserRuleContext ctx
    ) {
        OracleDdlEventVisitorCore.addIndexEvents(core, ctx, table,
                columns.stream().map(this::lastPart).toList(), role, kind);
    }

    private String currentTable() {
        return ddlTables.isEmpty() ? "" : ddlTables.peek();
    }

    private List<String> referenceColumns(ParserRuleContext ref) {
        return columnsFromParenColumnList(child(ref, Role.PAREN_COLUMN_LIST));
    }

    private List<String> columnsFromParenColumnList(ParserRuleContext parenColumnList) {
        if (parenColumnList == null) {
            return List.of();
        }
        ParserRuleContext columnList = child(parenColumnList, Role.COLUMN_LIST);
        return columnList == null ? List.of() : columns(children(columnList, Role.COLUMN_NAME));
    }

    private List<String> columns(List<ParserRuleContext> contexts) {
        return contexts.stream().map(this::name).map(this::lastPart).filter(s -> !s.isBlank()).toList();
    }

    private void visitChildren(ParseTree tree) {
        if (tree == null) {
            return;
        }
        for (ParseTree child : typedChildren(tree)) {
            visitor.accept(child);
        }
    }
}
