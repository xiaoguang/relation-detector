package com.relationdetector.postgres.fullgrammar.v18;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;
import com.relationdetector.postgres.fullgrammar.v18.PostgresFullGrammarParser.Set_clauseContext;
import com.relationdetector.postgres.fullgrammar.v18.PostgresFullGrammarParser.UpdatestmtContext;

/** Emits PostgreSQL 18 UPDATE locator controls from typed contexts. */
final class PostgresUpdateControlEmitter {
    private PostgresUpdateControlEmitter() {
    }

    static void emit(
            UpdatestmtContext update,
            String targetAlias,
            String targetTable,
            FullGrammarEventFacade sink
    ) {
        if (update.where_or_current_clause() == null
                || update.where_or_current_clause().a_expr() == null
                || update.set_clause_list() == null) {
            return;
        }
        for (Set_clauseContext clause : update.set_clause_list().set_clause()) {
            if (clause.set_target() == null) {
                continue;
            }
            String column = clause.set_target().colid() == null
                    ? clause.set_target().getText() : clause.set_target().colid().getText();
            sink.updateControl(update.where_or_current_clause(), targetAlias, targetTable,
                    column, update.where_or_current_clause().a_expr());
        }
    }
}
