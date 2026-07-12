package com.relationdetector.postgres.fullgrammar.v16;

import com.relationdetector.core.fullgrammar.*;
import java.util.List;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.postgres.fullgrammar.common.PostgresSqlEventVisitorCore;
import com.relationdetector.postgres.fullgrammar.common.PostgresFullGrammarEventOutcome;
import com.relationdetector.postgres.plpgsql.v16.GeneratedPlPgSqlBodyParser;
import com.relationdetector.postgres.routine.PostgresRoutineLanguageDispatcher;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.CreatefunctionstmtContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Common_table_exprContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.A_exprContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.A_expr_andContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.A_expr_inContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.C_expr_existsContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Case_exprContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.DeletestmtContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Func_asContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Func_alias_clauseContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.In_expr_selectContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.InsertstmtContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Join_qualContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Merge_insert_clauseContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Merge_update_clauseContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.MergestmtContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Set_clauseContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Select_clauseContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Select_with_parensContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Target_elContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Simple_select_pramaryContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Table_joinContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Table_primaryContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Table_refContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Target_labelContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.Target_starContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.UpdatestmtContext;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParserBaseVisitor;
import org.antlr.v4.runtime.tree.ParseTree;

/** PostgreSQL 16 typed parse-tree traversal; shared semantics stay in core/common helpers. */
final class PostgresFullGrammarParseTreeVisitor extends PostgresFullGrammarParserBaseVisitor<Void> {
    private final SqlStatementRecord statement;
    private final PostgresSqlEventVisitorCore core;
    private final FullGrammarEventFacade sink;
    private final PostgresMergeEventSupport mergeEvents;
    private final java.util.List<com.relationdetector.contracts.model.WarningMessage> warnings = new java.util.ArrayList<>();
    private int ownedJoinPredicateDepth;
    private final PostgresSetProjectionSupport setProjections;

    PostgresFullGrammarParseTreeVisitor(SqlStatementRecord statement, List<?> visibleTokens) {
        this.statement = statement;
        this.core = new PostgresSqlEventVisitorCore(statement, new PostgresParseTreeAdapter());
        this.sink = core.sink();
        this.mergeEvents = new PostgresMergeEventSupport(core, sink);
        this.setProjections = new PostgresSetProjectionSupport(
                statement, sink, warnings, this::projectedColumnName);
    }
    /**
     * 访问 parse tree 并返回该 SQL 的结构事件。
     *
     * <p>EN: Visits the parse tree and returns structured events for the SQL.
     */
    List<StructuredSqlEvent> extract(ParseTree tree) {
        if (tree != null) {
            visit(tree);
        }
        return core.mergedEvents();
    }
    PostgresFullGrammarEventOutcome extractOutcome(ParseTree tree) {
        return new PostgresFullGrammarEventOutcome(extract(tree), warnings);
    }
    @Override
    public Void visitCreatefunctionstmt(CreatefunctionstmtContext ctx) {
        var adapter = new RoutineDeclarationAdapter();
        var descriptor = adapter.describe(ctx, statement);
        if (descriptor.isEmpty()) return null;
        var outcome = new PostgresRoutineLanguageDispatcher(new GeneratedPlPgSqlBodyParser()).dispatch(
                descriptor.get(),
                com.relationdetector.postgres.routine.PostgresRoutineAttributes
                        .withNonColumnIdentifiers(statement, adapter.parameterNames(ctx)),
                null, new FullGrammarDialectModule().sqlParser());
        warnings.addAll(outcome.warnings());
        sink.events().addAll(outcome.events());
        return null;
    }
    @Override
    public Void visitTable_ref(Table_refContext ctx) {
        if (ctx.table_primary() != null) {
            visit(ctx.table_primary());
        }
        for (Table_joinContext join : ctx.table_join()) {
            visit(join);
        }
        return null;
    }

    @Override
    public Void visitTable_primary(Table_primaryContext ctx) {
        if (ctx.relation_expr() != null) {
            String table = ctx.relation_expr().qualified_name() == null
                    ? ctx.relation_expr().getText()
                    : ctx.relation_expr().qualified_name().getText();
            String alias = ctx.alias_clause() == null ? "" : firstAlias(ctx.alias_clause());
            sink.rowset(ctx, "FROM", table, alias);
            rememberRowset(alias.isBlank() ? sink.baseName(table) : alias);
        } else if (ctx.select_with_parens() != null) {
            String alias = ctx.alias_clause() == null ? "" : firstAlias(ctx.alias_clause());
            if (!alias.isBlank()) {
                sink.ignoredRowset(ctx, alias, "DERIVED_TABLE");
                sink.rowset(ctx, "FROM", alias, alias);
                rememberRowset(alias);
                sink.withProjectionOwner(alias, () -> visit(ctx.select_with_parens()));
            }
        } else if (ctx.func_table() != null) {
            String alias = ctx.func_alias_clause() == null ? "FUNCTION_ROWSET"
                    : ctx.func_alias_clause().alias_clause() != null
                            ? firstAlias(ctx.func_alias_clause().alias_clause())
                            : firstIdentifier(ctx.func_alias_clause());
            sink.ignoredRowset(ctx, alias, "FUNCTION_ROWSET");
            sink.rowset(ctx, "FROM", alias, alias);
            rememberRowset(alias);
        } else {
            return visitChildren(ctx);
        }
        return null;
    }

