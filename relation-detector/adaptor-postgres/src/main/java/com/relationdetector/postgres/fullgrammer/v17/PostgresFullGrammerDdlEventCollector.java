package com.relationdetector.postgres.fullgrammer.v17;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.postgres.fullgrammer.common.PostgresDdlEventVisitorCore;
import com.relationdetector.postgres.fullgrammer.common.PostgresDdlVersionPolicy;

/**
 * PostgreSQL full-grammer DDL event collector。
 *
 * <p>CN: 从 PostgreSQL full grammar typed parse-tree contexts 中抽取 FK/index 事实，
 * 并输出统一 DDL structured events。它不调用 token-event DDL cursor。
 *
 * <p>EN: PostgreSQL full-grammer DDL event collector. It extracts FK/index facts
 * from typed parse-tree contexts and emits unified DDL structured events. It
 * does not call the token-event DDL cursor.
 */
final class PostgresFullGrammerDdlEventCollector {
    private final PostgresDdlVersionPolicy policy;

    PostgresFullGrammerDdlEventCollector(PostgresDdlVersionPolicy policy) {
        this.policy = policy;
    }

    List<StructuredSqlEvent> collect(String sourceName, ParserRuleContext root) {
        if (root == null) {
            return List.of();
        }
        PostgresDdlVisitor visitor = new PostgresDdlVisitor(sourceName, policy);
        visitor.visit(root);
        return visitor.events();
    }

    private static final class PostgresDdlVisitor extends Postgres17FullGrammerParserBaseVisitor<Void> {
        private final PostgresDdlEventVisitorCore core;
        private final PostgresDdlVersionPolicy policy;

        PostgresDdlVisitor(String sourceName, PostgresDdlVersionPolicy policy) {
            this.core = new PostgresDdlEventVisitorCore(sourceName);
            this.policy = policy;
        }

        List<StructuredSqlEvent> events() {
            return core.events();
        }

        @Override
        public Void visitCreatestmt(Postgres17FullGrammerParser.CreatestmtContext ctx) {
            core.withCurrentTable(ctx.qualified_name().isEmpty() ? "" : core.out().clean(ctx.qualified_name(0).getText()), () -> {
                if (ctx.opttableelementlist() != null) {
                    visit(ctx.opttableelementlist());
                }
            });
            return null;
        }

        @Override
        public Void visitColumnDef(Postgres17FullGrammerParser.ColumnDefContext ctx) {
            if (core.currentTable().isBlank()) {
                return null;
            }
            String column = core.out().clean(ctx.colid() == null ? "" : ctx.colid().getText());
            core.out().addColumn(core.currentTable(), column, ctx.getStart().getLine());
            if (ctx.colquallist() == null) {
                return null;
            }
            for (Postgres17FullGrammerParser.ColconstraintContext colConstraint : ctx.colquallist().colconstraint()) {
                Postgres17FullGrammerParser.ColconstraintelemContext constraint = colConstraint.colconstraintelem();
                if (constraint == null) {
                    continue;
                }
                if (constraint.PRIMARY() != null && constraint.KEY() != null) {
                    core.out().addIndex(core.currentTable(), column, "TARGET_UNIQUE", "INLINE_PRIMARY_KEY", ctx.getStart().getLine());
                }
                if (constraint.UNIQUE() != null) {
                    core.out().addIndex(core.currentTable(), column, "TARGET_UNIQUE", "INLINE_UNIQUE", ctx.getStart().getLine());
                }
                if (constraint.REFERENCES() != null && constraint.qualified_name() != null) {
                    core.out().addForeignKeyEvents(core.currentTable(), List.of(column), core.out().clean(constraint.qualified_name().getText()),
                            postgresColumnList(constraint.column_list_(), List.of("id")), ctx.getStart().getLine());
                }
            }
            return super.visitColumnDef(ctx);
        }

        @Override
        public Void visitAltertablestmt(Postgres17FullGrammerParser.AltertablestmtContext ctx) {
            if (ctx.relation_expr() == null || ctx.alter_table_cmds() == null) {
                return super.visitAltertablestmt(ctx);
            }
            core.withCurrentTable(relationExprTable(ctx.relation_expr()), () -> visit(ctx.alter_table_cmds()));
            return null;
        }

