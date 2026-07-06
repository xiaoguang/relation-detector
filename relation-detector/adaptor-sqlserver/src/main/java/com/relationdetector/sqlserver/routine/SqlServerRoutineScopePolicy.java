package com.relationdetector.sqlserver.routine;

import com.relationdetector.core.fullgrammer.FullGrammerTypedSqlEventSink;

/** Shared T-SQL routine scope rules. */
public final class SqlServerRoutineScopePolicy {
    private SqlServerRoutineScopePolicy() {
    }

    public static void markParameterOrVariable(FullGrammerTypedSqlEventSink sink, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return;
        }
        sink.nonColumnIdentifier(identifier);
    }

    public static boolean isTemporaryTable(String tableName) {
        return tableName != null && tableName.strip().startsWith("#");
    }
}
