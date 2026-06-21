package com.relationdetector.mysql.fullgrammer.v8_0;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Emits MySQL DDL relationship facts from full-grammer typed parse-tree contexts. */
final class MySqlFullGrammerDdlEventCollector {
    List<StructuredSqlEvent> collect(String sourceName, ParserRuleContext root) {
        if (root == null) {
            return List.of();
        }
        MysqlDdlVisitor visitor = new MysqlDdlVisitor(sourceName);
        visitor.visit(root);
        return visitor.events();
    }

    private abstract static class BaseDdlVisitor {
        final String sourceName;
        final List<StructuredSqlEvent> events = new ArrayList<>();

        BaseDdlVisitor(String sourceName) {
            this.sourceName = sourceName;
        }

        List<StructuredSqlEvent> events() {
            return List.copyOf(events);
        }

        void addForeignKeyEvents(
                String sourceTable,
                List<String> sourceColumns,
                String targetTable,
                List<String> targetColumns,
                long line
        ) {
            int count = Math.min(sourceColumns.size(), targetColumns.size());
            for (int i = 0; i < count; i++) {
                Map<String, Object> attributes = new LinkedHashMap<>();
                attributes.put("sourceTable", sourceTable);
                attributes.put("sourceColumn", sourceColumns.get(i));
                attributes.put("targetTable", targetTable);
                attributes.put("targetColumn", targetColumns.get(i));
                attributes.put("compositePosition", i + 1);
                attributes.put("compositeSize", count);
                events.add(new StructuredSqlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, sourceName, line, attributes));
            }
        }

        void addIndex(String table, String column, String role, String kind, long line) {
            events.add(new StructuredSqlEvent(StructuredParseEventType.DDL_INDEX, sourceName, line,
                    Map.of("table", table, "column", column, "role", role, "kind", kind)));
        }

        String clean(String value) {
            if (value == null) {
                return "";
            }
            String text = value.strip();
            while ((text.startsWith("`") && text.endsWith("`"))
                    || (text.startsWith("\"") && text.endsWith("\""))
                    || (text.startsWith("[") && text.endsWith("]"))) {
                text = text.substring(1, text.length() - 1);
            }
            return text.replace("`.", ".")
                    .replace(".`", ".")
                    .replace("\".", ".")
                    .replace(".\"", ".")
                    .replace(" ", "");
        }

        List<String> nonBlank(List<String> values) {
            return values.stream().map(this::clean).filter(value -> !value.isBlank()).toList();
        }
    }

    private static final class MysqlDdlVisitor extends MySqlFullGrammerParserBaseVisitor<Void> {
        private final BaseDdlVisitor out;
        private String currentTable = "";

        MysqlDdlVisitor(String sourceName) {
            this.out = new BaseDdlVisitor(sourceName) {
            };
        }

        List<StructuredSqlEvent> events() {
            return out.events();
        }

        @Override
        public Void visitCreateTable(MySqlFullGrammerParser.CreateTableContext ctx) {
            String previous = currentTable;
            currentTable = out.clean(ctx.tableName() == null ? "" : ctx.tableName().getText());
            if (ctx.tableElementList() != null) {
                visit(ctx.tableElementList());
            }
            currentTable = previous;
            return null;
        }

        @Override
        public Void visitColumnDefinition(MySqlFullGrammerParser.ColumnDefinitionContext ctx) {
            if (currentTable.isBlank()) {
                return null;
            }
            String column = out.clean(ctx.columnName() == null ? "" : ctx.columnName().getText());
            if (hasInlinePrimaryKey(ctx.fieldDefinition())) {
                out.addIndex(currentTable, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", ctx.getStart().getLine());
            }
            if (hasInlineUnique(ctx.fieldDefinition())) {
                out.addIndex(currentTable, column, "TARGET_UNIQUE", "INLINE_UNIQUE", ctx.getStart().getLine());
            }
            MySqlFullGrammerParser.ReferencesContext references =
                    firstChild(ctx, MySqlFullGrammerParser.ReferencesContext.class);
            if (references != null) {
                out.addForeignKeyEvents(currentTable, List.of(column), table(references.tableRef()),
                        identifierList(references.identifierListWithParentheses()), ctx.getStart().getLine());
            }
            return super.visitColumnDefinition(ctx);
        }

        @Override
        public Void visitTableConstraintDef(MySqlFullGrammerParser.TableConstraintDefContext ctx) {
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
        public Void visitCreateIndex(MySqlFullGrammerParser.CreateIndexContext ctx) {
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

        private String table(MySqlFullGrammerParser.TableRefContext ctx) {
            return out.clean(ctx == null ? "" : ctx.getText());
        }

        private boolean hasInlinePrimaryKey(MySqlFullGrammerParser.FieldDefinitionContext ctx) {
            if (ctx == null) {
                return false;
            }
            return ctx.columnAttribute().stream()
                    .anyMatch(attribute -> attribute.PRIMARY_SYMBOL() != null && attribute.KEY_SYMBOL() != null);
        }

        private boolean hasInlineUnique(MySqlFullGrammerParser.FieldDefinitionContext ctx) {
            if (ctx == null) {
                return false;
            }
            return ctx.columnAttribute().stream()
                    .anyMatch(attribute -> attribute.UNIQUE_SYMBOL() != null);
        }

        private List<String> keyList(MySqlFullGrammerParser.KeyListContext ctx) {
            if (ctx == null) {
                return List.of();
            }
            return out.nonBlank(ctx.keyPart().stream()
                    .filter(part -> part.fieldLength() == null)
                    .map(part -> part.identifier() == null ? "" : part.identifier().getText())
                    .toList());
        }

        private List<String> keyListWithExpression(MySqlFullGrammerParser.KeyListWithExpressionContext ctx) {
            if (ctx == null) {
                return List.of();
            }
            List<String> columns = new ArrayList<>();
            for (MySqlFullGrammerParser.KeyPartOrExpressionContext part : ctx.keyPartOrExpression()) {
                if (part.keyPart() != null && part.keyPart().fieldLength() == null) {
                    columns.add(part.keyPart().identifier() == null ? "" : part.keyPart().identifier().getText());
                }
            }
            return out.nonBlank(columns);
        }

        private List<String> identifierList(MySqlFullGrammerParser.IdentifierListWithParenthesesContext ctx) {
            if (ctx == null) {
                return List.of("id");
            }
            MySqlFullGrammerParser.IdentifierListContext list = ctx.identifierList();
            if (list == null) {
                return List.of("id");
            }
            return out.nonBlank(list.identifier().stream()
                    .map(ParseTree::getText)
                    .toList());
        }

        private <T extends ParserRuleContext> T firstChild(ParserRuleContext ctx, Class<T> type) {
            if (ctx == null) {
                return null;
            }
            if (type.isInstance(ctx)) {
                return type.cast(ctx);
            }
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof ParserRuleContext childContext) {
                    T found = firstChild(childContext, type);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }
    }
}
