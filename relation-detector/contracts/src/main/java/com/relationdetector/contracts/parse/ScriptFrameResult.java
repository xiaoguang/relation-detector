package com.relationdetector.contracts.parse;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;

/**
 * CN: 承载一份方言客户端脚本 framing 后的 server statements 与 framing warnings。
 * EN: Carries framed server statements and framing warnings extracted from one dialect client script.
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