    @Override
    public Void visitTable_join(Table_joinContext ctx) {
        String left = lastRowsetAlias();
        if (ctx.table_primary() != null) {
            visit(ctx.table_primary());
        }
        String right = lastRowsetAlias();
        Join_qualContext join = ctx.join_qual();
        if (join != null) {
            if (join.a_expr() != null) {
                if (core.inExists()) {
                    sink.existsPredicateEqualities(join.a_expr(), join.a_expr());
                } else {
                    sink.predicateEqualities(join.a_expr(), join.a_expr(), joinKind(ctx));
                }
                ownedJoinPredicateDepth++;
                try {
                    visit(join.a_expr());
                } finally {
                    ownedJoinPredicateDepth--;
                }
            }
            if (join.name_list() != null && !left.isBlank() && !right.isBlank()) {
                sink.joinUsing(join, left, right, sink.identifiers(join.name_list()));
            }
        }
        return null;
    }

    private String joinKind(Table_joinContext ctx) {
        if (ctx.CROSS() != null) return "CROSS_JOIN";
        if (ctx.join_type() == null) return "JOIN_ON";
        if (ctx.join_type().LEFT() != null) return "LEFT_JOIN";
        if (ctx.join_type().RIGHT() != null) return "RIGHT_JOIN";
        if (ctx.join_type().FULL() != null) return "FULL_JOIN";
        return "JOIN_ON";
    }

    @Override
    public Void visitCommon_table_expr(Common_table_exprContext ctx) {
        String name = ctx.name() == null ? "" : ctx.name().getText();
        sink.cte(ctx, name);
        List<String> explicitColumns = ctx.name_list_() == null ? List.of()
                : ctx.name_list_().name_list().name().stream().map(ParseTree::getText).toList();
        setProjections.pushColumnHints(explicitColumns);
        sink.withProjectionOwner(name, () -> {
            if (ctx.preparablestmt() != null) {
                visit(ctx.preparablestmt());
            }
        });
        setProjections.popColumnHints();
        return null;
    }

    @Override
    public Void visitSimple_select_pramary(Simple_select_pramaryContext ctx) {
        boolean insertSelect = core.hasInsertSelectTarget();
        if (insertSelect) core.enterInsertSelectQuery();
        try {
            sink.withSelectScope(() -> {
            if (ctx.from_clause() != null) {
                visit(ctx.from_clause());
            }
            if (ctx.where_clause() != null) {
                visit(ctx.where_clause());
            }
            if (ctx.group_clause() != null) {
                visit(ctx.group_clause());
            }
            if (ctx.having_clause() != null) {
                visit(ctx.having_clause());
            }
            if (ctx.target_list() != null) {
                visit(ctx.target_list());
            }
            if (ctx.target_list_() != null) {
                visit(ctx.target_list_());
            }
            if (ctx.window_clause() != null) {
                visit(ctx.window_clause());
            }
            if (ctx.select_with_parens() != null) {
                visit(ctx.select_with_parens());
            }
            });
        } finally {
            if (insertSelect) core.leaveInsertSelectQuery();
        }
        return null;
    }

    @Override
    public Void visitSelect_clause(Select_clauseContext ctx) {
        return setProjections.visit(ctx, this::visit) ? null : visitChildren(ctx);
    }

