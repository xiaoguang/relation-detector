package com.relationdetector.postgres.script;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.ScriptFrameRequest;
import com.relationdetector.contracts.parse.ScriptFrameResult;
import com.relationdetector.contracts.spi.DialectScriptFramer;
import com.relationdetector.core.script.ScriptDialect;
import com.relationdetector.core.script.ScriptLexeme;
import com.relationdetector.core.script.ScriptLexemeKind;
import com.relationdetector.core.script.StructuredScriptFramer;

public final class PostgresScriptFramer implements DialectScriptFramer {
    @Override
    public ScriptFrameResult frame(ScriptFrameRequest request) {
        PostgresClientScriptLexer lexer = new PostgresClientScriptLexer(CharStreams.fromString(request.text()));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<ScriptLexeme> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() != Token.EOF) tokens.add(ScriptLexeme.from(token, kind(token.getType())));
        }
        return new StructuredScriptFramer().frame(request, tokens, ScriptDialect.POSTGRESQL);
    }

    private ScriptLexemeKind kind(int type) {
        if (type == PostgresClientScriptLexer.LINE_COMMENT || type == PostgresClientScriptLexer.BLOCK_COMMENT) return ScriptLexemeKind.COMMENT;
        if (type == PostgresClientScriptLexer.SINGLE_QUOTED || type == PostgresClientScriptLexer.ESCAPE_QUOTED
                || type == PostgresClientScriptLexer.DOUBLE_QUOTED) return ScriptLexemeKind.QUOTED;
        if (type == PostgresClientScriptLexer.DOLLAR_TAG) return ScriptLexemeKind.DOLLAR_TAG;
        if (type == PostgresClientScriptLexer.SEMI) return ScriptLexemeKind.SEMICOLON;
        if (type == PostgresClientScriptLexer.NEWLINE) return ScriptLexemeKind.NEWLINE;
        if (type == PostgresClientScriptLexer.WS) return ScriptLexemeKind.WHITESPACE;
        if (type == PostgresClientScriptLexer.WORD) return ScriptLexemeKind.WORD;
        if (type == PostgresClientScriptLexer.DOT) return ScriptLexemeKind.DOT;
        if (type == PostgresClientScriptLexer.CREATE) return ScriptLexemeKind.CREATE;
        if (type == PostgresClientScriptLexer.OR) return ScriptLexemeKind.OR;
        if (type == PostgresClientScriptLexer.REPLACE) return ScriptLexemeKind.REPLACE;
        if (type == PostgresClientScriptLexer.ALTER) return ScriptLexemeKind.ALTER;
        if (type == PostgresClientScriptLexer.PROCEDURE) return ScriptLexemeKind.PROCEDURE;
        if (type == PostgresClientScriptLexer.FUNCTION) return ScriptLexemeKind.FUNCTION;
        if (type == PostgresClientScriptLexer.TRIGGER) return ScriptLexemeKind.TRIGGER;
        if (type == PostgresClientScriptLexer.PACKAGE) return ScriptLexemeKind.PACKAGE;
        if (type == PostgresClientScriptLexer.BODY) return ScriptLexemeKind.BODY;
        if (type == PostgresClientScriptLexer.EVENT) return ScriptLexemeKind.EVENT;
        if (type == PostgresClientScriptLexer.VIEW) return ScriptLexemeKind.VIEW;
        if (type == PostgresClientScriptLexer.MATERIALIZED) return ScriptLexemeKind.MATERIALIZED;
        if (type == PostgresClientScriptLexer.RETURNS) return ScriptLexemeKind.RETURNS;
        if (type == PostgresClientScriptLexer.TEMPORARY) return ScriptLexemeKind.TEMPORARY;
        if (type == PostgresClientScriptLexer.TEMP) return ScriptLexemeKind.TEMP;
        if (type == PostgresClientScriptLexer.TABLE) return ScriptLexemeKind.TABLE;
        if (type == PostgresClientScriptLexer.IF) return ScriptLexemeKind.IF;
        if (type == PostgresClientScriptLexer.NOT) return ScriptLexemeKind.NOT;
        if (type == PostgresClientScriptLexer.EXISTS) return ScriptLexemeKind.EXISTS;
        return ScriptLexemeKind.SYMBOL;
    }
}