        @Override
        public Void visitTableconstraint(Postgres17FullGrammerParser.TableconstraintContext ctx) {
            if (core.currentTable().isBlank() || ctx.constraintelem() == null) {
                return null;
            }
            Postgres17FullGrammerParser.ConstraintelemContext constraint = ctx.constraintelem();
            long line = ctx.getStart().getLine();
            if (constraint.REFERENCES() != null && constraint.qualified_name() != null) {
                List<Postgres17FullGrammerParser.ColumnlistContext> columnLists =
                        constraint.getRuleContexts(Postgres17FullGrammerParser.ColumnlistContext.class);
                List<String> sourceColumns = columnLists.isEmpty() ? List.of() : postgresColumns(columnLists.get(0));
                List<String> targetColumns = postgresColumnList(constraint.column_list_(), List.of("id"));
                core.out().addForeignKeyEvents(core.currentTable(), sourceColumns, core.out().clean(constraint.qualified_name().getText()),
                        targetColumns, line);
                return null;
            }
            if (constraint.PRIMARY() != null && constraint.KEY() != null) {
                for (String column : postgresColumnList(constraint, 0, List.of())) {
                    core.out().addIndex(core.currentTable(), column, "TARGET_UNIQUE", "PRIMARY_KEY", line);
                }
            } else if (constraint.UNIQUE() != null) {
                for (String column : postgresColumnList(constraint, 0, List.of())) {
                    core.out().addIndex(core.currentTable(), column, "TARGET_UNIQUE", "UNIQUE_CONSTRAINT", line);
                }
            }
            return null;
        }

        @Override
        public Void visitIndexstmt(Postgres17FullGrammerParser.IndexstmtContext ctx) {
            if (ctx.where_clause() != null) {
                return null;
            }
            if (ctx.relation_expr() == null || ctx.index_params() == null) {
                return null;
            }
            String table = relationExprTable(ctx.relation_expr());
            boolean unique = ctx.unique_() != null;
            for (Postgres17FullGrammerParser.Index_elemContext elem : ctx.index_params().index_elem()) {
                String column = postgresIndexColumn(elem);
                if (column.isBlank()) {
                    continue;
                }
                core.out().addIndex(table, column, unique ? "TARGET_UNIQUE" : "SOURCE_INDEX",
                        unique ? "CREATE_UNIQUE_INDEX" : "CREATE_INDEX", ctx.getStart().getLine());
            }
            return null;
        }

        private String relationExprTable(Postgres17FullGrammerParser.Relation_exprContext ctx) {
            return ctx == null || ctx.qualified_name() == null ? "" : core.out().clean(ctx.qualified_name().getText());
        }

        private List<String> postgresColumnList(
                Postgres17FullGrammerParser.ConstraintelemContext constraint,
                int index,
                List<String> fallback
        ) {
            List<Postgres17FullGrammerParser.ColumnlistContext> lists =
                    constraint.getRuleContexts(Postgres17FullGrammerParser.ColumnlistContext.class);
            if (index >= lists.size()) {
                return fallback;
            }
            return postgresColumns(lists.get(index));
        }

        private List<String> postgresColumnList(
                Postgres17FullGrammerParser.Column_list_Context columnList,
                List<String> fallback
        ) {
            if (columnList == null || columnList.columnlist() == null) {
                return fallback;
            }
            return postgresColumns(columnList.columnlist());
        }

        private List<String> postgresColumns(Postgres17FullGrammerParser.ColumnlistContext ctx) {
            return core.out().nonBlank(ctx.getRuleContexts(Postgres17FullGrammerParser.ColumnElemContext.class).stream()
                    .map(this::postgresColumn)
                    .toList());
        }

        private String postgresColumn(Postgres17FullGrammerParser.ColumnElemContext elem) {
            if (elem == null || elem.colid() == null) {
                return "";
            }
            return elem.colid().getText();
        }

        private String postgresIndexColumn(Postgres17FullGrammerParser.Index_elemContext elem) {
            if (elem.colid() != null) {
                return core.out().clean(elem.colid().getText());
            }
            return "";
        }

    }
}
