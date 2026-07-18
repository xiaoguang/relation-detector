package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StatementSourceType;

/**
 * CN: 为 dialect script framer 提供原始客户端脚本与默认来源 provenance。
 * EN: Supplies raw client-script text and default source provenance to a dialect script framer.
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
