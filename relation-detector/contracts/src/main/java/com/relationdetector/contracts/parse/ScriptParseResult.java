package com.relationdetector.contracts.parse;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;

/** Parser-ready server statements extracted from one dialect client script. */
public record ScriptParseResult(
        List<SqlStatementRecord> statements,
        List<WarningMessage> warnings
) {
    public ScriptParseResult {
        statements = statements == null ? List.of() : List.copyOf(statements);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ScriptParseResult empty() {
        return new ScriptParseResult(List.of(), List.of());
    }
}
