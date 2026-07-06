package com.relationdetector.postgres.routine;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * PostgreSQL dialect-level parser for PL/pgSQL routine bodies.
 *
 * <p>CN: 外层 token-event 或 full-grammer parser 负责识别 CREATE
 * FUNCTION/PROCEDURE 并抽出 dollar-quoted body；该 helper 只解析过程体内的
 * SQL/DML 结构事件。
 *
 * <p>EN: The outer token-event or full-grammer parser identifies the CREATE
 * FUNCTION/PROCEDURE statement and extracts the dollar-quoted body. This helper
 * parses the body with a dialect-level typed grammar and visitor.
 */
public final class PostgresRoutineBodyParser {
    private PostgresRoutineBodyParser() {
    }

    public static List<StructuredSqlEvent> extract(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        PostgresRoutineBodySqlLexer lexer =
                new PostgresRoutineBodySqlLexer(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresRoutineBodySqlParser parser = new PostgresRoutineBodySqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        PostgresRoutineBodySqlParser.ScriptContext root = parser.script();
        tokens.fill();
        if (errors.count() > 0) {
            return List.of();
        }
        return new PostgresRoutineBodyParseTreeVisitor(statement).collect(root);
    }
}
