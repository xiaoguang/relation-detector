package com.relationdetector.core.script;

import org.antlr.v4.runtime.Token;

/**
 * CN: 承载 generated script lexer token 的类别、原文与精确字符/行列区间。
 * EN: Carries one generated script-lexer token with its category, text, and exact character and line/column interval.
 */
public record ScriptLexeme(
        ScriptLexemeKind kind,
        String text,
        int startOffset,
        int endOffset,
        int line,
        int column
) {
    public ScriptLexeme {
        text = text == null ? "" : text;
    }

    public static ScriptLexeme from(Token token, ScriptLexemeKind kind) {
        return new ScriptLexeme(kind, token.getText(), token.getStartIndex(), token.getStopIndex() + 1,
                token.getLine(), token.getCharPositionInLine());
    }
}
