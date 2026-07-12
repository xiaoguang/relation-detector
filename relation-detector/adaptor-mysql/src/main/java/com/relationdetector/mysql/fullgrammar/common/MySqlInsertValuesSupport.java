package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;

/** Emits typed INSERT VALUES mappings independently of versioned generated contexts. */
public final class MySqlInsertValuesSupport {
    private MySqlInsertValuesSupport() {
    }

    public static void emit(
            FullGrammarEventFacade sink,
            String target,
            List<String> targetColumns,
            List<? extends List<? extends ParserRuleContext>> rows
    ) {
        for (List<? extends ParserRuleContext> row : rows) {
            int count = Math.min(targetColumns.size(), row.size());
            for (int index = 0; index < count; index++) {
                ParserRuleContext expression = row.get(index);
                sink.insertValues(expression, "", target, targetColumns.get(index), expression);
            }
        }
    }
}
