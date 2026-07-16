package com.relationdetector.oracle.script;

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

/**
 * CN: 识别 Oracle SQL*Plus 风格的 slash object terminator、引号和注释并生成 statement records；不解析 PL/SQL 语义。
 * EN: Frames Oracle client scripts using SQL*Plus slash terminators, quotes, and comments; it does not parse PL/SQL
 * semantics or emit physical facts.
 */
public final class OracleScriptFramer implements DialectScriptFramer {
    @Override
    public ScriptFrameResult frame(ScriptFrameRequest request) {
        OracleClientScriptLexer lexer = new OracleClientScriptLexer(CharStreams.fromString(request.text()));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<ScriptLexeme> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() != Token.EOF) tokens.add(ScriptLexeme.from(token, kind(token.getType())));
        }
        return new StructuredScriptFramer().frame(request, tokens, ScriptDialect.ORACLE);
    }

    private ScriptLexemeKind kind(int type) {
        if (type == OracleClientScriptLexer.LINE_COMMENT || type == OracleClientScriptLexer.BLOCK_COMMENT) return ScriptLexemeKind.COMMENT;
        if (type == OracleClientScriptLexer.SINGLE_QUOTED || type == OracleClientScriptLexer.DOUBLE_QUOTED) return ScriptLexemeKind.QUOTED;
        if (type == OracleClientScriptLexer.SEMI) return ScriptLexemeKind.SEMICOLON;
        if (type == OracleClientScriptLexer.NEWLINE) return ScriptLexemeKind.NEWLINE;
        if (type == OracleClientScriptLexer.WS) return ScriptLexemeKind.WHITESPACE;
        if (type == OracleClientScriptLexer.WORD) return ScriptLexemeKind.WORD;
        if (type == OracleClientScriptLexer.DOT) return ScriptLexemeKind.DOT;
        if (type == OracleClientScriptLexer.CREATE) return ScriptLexemeKind.CREATE;
        if (type == OracleClientScriptLexer.OR) return ScriptLexemeKind.OR;
        if (type == OracleClientScriptLexer.REPLACE) return ScriptLexemeKind.REPLACE;
        if (type == OracleClientScriptLexer.ALTER) return ScriptLexemeKind.ALTER;
        if (type == OracleClientScriptLexer.PROCEDURE) return ScriptLexemeKind.PROCEDURE;
        if (type == OracleClientScriptLexer.FUNCTION) return ScriptLexemeKind.FUNCTION;
        if (type == OracleClientScriptLexer.TRIGGER) return ScriptLexemeKind.TRIGGER;
        if (type == OracleClientScriptLexer.PACKAGE) return ScriptLexemeKind.PACKAGE;
        if (type == OracleClientScriptLexer.BODY) return ScriptLexemeKind.BODY;
        if (type == OracleClientScriptLexer.EVENT) return ScriptLexemeKind.EVENT;
        if (type == OracleClientScriptLexer.VIEW) return ScriptLexemeKind.VIEW;
        if (type == OracleClientScriptLexer.MATERIALIZED) return ScriptLexemeKind.MATERIALIZED;
        if (type == OracleClientScriptLexer.RETURNS) return ScriptLexemeKind.RETURNS;
        if (type == OracleClientScriptLexer.EDITIONABLE) return ScriptLexemeKind.EDITIONABLE;
        if (type == OracleClientScriptLexer.NONEDITIONABLE) return ScriptLexemeKind.NONEDITIONABLE;
        if (type == OracleClientScriptLexer.TEMPORARY) return ScriptLexemeKind.TEMPORARY;
        if (type == OracleClientScriptLexer.TEMP) return ScriptLexemeKind.TEMP;
        if (type == OracleClientScriptLexer.TABLE) return ScriptLexemeKind.TABLE;
        if (type == OracleClientScriptLexer.IF) return ScriptLexemeKind.IF;
        if (type == OracleClientScriptLexer.NOT) return ScriptLexemeKind.NOT;
        if (type == OracleClientScriptLexer.EXISTS) return ScriptLexemeKind.EXISTS;
        return ScriptLexemeKind.SYMBOL;
    }
}
