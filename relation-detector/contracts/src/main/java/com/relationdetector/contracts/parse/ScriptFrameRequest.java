package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 *
 * Raw client script plus the provenance defaults used by a dialect script framer.
 */
public record ScriptFrameRequest(
        String text,
        String sourceFile,
        StatementSourceType defaultSourceType
) {
    public ScriptFrameRequest {
        text = text == null ? "" : text;
        sourceFile = sourceFile == null ? "" : sourceFile;
        defaultSourceType = defaultSourceType == null ? StatementSourceType.PLAIN_SQL : defaultSourceType;
    }
}
