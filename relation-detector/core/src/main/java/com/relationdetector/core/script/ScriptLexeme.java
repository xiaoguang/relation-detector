package com.relationdetector.core.script;

import org.antlr.v4.runtime.Token;

/** One generated-lexer token with its exact source interval. */
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
