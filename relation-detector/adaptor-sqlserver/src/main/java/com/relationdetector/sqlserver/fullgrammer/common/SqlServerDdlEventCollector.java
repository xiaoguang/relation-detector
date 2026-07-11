package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.ddl.DdlEventBuilder;

/** Per-parse DDL event collector used by the SQL Server full grammar. */
final class SqlServerDdlEventCollector extends SqlServerParseTreeSupport {
    private final DdlEventBuilder builder;
    private final Consumer<ParseTree> visitor;

    SqlServerDdlEventCollector(Parser parser, String sourceName, Consumer<ParseTree> visitor) {
        super(parser);
        this.builder = new DdlEventBuilder(sourceName);
        this.visitor = visitor;
    }

    List<StructuredSqlEvent> events() {
        return builder.events();
    }

    void visitCreateTable(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, "table_name").orElse("");
        if (table.isBlank() || isLocalTemp(table)) {
            return;
        }
        for (ParserRuleContext column : descendants(ctx, "column_definition")) {
            String columnName = firstDirectText(column, "id_").orElse("");
            if (columnName.isBlank()) {
                continue;
            }
            builder.addColumn(qualifiedTable(table), columnName, line(column));
            if (containsDirectKeyword(column, "PRIMARY") || containsDirectKeyword(column, "UNIQUE")) {
                builder.addIndex(qualifiedTable(table), columnName,
                        "TARGET_UNIQUE", "INLINE_CONSTRAINT", line(column));
            }
            firstDescendant(column, "foreign_key_options").ifPresent(fk -> {
                String targetTable = firstDirectText(fk, "table_name").orElse("");
                List<String> targetColumns = firstDirect(fk, "column_name_list")
                        .map(this::identifierList).orElse(List.of());
                if (!targetTable.isBlank() && !targetColumns.isEmpty()) {
                    builder.addForeignKey(qualifiedTable(table), List.of(columnName),
                            qualifiedTable(targetTable), targetColumns, line(fk));
                    builder.addIndex(qualifiedTable(table), columnName,
                            "SOURCE_INDEX", "IMPLICIT_FK_SOURCE", line(fk));
                    targetColumns.forEach(targetColumn -> builder.addIndex(
                            qualifiedTable(targetTable), targetColumn,
                            "TARGET_UNIQUE", "REFERENCED_KEY", line(fk)));
                }
            });
        }
        for (ParserRuleContext constraint : descendants(ctx, "table_constraint")) {
            if (containsDirectKeyword(constraint, "FOREIGN")) {
                addForeignKeyConstraint(table, constraint, false);
            } else if (containsDirectKeyword(constraint, "PRIMARY")
                    || containsDirectKeyword(constraint, "UNIQUE")) {
                firstDirect(constraint, "column_name_list_with_order")
                        .map(this::identifierList).orElse(List.of())
                        .forEach(column -> builder.addIndex(qualifiedTable(table), column,
                                "TARGET_UNIQUE", "TABLE_CONSTRAINT", line(constraint)));
            }
        }
    }

    void visitAlterTable(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, "table_name").orElse("");
        if (table.isBlank() || isLocalTemp(table)) {
            visitChildren(ctx);
            return;
        }
        for (ParserRuleContext column : descendants(ctx, "column_definition")) {
            String columnName = firstDirectText(column, "id_").orElse("");
            if (!columnName.isBlank()) {
                builder.addColumn(qualifiedTable(table), columnName, line(column));
            }
        }
        for (ParserRuleContext constraint : descendants(ctx, "table_constraint")) {
            if (containsDirectKeyword(constraint, "FOREIGN")) {
                addForeignKeyConstraint(table, constraint, true);
            }
        }
        visitChildren(ctx);
    }

    void visitCreateIndex(ParserRuleContext ctx) {
        String table = firstDirectText(ctx, "table_name").orElse("");
        if (table.isBlank()) {
            return;
        }
        String role = containsDirectKeyword(ctx, "UNIQUE") ? "TARGET_UNIQUE" : "SOURCE_INDEX";
        firstDirect(ctx, "column_name_list_with_order").map(this::identifierList).orElse(List.of())
                .forEach(column -> builder.addIndex(qualifiedTable(table), column,
                        role, "CREATE_INDEX", line(ctx)));
    }

    private void addForeignKeyConstraint(
            String table,
            ParserRuleContext constraint,
            boolean addConstraintColumnInventory
    ) {
        List<ParserRuleContext> lists = directChildren(constraint, "column_name_list");
        Optional<ParserRuleContext> fkOptions = firstDirect(constraint, "foreign_key_options");
        String targetTable = fkOptions.flatMap(fk -> firstDirectText(fk, "table_name")).orElse("");
        List<String> sourceColumns = lists.isEmpty() ? List.of() : identifierList(lists.get(0));
        List<String> targetColumns = fkOptions.flatMap(fk -> firstDirect(fk, "column_name_list"))
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
        sourceColumns.forEach(sourceColumn -> builder.addIndex(qualifiedTable(table), sourceColumn,
                "SOURCE_INDEX", "FK_SOURCE", line(constraint)));
        targetColumns.forEach(targetColumn -> builder.addIndex(qualifiedTable(targetTable), targetColumn,
                "TARGET_UNIQUE", "REFERENCED_KEY", line(constraint)));
    }

    private void visitChildren(ParseTree tree) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            visitor.accept(tree.getChild(index));
        }
    }
}
