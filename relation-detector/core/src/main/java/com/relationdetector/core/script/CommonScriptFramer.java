package com.relationdetector.core.script;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.spi.DialectScriptFramer;

public final class CommonScriptFramer implements DialectScriptFramer {
    @Override
    public ScriptFrameResult frame(ScriptFrameRequest request) {
        CommonScriptLexer lexer = new CommonScriptLexer(CharStreams.fromString(request.text()));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<ScriptLexeme> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() == Token.EOF) continue;
            tokens.add(ScriptLexeme.from(token, kind(token.getType())));
        }
        return new StructuredScriptFramer().frame(request, tokens, ScriptDialect.COMMON);
    }

    private ScriptLexemeKind kind(int type) {
        if (type == CommonScriptLexer.LINE_COMMENT || type == CommonScriptLexer.BLOCK_COMMENT) return ScriptLexemeKind.COMMENT;
        if (type == CommonScriptLexer.SINGLE_QUOTED || type == CommonScriptLexer.DOUBLE_QUOTED) return ScriptLexemeKind.QUOTED;
        if (type == CommonScriptLexer.SEMI) return ScriptLexemeKind.SEMICOLON;
        if (type == CommonScriptLexer.NEWLINE) return ScriptLexemeKind.NEWLINE;
        if (type == CommonScriptLexer.WS) return ScriptLexemeKind.WHITESPACE;
        if (type == CommonScriptLexer.WORD) return ScriptLexemeKind.WORD;
        if (type == CommonScriptLexer.DOT) return ScriptLexemeKind.DOT;
        if (type == CommonScriptLexer.CREATE) return ScriptLexemeKind.CREATE;
        if (type == CommonScriptLexer.OR) return ScriptLexemeKind.OR;
        if (type == CommonScriptLexer.REPLACE) return ScriptLexemeKind.REPLACE;
        if (type == CommonScriptLexer.ALTER) return ScriptLexemeKind.ALTER;
        if (type == CommonScriptLexer.PROCEDURE) return ScriptLexemeKind.PROCEDURE;
        if (type == CommonScriptLexer.FUNCTION) return ScriptLexemeKind.FUNCTION;
        if (type == CommonScriptLexer.TRIGGER) return ScriptLexemeKind.TRIGGER;
        if (type == CommonScriptLexer.PACKAGE) return ScriptLexemeKind.PACKAGE;
        if (type == CommonScriptLexer.BODY) return ScriptLexemeKind.BODY;
        if (type == CommonScriptLexer.EVENT) return ScriptLexemeKind.EVENT;
        if (type == CommonScriptLexer.VIEW) return ScriptLexemeKind.VIEW;
        if (type == CommonScriptLexer.MATERIALIZED) return ScriptLexemeKind.MATERIALIZED;
        if (type == CommonScriptLexer.RETURNS) return ScriptLexemeKind.RETURNS;
        if (type == CommonScriptLexer.TEMPORARY) return ScriptLexemeKind.TEMPORARY;
        if (type == CommonScriptLexer.TEMP) return ScriptLexemeKind.TEMP;
        if (type == CommonScriptLexer.TABLE) return ScriptLexemeKind.TABLE;
        if (type == CommonScriptLexer.IF) return ScriptLexemeKind.IF;
        if (type == CommonScriptLexer.NOT) return ScriptLexemeKind.NOT;
        if (type == CommonScriptLexer.EXISTS) return ScriptLexemeKind.EXISTS;
        if (type == CommonScriptLexer.BEGIN) return ScriptLexemeKind.BEGIN;
        if (type == CommonScriptLexer.CASE) return ScriptLexemeKind.CASE;
        if (type == CommonScriptLexer.END) return ScriptLexemeKind.END;
        return ScriptLexemeKind.SYMBOL;
    }
}
