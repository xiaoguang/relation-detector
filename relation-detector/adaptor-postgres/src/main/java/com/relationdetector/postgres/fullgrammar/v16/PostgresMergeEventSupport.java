package com.relationdetector.postgres.fullgrammar.v16;

import java.util.List;
import java.util.function.Consumer;
import org.antlr.v4.runtime.tree.ParseTree;
import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.postgres.fullgrammar.common.PostgresSqlEventVisitorCore;
import com.relationdetector.postgres.fullgrammar.v16.PostgresFullGrammarParser.*;

/** Emits PostgreSQL MERGE rowsets, predicates, and write mappings for v16 contexts. */
final class PostgresMergeEventSupport {
    private final PostgresSqlEventVisitorCore core;
    private final FullGrammarEventFacade sink;

    PostgresMergeEventSupport(PostgresSqlEventVisitorCore core, FullGrammarEventFacade sink) {
        this.core = core;
        this.sink = sink;
    }

    void visitStatement(MergestmtContext context, Consumer<ParseTree> visitor) {
        core.mergeTarget(context.qualified_name().isEmpty() ? "" : context.qualified_name(0).getText());
        String target = core.mergeTarget();
        String targetAlias = context.alias_clause().isEmpty() ? "" : core.firstAlias(context.alias_clause(0));
        if (targetAlias.isBlank()) targetAlias = sink.baseName(target);
        if (!target.isBlank()) {
            sink.rowset(context, "MERGE", target, targetAlias.equals(sink.baseName(target)) ? "" : targetAlias);
            core.rememberRowset(targetAlias);
        }
        if (context.select_with_parens() != null) {
            core.mergeSource(context.alias_clause().size() > 1 ? core.firstAlias(context.alias_clause(1)) : "");
            String source = core.mergeSource();
            if (!source.isBlank()) {
                sink.ignoredRowset(context, source, "DERIVED_TABLE");
                sink.rowset(context, "USING", source, source);
                core.rememberRowset(source);
                sink.withProjectionOwner(source, () -> visitor.accept(context.select_with_parens()));
            }
        } else if (context.qualified_name().size() > 1) {
            core.mergeSource(context.qualified_name(1).getText());
            String sourceAlias = context.alias_clause().size() > 1 ? core.firstAlias(context.alias_clause(1)) : "";
            sink.rowset(context, "USING", core.mergeSource(), sourceAlias);
            core.rememberRowset(sourceAlias.isBlank() ? sink.baseName(core.mergeSource()) : sourceAlias);
        } else core.mergeSource("");
        sink.writeTarget(context, target, targetAlias.equals(sink.baseName(target)) ? "" : targetAlias);
        if (context.a_expr() != null) sink.predicateEqualities(context, context.a_expr(), "MERGE_OR_USING");
    }

    void visitUpdate(Runnable visitor) { sink.withWriteTarget(core.mergeTarget(), visitor); }

    void visitInsert(Merge_insert_clauseContext context) {
        if (context.insert_column_list() == null || context.values_clause() == null) return;
        List<String> targets = sink.identifiers(context.insert_column_list());
        List<ParseTree> values = core.expressionChildren(context.values_clause());
        int count = Math.min(targets.size(), values.size());
        for (int index = 0; index < count; index++)
            sink.mergeInsert(context, "", core.mergeTarget(), targets.get(index), values.get(index));
    }
}
