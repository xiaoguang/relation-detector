package com.relationdetector.mysql.fullgrammar.v8_0;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.mysql.fullgrammar.common.MySqlDdlEventSink;

/**
 * MySQL full-grammar DDL event collector。
 *
 * <p>CN: 从 MySQL full grammar typed parse-tree contexts 中抽取 FK/index 事实，并
 * 输出统一 DDL structured events。它不调用 token-event DDL cursor。
 *
 * <p>EN: MySQL full-grammar DDL event collector. It extracts FK/index facts
 * from typed parse-tree contexts and emits unified DDL structured events. It
 * does not call the token-event DDL cursor.
 */
final class MySqlFullGrammarDdlEventCollector {
    List<StructuredSqlEvent> collect(String sourceName, ParserRuleContext root) {
        if (root == null) {
            return List.of();
        }
        MysqlDdlVisitor visitor = new MysqlDdlVisitor(sourceName);
        visitor.visit(root);
        return visitor.events();
    }

    private static final class MysqlDdlVisitor extends MySqlFullGrammarParserBaseVisitor<Void> {
        private final MySqlDdlEventSink out;
        private String currentTable = "";
        private String currentColumn = "";

        MysqlDdlVisitor(String sourceName) {
            this.out = new MySqlDdlEventSink(sourceName);
        }

        List<StructuredSqlEvent> events() {
            return out.events();
        }

        @Override
        public Void visitCreateTable(MySqlFullGrammarParser.CreateTableContext ctx) {
            String previous = currentTable;
            currentTable = out.clean(ctx.tableName() == null ? "" : ctx.tableName().getText());
            if (ctx.tableElementList() != null) {
                visit(ctx.tableElementList());
            }
            currentTable = previous;
            return null;
        }

        @Override
        public Void visitAlterTable(MySqlFullGrammarParser.AlterTableContext ctx) {
            String previous = currentTable;
            currentTable = table(ctx.tableRef());
            if (ctx.alterTableActions() != null) {
                visit(ctx.alterTableActions());
            }
            currentTable = previous;
            return null;
        }

        @Override
        public Void visitAlterListItem(MySqlFullGrammarParser.AlterListItemContext ctx) {
            if (currentTable.isBlank()) {
                return null;
            }
            long line = ctx.getStart().getLine();
            if (ctx.tableConstraintDef() != null) {
                visit(ctx.tableConstraintDef());
                return null;
            }
            if (ctx.identifier() != null && ctx.checkOrReferences() != null
                    && ctx.checkOrReferences().references() != null) {
                MySqlFullGrammarParser.ReferencesContext references = ctx.checkOrReferences().references();
                out.addForeignKeyEvents(currentTable, List.of(out.clean(ctx.identifier().getText())),
                        table(references.tableRef()),
                        identifierList(references.identifierListWithParentheses()), line);
                return null;
            }
            return super.visitAlterListItem(ctx);
        }

