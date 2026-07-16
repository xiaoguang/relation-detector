package com.relationdetector.mysql.script;

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
 * CN: 识别 MySQL 客户端 DELIMITER、引号和注释边界并生成 statement records；不解释 routine body 或 SQL 事实。
 * EN: Frames MySQL client scripts around DELIMITER directives, quotes, and comments; it does not interpret routine
 * bodies or derive SQL facts.
 */
public final class MySqlScriptFramer implements DialectScriptFramer {
    @Override
    public ScriptFrameResult frame(ScriptFrameRequest request) {
        MySqlClientScriptLexer lexer = new MySqlClientScriptLexer(CharStreams.fromString(request.text()));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<ScriptLexeme> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() != Token.EOF) tokens.add(ScriptLexeme.from(token, kind(token.getType())));
        }
        return new StructuredScriptFramer().frame(request, tokens, ScriptDialect.MYSQL);
    }

    private ScriptLexemeKind kind(int type) {
        if (type == MySqlClientScriptLexer.LINE_COMMENT || type == MySqlClientScriptLexer.BLOCK_COMMENT) return ScriptLexemeKind.COMMENT;
        if (type == MySqlClientScriptLexer.SINGLE_QUOTED || type == MySqlClientScriptLexer.DOUBLE_QUOTED
                || type == MySqlClientScriptLexer.BACKTICK_QUOTED) return ScriptLexemeKind.QUOTED;
        if (type == MySqlClientScriptLexer.SEMI) return ScriptLexemeKind.SEMICOLON;
        if (type == MySqlClientScriptLexer.NEWLINE) return ScriptLexemeKind.NEWLINE;
        if (type == MySqlClientScriptLexer.WS) return ScriptLexemeKind.WHITESPACE;
        if (type == MySqlClientScriptLexer.WORD) return ScriptLexemeKind.WORD;
        if (type == MySqlClientScriptLexer.DOT) return ScriptLexemeKind.DOT;
        if (type == MySqlClientScriptLexer.DELIMITER) return ScriptLexemeKind.DELIMITER;
        if (type == MySqlClientScriptLexer.CUSTOM_TERMINATOR) return ScriptLexemeKind.CUSTOM_TERMINATOR;
        if (type == MySqlClientScriptLexer.CREATE) return ScriptLexemeKind.CREATE;
        if (type == MySqlClientScriptLexer.OR) return ScriptLexemeKind.OR;
        if (type == MySqlClientScriptLexer.REPLACE) return ScriptLexemeKind.REPLACE;
        if (type == MySqlClientScriptLexer.ALTER) return ScriptLexemeKind.ALTER;
        if (type == MySqlClientScriptLexer.PROCEDURE) return ScriptLexemeKind.PROCEDURE;
        if (type == MySqlClientScriptLexer.FUNCTION) return ScriptLexemeKind.FUNCTION;
        if (type == MySqlClientScriptLexer.TRIGGER) return ScriptLexemeKind.TRIGGER;
        if (type == MySqlClientScriptLexer.PACKAGE) return ScriptLexemeKind.PACKAGE;
        if (type == MySqlClientScriptLexer.BODY) return ScriptLexemeKind.BODY;
        if (type == MySqlClientScriptLexer.EVENT) return ScriptLexemeKind.EVENT;
        if (type == MySqlClientScriptLexer.VIEW) return ScriptLexemeKind.VIEW;
        if (type == MySqlClientScriptLexer.MATERIALIZED) return ScriptLexemeKind.MATERIALIZED;
        if (type == MySqlClientScriptLexer.RETURNS) return ScriptLexemeKind.RETURNS;
        if (type == MySqlClientScriptLexer.TEMPORARY) return ScriptLexemeKind.TEMPORARY;
        if (type == MySqlClientScriptLexer.TEMP) return ScriptLexemeKind.TEMP;
        if (type == MySqlClientScriptLexer.TABLE) return ScriptLexemeKind.TABLE;
        if (type == MySqlClientScriptLexer.IF) return ScriptLexemeKind.IF;
        if (type == MySqlClientScriptLexer.NOT) return ScriptLexemeKind.NOT;
        if (type == MySqlClientScriptLexer.EXISTS) return ScriptLexemeKind.EXISTS;
        return ScriptLexemeKind.SYMBOL;
    }
}
