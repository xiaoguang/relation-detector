package com.relationdetector.contracts.parse;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;

/**
 *
 * Framed server statements extracted from one dialect client script.
 */
public record ScriptFrameResult(
        List<SqlStatementRecord> statements,
        List<WarningMessage> warnings
) {
    public ScriptFrameResult {
        statements = statements == null ? List.of() : List.copyOf(statements);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ScriptFrameResult empty() {
        return new ScriptFrameResult(List.of(), List.of());
    }
}
