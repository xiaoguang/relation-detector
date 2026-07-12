package com.relationdetector.sqlserver.routine;

import com.relationdetector.core.fullgrammar.FullGrammarEventFacade;

/** Shared T-SQL routine scope rules. */
public final class SqlServerRoutineScopePolicy {
    private SqlServerRoutineScopePolicy() {
    }

    public static void markParameterOrVariable(FullGrammarEventFacade sink, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        sink.nonColumnIdentifier(identifier);
    }

    public static boolean isTemporaryTable(String tableName) {
        return tableName != null && tableName.strip().startsWith("#");
    }
}