        @Override
        public Void visitColumnDefinition(MySqlFullGrammarParser.ColumnDefinitionContext ctx) {
            if (currentTable.isBlank()) {
                return null;
            }
            String column = out.clean(ctx.columnName() == null ? "" : ctx.columnName().getText());
            if (!column.isBlank()) {
                out.addColumn(currentTable, column, ctx.getStart().getLine());
            }
            if (hasInlinePrimaryKey(ctx.fieldDefinition())) {
                out.addIndex(currentTable, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", ctx.getStart().getLine());
            }
            if (hasInlineUnique(ctx.fieldDefinition())) {
                out.addIndex(currentTable, column, "TARGET_UNIQUE", "INLINE_UNIQUE", ctx.getStart().getLine());
            }
            String previous = currentColumn;
            currentColumn = column;
            Void result = super.visitColumnDefinition(ctx);
            currentColumn = previous;
            return result;
        }

        @Override
        public Void visitReferences(MySqlFullGrammarParser.ReferencesContext ctx) {
            if (!currentTable.isBlank() && !currentColumn.isBlank()) {
                out.addForeignKeyEvents(currentTable, List.of(currentColumn), table(ctx.tableRef()),
                        identifierList(ctx.identifierListWithParentheses()), ctx.getStart().getLine());
            }
            return null;
        }

        @Override
        public Void visitTableConstraintDef(MySqlFullGrammarParser.TableConstraintDefContext ctx) {
            if (currentTable.isBlank()) {
                return null;
            }
            long line = ctx.getStart().getLine();
            if (ctx.references() != null && ctx.keyList() != null) {
                out.addForeignKeyEvents(currentTable, keyList(ctx.keyList()), table(ctx.references().tableRef()),
                        identifierList(ctx.references().identifierListWithParentheses()), line);
                return null;
            }
            if (ctx.PRIMARY_SYMBOL() != null) {
                for (String column : keyListWithExpression(ctx.keyListWithExpression())) {
                    out.addIndex(currentTable, column, "TARGET_UNIQUE", "PRIMARY_KEY", line);
                }
                return null;
            }
            if (ctx.UNIQUE_SYMBOL() != null) {
                for (String column : keyListWithExpression(ctx.keyListWithExpression())) {
                    out.addIndex(currentTable, column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", line);
                }
                return null;
            }
            if (ctx.KEY_SYMBOL() != null || ctx.INDEX_SYMBOL() != null) {
                for (String column : keyListWithExpression(ctx.keyListWithExpression())) {
                    out.addIndex(currentTable, column, "SOURCE_INDEX", "CREATE_TABLE_INDEX", line);
                }
            }
            return null;
        }

        @Override
        public Void visitCreateIndex(MySqlFullGrammarParser.CreateIndexContext ctx) {
            if (ctx.createIndexTarget() == null || ctx.createIndexTarget().keyListWithExpression() == null) {
                return null;
            }
            String table = table(ctx.createIndexTarget().tableRef());
            boolean unique = ctx.UNIQUE_SYMBOL() != null;
            for (String column : keyListWithExpression(ctx.createIndexTarget().keyListWithExpression())) {
                out.addIndex(table, column, unique ? "TARGET_UNIQUE" : "SOURCE_INDEX",
                        unique ? "CREATE_UNIQUE_INDEX" : "CREATE_INDEX", ctx.getStart().getLine());
            }
            return null;
        }

        private String table(MySqlFullGrammarParser.TableRefContext ctx) {
            return out.clean(ctx == null ? "" : ctx.getText());
        }

        private boolean hasInlinePrimaryKey(MySqlFullGrammarParser.FieldDefinitionContext ctx) {
            if (ctx == null) {
                return false;
            }
            return ctx.columnAttribute().stream()
                    .anyMatch(attribute -> attribute.PRIMARY_SYMBOL() != null && attribute.KEY_SYMBOL() != null);
        }

        private boolean hasInlineUnique(MySqlFullGrammarParser.FieldDefinitionContext ctx) {
            if (ctx == null) {
                return false;
            }
            return ctx.columnAttribute().stream()
                    .anyMatch(attribute -> attribute.UNIQUE_SYMBOL() != null);
        }

        private List<String> keyList(MySqlFullGrammarParser.KeyListContext ctx) {
            if (ctx == null) {
                return List.of();
            }
            return out.nonBlank(ctx.keyPart().stream()
                    .filter(part -> part.fieldLength() == null)
                    .map(part -> part.identifier() == null ? "" : part.identifier().getText())
                    .toList());
        }

        private List<String> keyListWithExpression(MySqlFullGrammarParser.KeyListWithExpressionContext ctx) {
            if (ctx == null) {
                return List.of();
            }
            List<String> columns = new ArrayList<>();
            for (MySqlFullGrammarParser.KeyPartOrExpressionContext part : ctx.keyPartOrExpression()) {
                if (part.keyPart() != null && part.keyPart().fieldLength() == null) {
                    columns.add(part.keyPart().identifier() == null ? "" : part.keyPart().identifier().getText());
                }
            }
            return out.nonBlank(columns);
        }

        private List<String> identifierList(MySqlFullGrammarParser.IdentifierListWithParenthesesContext ctx) {
            if (ctx == null) {
                return List.of("id");
            }
            MySqlFullGrammarParser.IdentifierListContext list = ctx.identifierList();
            if (list == null) {
                return List.of("id");
            }
            return out.nonBlank(list.identifier().stream()
                    .map(ParseTree::getText)
                    .toList());
        }

    }
}
