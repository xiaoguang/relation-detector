package com.relationdetector.sqlserver.script;

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
 * CN: 识别 SQL Server 独立行 GO、引号和注释并生成 batch statement records；不解析 T-SQL 结构或事实。
 * EN: Frames SQL Server client scripts around standalone GO lines, quotes, and comments; it does not parse T-SQL
 * structure or emit physical facts.
 */
public final class SqlServerScriptFramer implements DialectScriptFramer {
    @Override
    public ScriptFrameResult frame(ScriptFrameRequest request) {
        SqlServerClientScriptLexer lexer = new SqlServerClientScriptLexer(CharStreams.fromString(request.text()));
        CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        List<ScriptLexeme> tokens = new ArrayList<>();
        for (Token token : stream.getTokens()) {
            if (token.getType() != Token.EOF) tokens.add(ScriptLexeme.from(token, kind(token.getType())));
        }
        return new StructuredScriptFramer().frame(request, tokens, ScriptDialect.SQLSERVER);
    }

    private ScriptLexemeKind kind(int type) {
        if (type == SqlServerClientScriptLexer.LINE_COMMENT || type == SqlServerClientScriptLexer.BLOCK_COMMENT) return ScriptLexemeKind.COMMENT;
        if (type == SqlServerClientScriptLexer.SINGLE_QUOTED || type == SqlServerClientScriptLexer.DOUBLE_QUOTED
                || type == SqlServerClientScriptLexer.BRACKET_QUOTED) return ScriptLexemeKind.QUOTED;
        if (type == SqlServerClientScriptLexer.SEMI) return ScriptLexemeKind.SEMICOLON;
        if (type == SqlServerClientScriptLexer.NEWLINE) return ScriptLexemeKind.NEWLINE;
        if (type == SqlServerClientScriptLexer.WS) return ScriptLexemeKind.WHITESPACE;
        if (type == SqlServerClientScriptLexer.WORD) return ScriptLexemeKind.WORD;
        if (type == SqlServerClientScriptLexer.DOT) return ScriptLexemeKind.DOT;
        if (type == SqlServerClientScriptLexer.GO) return ScriptLexemeKind.GO;
        if (type == SqlServerClientScriptLexer.CREATE) return ScriptLexemeKind.CREATE;
        if (type == SqlServerClientScriptLexer.OR) return ScriptLexemeKind.OR;
        if (type == SqlServerClientScriptLexer.REPLACE) return ScriptLexemeKind.REPLACE;
        if (type == SqlServerClientScriptLexer.ALTER) return ScriptLexemeKind.ALTER;
        if (type == SqlServerClientScriptLexer.PROCEDURE) return ScriptLexemeKind.PROCEDURE;
        if (type == SqlServerClientScriptLexer.FUNCTION) return ScriptLexemeKind.FUNCTION;
        if (type == SqlServerClientScriptLexer.TRIGGER) return ScriptLexemeKind.TRIGGER;
        if (type == SqlServerClientScriptLexer.PACKAGE) return ScriptLexemeKind.PACKAGE;
        if (type == SqlServerClientScriptLexer.BODY) return ScriptLexemeKind.BODY;
        if (type == SqlServerClientScriptLexer.EVENT) return ScriptLexemeKind.EVENT;
        if (type == SqlServerClientScriptLexer.VIEW) return ScriptLexemeKind.VIEW;
        if (type == SqlServerClientScriptLexer.MATERIALIZED) return ScriptLexemeKind.MATERIALIZED;
        if (type == SqlServerClientScriptLexer.RETURNS) return ScriptLexemeKind.RETURNS;
        if (type == SqlServerClientScriptLexer.TEMPORARY) return ScriptLexemeKind.TEMPORARY;
        if (type == SqlServerClientScriptLexer.TEMP) return ScriptLexemeKind.TEMP;
        if (type == SqlServerClientScriptLexer.TABLE) return ScriptLexemeKind.TABLE;
        if (type == SqlServerClientScriptLexer.IF) return ScriptLexemeKind.IF;
        if (type == SqlServerClientScriptLexer.NOT) return ScriptLexemeKind.NOT;
        if (type == SqlServerClientScriptLexer.EXISTS) return ScriptLexemeKind.EXISTS;
        return ScriptLexemeKind.SYMBOL;
    }
}