    @Override
    public Void visitUpdatestmt(UpdatestmtContext ctx) {
        String table = ctx.relation_expr_opt_alias() == null ? "" : firstIdentifier(ctx.relation_expr_opt_alias());
        String alias = ctx.relation_expr_opt_alias() == null ? "" : lastIdentifier(ctx.relation_expr_opt_alias());
        sink.rowset(ctx, "UPDATE", table, alias.equals(table) ? "" : alias);
        rememberRowset(alias.equals(table) ? table : alias);
        sink.writeTarget(ctx, table, alias.equals(table) ? "" : alias);
        sink.withWriteTarget(table, () -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitDeletestmt(DeletestmtContext ctx) {
        String table = ctx.relation_expr_opt_alias() == null ? "" : firstIdentifier(ctx.relation_expr_opt_alias());
        String alias = ctx.relation_expr_opt_alias() == null ? "" : lastIdentifier(ctx.relation_expr_opt_alias());
        sink.rowset(ctx, "DELETE", table, alias.equals(table) ? "" : alias);
        rememberRowset(alias.equals(table) ? table : alias);
        return visitChildren(ctx);
    }

    @Override
    public Void visitInsertstmt(InsertstmtContext ctx) {
        if (ctx.insert_target() == null
                || ctx.insert_target().qualified_name() == null
                || ctx.insert_rest() == null
                || ctx.insert_rest().insert_column_list() == null
                || ctx.insert_rest().selectstmt() == null) {
            return visitChildren(ctx);
        }
        String targetTable = ctx.insert_target().qualified_name().getText();
        List<String> targetColumns = sink.identifiers(ctx.insert_rest().insert_column_list());
        core.pushInsertSelectTarget(targetTable, targetColumns);
        try {
            visit(ctx.insert_rest().selectstmt());
        } finally {
            core.popInsertSelectTarget();
        }
        return null;
    }

    @Override
    public Void visitSet_clause(Set_clauseContext ctx) {
        if (ctx.set_target() == null || ctx.a_expr() == null) {
            return visitChildren(ctx);
        }
        String targetColumn = ctx.set_target().colid() == null ? ctx.set_target().getText() : ctx.set_target().colid().getText();
        sink.updateAssignment(ctx, "", sink.currentWriteTarget(), targetColumn, ctx.a_expr());
        return visitChildren(ctx);
    }

    @Override
    public Void visitCase_expr(Case_exprContext ctx) {
        ParseTree selector = ctx.case_arg() == null ? null : ctx.case_arg().a_expr();
        if (selector != null) visit(selector);
        if (ctx.when_clause_list() != null) {
            ctx.when_clause_list().when_clause().forEach(clause -> {
                if (clause.a_expr().size() == 2) {
                    sink.withCaseBranchGuards(selector, clause.a_expr(0), () -> {
                        visit(clause.a_expr(0));
                        visit(clause.a_expr(1));
                    });
                }
            });
        }
        if (ctx.case_default() != null && ctx.case_default().a_expr() != null) {
            visit(ctx.case_default().a_expr());
        }
        return null;
    }

    @Override
    public Void visitA_expr(A_exprContext ctx) {
        if (core.inExists()) {
            sink.existsPredicateEqualities(ctx, ctx);
        } else if (ownedJoinPredicateDepth == 0) {
            sink.predicateEqualities(ctx, ctx, "WHERE_OR_UNKNOWN");
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitA_expr_and(A_expr_andContext ctx) {
        if (ctx.a_expr_between().size() <= 1) return visitChildren(ctx);
        sink.withPredicateGuards(ctx, () -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitA_expr_in(A_expr_inContext ctx) {
        if (ctx.IN_P() != null && ctx.in_expr() instanceof In_expr_selectContext select) {
            sink.inSubqueryPredicate(ctx, ctx.a_expr_unary_not(), select.select_with_parens());
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitC_expr_exists(C_expr_existsContext ctx) {
        core.enterExists();
        try {
            return visitChildren(ctx);
        } finally {
            core.leaveExists();
        }
    }

    @Override
    public Void visitTarget_label(Target_labelContext ctx) {
        if (ctx.a_expr() != null && core.inDirectInsertSelectQuery()) {
            PostgresSqlEventVisitorCore.InsertSelectTarget state = core.currentInsertSelectTarget();
            if (state.index() < state.targetColumns().size()) {
                sink.insertSelect(ctx, "", state.targetTable(), state.targetColumns().get(state.index()), ctx.a_expr());
            }
            core.advanceInsertSelectTarget();
        }
        if (ctx.a_expr() == null || sink.currentProjectionOwner().isBlank()) {
            return visitChildren(ctx);
        }
        String outputColumn = ctx.bareColLabel() != null
                ? ctx.bareColLabel().getText()
                : ctx.colLabel() != null ? ctx.colLabel().getText() : projectedColumnName(ctx.a_expr());
        outputColumn = setProjections.nextOutputColumn(outputColumn);
        sink.projection(ctx, sink.currentProjectionOwner(), outputColumn, ctx.a_expr());
        return visitChildren(ctx);
    }

    @Override
    public Void visitTarget_star(Target_starContext ctx) {
        if (!sink.currentProjectionOwner().isBlank()) {
            sink.wildcardProjection(ctx, sink.currentProjectionOwner());
        }
        return null;
    }

    @Override
    public Void visitMergestmt(MergestmtContext ctx) {
        mergeEvents.visitStatement(ctx, this::visit);
        return visitChildren(ctx);
    }

    @Override
    public Void visitMerge_update_clause(Merge_update_clauseContext ctx) {
        mergeEvents.visitUpdate(() -> visitChildren(ctx));
        return null;
    }

    @Override
    public Void visitMerge_insert_clause(Merge_insert_clauseContext ctx) {
        mergeEvents.visitInsert(ctx);
        return visitChildren(ctx);
    }

    private String firstAlias(ParseTree tree) {
        return core.firstAlias(tree);
    }

    private String firstIdentifier(ParseTree tree) {
        return core.firstIdentifier(tree);
    }

    private String lastIdentifier(ParseTree tree) {
        return core.lastIdentifier(tree);
    }

    private String projectedColumnName(ParseTree expression) {
        return core.projectedColumnName(expression);
    }

    private void rememberRowset(String aliasOrTable) {
        core.rememberRowset(aliasOrTable);
    }

    private String lastRowsetAlias() {
        return core.lastRowsetAlias();
    }
}
