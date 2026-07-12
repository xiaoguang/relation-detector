package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.ddl.DdlEventBuilder;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.DdlConstraintSemantic;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role;

/** Per-parse DDL event collector used by the SQL Server full grammar. */
final class SqlServerDdlEventCollector extends SqlServerParseTreeSupport {
    private final DdlEventBuilder builder;
    private final Consumer<ParseTree> visitor;

    SqlServerDdlEventCollector(
            FullGrammarParseTreeAdapter adapter,
            String sourceName,
            Consumer<ParseTree> visitor
    ) {
        super(adapter);
        this.builder = new DdlEventBuilder(sourceName);
        this.visitor = visitor;
    }

    List<StructuredSqlEvent> events() {
        return builder.events();
    }

    void visitCreateTable(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, Role.TABLE_NAME).orElse("");
        if (table.isBlank() || isLocalTemp(table)) {
            return;
        }
        for (ParserRuleContext column : descendants(ctx, Role.COLUMN_DEFINITION)) {
            String columnName = firstDirectText(column, Role.IDENTIFIER).orElse("");
            if (columnName.isBlank()) {
                continue;
            }
            builder.addColumn(qualifiedTable(table), columnName, line(column));
            DdlConstraintSemantic columnConstraint = ddlConstraintSemantic(column);
            if (columnConstraint == DdlConstraintSemantic.PRIMARY_KEY
                    || columnConstraint == DdlConstraintSemantic.UNIQUE) {
                builder.addIndex(qualifiedTable(table), columnName,
                        "TARGET_UNIQUE", "INLINE_CONSTRAINT", line(column));
            }
            firstDescendant(column, Role.FOREIGN_KEY_OPTIONS).ifPresent(fk -> {
                String targetTable = firstDirectText(fk, Role.TABLE_NAME).orElse("");
                List<String> targetColumns = firstDirect(fk, Role.COLUMN_LIST)
                        .map(this::identifierList).orElse(List.of());
                if (!targetTable.isBlank() && !targetColumns.isEmpty()) {
                    builder.addForeignKey(qualifiedTable(table), List.of(columnName),
                            qualifiedTable(targetTable), targetColumns, line(fk));
                }
            });
        }
        for (ParserRuleContext constraint : descendants(ctx, Role.TABLE_CONSTRAINT)) {
            DdlConstraintSemantic semantic = ddlConstraintSemantic(constraint);
            if (semantic == DdlConstraintSemantic.FOREIGN_KEY) {
                addForeignKeyConstraint(table, constraint, false);
            } else if (semantic == DdlConstraintSemantic.PRIMARY_KEY
                    || semantic == DdlConstraintSemantic.UNIQUE) {
                builder.addIndex(qualifiedTable(table), firstDirect(constraint, Role.ORDERED_COLUMN_LIST)
                                .map(this::identifierList).orElse(List.of()),
                        "TARGET_UNIQUE", "TABLE_CONSTRAINT", line(constraint));
            }
        }
    }

    void visitAlterTable(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, Role.TABLE_NAME).orElse("");
        if (table.isBlank() || isLocalTemp(table)) {
            visitChildren(ctx);
            return;
        }
        for (ParserRuleContext column : descendants(ctx, Role.COLUMN_DEFINITION)) {
            String columnName = firstDirectText(column, Role.IDENTIFIER).orElse("");
            if (!columnName.isBlank()) {
                builder.addColumn(qualifiedTable(table), columnName, line(column));
            }
        }
        for (ParserRuleContext constraint : descendants(ctx, Role.TABLE_CONSTRAINT)) {
            if (ddlConstraintSemantic(constraint) == DdlConstraintSemantic.FOREIGN_KEY) {
                addForeignKeyConstraint(table, constraint, true);
            }
        }
        visitChildren(ctx);
    }

    void visitCreateIndex(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, Role.TABLE_NAME).orElse("");
        if (table.isBlank()) {
            return;
        }
        String role = ddlConstraintSemantic(ctx) == DdlConstraintSemantic.UNIQUE
                ? "TARGET_UNIQUE" : "SOURCE_INDEX";
        builder.addIndex(qualifiedTable(table),
                firstDirect(ctx, Role.ORDERED_COLUMN_LIST).map(this::identifierList).orElse(List.of()),
                role, "CREATE_INDEX", line(ctx));
    }

    private void addForeignKeyConstraint(
            String table,
            ParserRuleContext constraint,
            boolean addConstraintColumnInventory
    ) {
        List<ParserRuleContext> lists = directChildren(constraint, Role.COLUMN_LIST);
        Optional<ParserRuleContext> fkOptions = firstDirect(constraint, Role.FOREIGN_KEY_OPTIONS);
        String targetTable = fkOptions.flatMap(fk -> firstDirectText(fk, Role.TABLE_NAME)).orElse("");
        List<String> sourceColumns = lists.isEmpty() ? List.of() : identifierList(lists.get(0));
        List<String> targetColumns = fkOptions.flatMap(fk -> firstDirect(fk, Role.COLUMN_LIST))
                .map(this::identifierList).orElse(List.of());
        if (targetTable.isBlank() || sourceColumns.isEmpty() || targetColumns.isEmpty()) {
            return;
        }
        if (addConstraintColumnInventory) {
            sourceColumns.forEach(sourceColumn ->
                    builder.addColumn(qualifiedTable(table), sourceColumn, line(constraint)));
            targetColumns.forEach(targetColumn ->
                    builder.addColumn(qualifiedTable(targetTable), targetColumn, line(constraint)));
        }
        builder.addForeignKey(qualifiedTable(table), sourceColumns,
                qualifiedTable(targetTable), targetColumns, line(constraint));
    }

    private void visitChildren(ParseTree tree) {
        for (ParseTree child : typedChildren(tree)) {
            visitor.accept(child);
        }
    }
}
