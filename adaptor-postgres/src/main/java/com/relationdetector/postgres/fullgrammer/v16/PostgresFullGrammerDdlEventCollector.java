package com.relationdetector.postgres.fullgrammer.v16;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Emits PostgreSQL DDL relationship facts from full-grammer typed parse-tree contexts. */
final class PostgresFullGrammerDdlEventCollector {
    List<StructuredSqlEvent> collect(String sourceName, ParserRuleContext root) {
        if (root == null) {
            return List.of();
        }
        PostgresDdlVisitor visitor = new PostgresDdlVisitor(sourceName);
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

    private static final class PostgresDdlVisitor extends PostgresFullGrammerParserBaseVisitor<Void> {
        private final BaseDdlVisitor out;
        private String currentTable = "";

        PostgresDdlVisitor(String sourceName) {
            this.out = new BaseDdlVisitor(sourceName) {
            };
        }

        List<StructuredSqlEvent> events() {
            return out.events();
        }

        @Override
        public Void visitCreatestmt(PostgresFullGrammerParser.CreatestmtContext ctx) {
            String previous = currentTable;
            currentTable = ctx.qualified_name().isEmpty() ? "" : out.clean(ctx.qualified_name(0).getText());
            if (ctx.opttableelementlist() != null) {
                visit(ctx.opttableelementlist());
            }
            currentTable = previous;
            return null;
        }

        @Override
        public Void visitColumnDef(PostgresFullGrammerParser.ColumnDefContext ctx) {
            if (currentTable.isBlank()) {
                return null;
            }
            String column = out.clean(ctx.colid() == null ? "" : ctx.colid().getText());
            if (ctx.colquallist() == null) {
                return null;
            }
            for (PostgresFullGrammerParser.ColconstraintContext colConstraint : ctx.colquallist().colconstraint()) {
                PostgresFullGrammerParser.ColconstraintelemContext constraint = colConstraint.colconstraintelem();
                if (constraint == null) {
                    continue;
                }
                if (constraint.PRIMARY() != null && constraint.KEY() != null) {
                    out.addIndex(currentTable, column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", ctx.getStart().getLine());
                }
                if (constraint.UNIQUE() != null) {
                    out.addIndex(currentTable, column, "TARGET_UNIQUE", "INLINE_UNIQUE", ctx.getStart().getLine());
                }
                if (constraint.REFERENCES() != null && constraint.qualified_name() != null) {
                    out.addForeignKeyEvents(currentTable, List.of(column), out.clean(constraint.qualified_name().getText()),
                            postgresColumnList(constraint.column_list_(), List.of("id")), ctx.getStart().getLine());
                }
            }
            return super.visitColumnDef(ctx);
        }

        @Override
        public Void visitAltertablestmt(PostgresFullGrammerParser.AltertablestmtContext ctx) {
            if (ctx.relation_expr() == null || ctx.alter_table_cmds() == null) {
                return super.visitAltertablestmt(ctx);
            }
            String previous = currentTable;
            currentTable = relationExprTable(ctx.relation_expr());
            visit(ctx.alter_table_cmds());
            currentTable = previous;
            return null;
        }

        @Override
        public Void visitTableconstraint(PostgresFullGrammerParser.TableconstraintContext ctx) {
            if (currentTable.isBlank() || ctx.constraintelem() == null) {
                return null;
            }
            PostgresFullGrammerParser.ConstraintelemContext constraint = ctx.constraintelem();
            long line = ctx.getStart().getLine();
            if (constraint.REFERENCES() != null && constraint.qualified_name() != null) {
                List<PostgresFullGrammerParser.ColumnlistContext> columnLists =
                        constraint.getRuleContexts(PostgresFullGrammerParser.ColumnlistContext.class);
                List<String> sourceColumns = columnLists.isEmpty() ? List.of() : postgresColumns(columnLists.get(0));
                List<String> targetColumns = postgresColumnList(constraint.column_list_(), List.of("id"));
                out.addForeignKeyEvents(currentTable, sourceColumns, out.clean(constraint.qualified_name().getText()),
                        targetColumns, line);
                return null;
            }
            if (constraint.PRIMARY() != null && constraint.KEY() != null) {
                for (String column : postgresColumnList(constraint, 0, List.of())) {
                    out.addIndex(currentTable, column, "TARGET_UNIQUE", "PRIMARY_KEY", line);
                }
            } else if (constraint.UNIQUE() != null) {
                for (String column : postgresColumnList(constraint, 0, List.of())) {
                    out.addIndex(currentTable, column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", line);
                }
            }
            return null;
        }

        @Override
        public Void visitIndexstmt(PostgresFullGrammerParser.IndexstmtContext ctx) {
            if (ctx.where_clause() != null) {
                return null;
            }
            if (ctx.relation_expr() == null || ctx.index_params() == null) {
                return null;
            }
            String table = relationExprTable(ctx.relation_expr());
            boolean unique = ctx.unique_() != null;
            for (PostgresFullGrammerParser.Index_elemContext elem : ctx.index_params().index_elem()) {
                String column = postgresIndexColumn(elem);
                if (column.isBlank()) {
                    continue;
                }
                out.addIndex(table, column, unique ? "TARGET_UNIQUE" : "SOURCE_INDEX",
                        unique ? "CREATE_UNIQUE_INDEX" : "CREATE_INDEX", ctx.getStart().getLine());
            }
            return null;
        }

        private String relationExprTable(PostgresFullGrammerParser.Relation_exprContext ctx) {
            return ctx == null || ctx.qualified_name() == null ? "" : out.clean(ctx.qualified_name().getText());
        }

        private List<String> postgresColumnList(
                PostgresFullGrammerParser.ConstraintelemContext constraint,
                int index,
                List<String> fallback
        ) {
            List<PostgresFullGrammerParser.ColumnlistContext> lists =
                    constraint.getRuleContexts(PostgresFullGrammerParser.ColumnlistContext.class);
            if (index >= lists.size()) {
                return fallback;
            }
            return postgresColumns(lists.get(index));
        }

        private List<String> postgresColumnList(
                PostgresFullGrammerParser.Column_list_Context columnList,
                List<String> fallback
        ) {
            if (columnList == null || columnList.columnlist() == null) {
                return fallback;
            }
            return postgresColumns(columnList.columnlist());
        }

        private List<String> postgresColumns(PostgresFullGrammerParser.ColumnlistContext ctx) {
            return out.nonBlank(ctx.getRuleContexts(PostgresFullGrammerParser.ColumnElemContext.class).stream()
                    .map(ParseTree::getText)
                    .toList());
        }

        private String postgresIndexColumn(PostgresFullGrammerParser.Index_elemContext elem) {
            if (elem.colid() != null) {
                return out.clean(elem.colid().getText());
            }
            return "";
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
