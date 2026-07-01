package com.relationdetector.postgres.fullgrammer.routine;

import java.util.List;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * PostgreSQL full-grammer local parser for PL/pgSQL routine bodies.
 *
 * <p>CN: PostgreSQL version full-grammer 已经解析外层 CREATE FUNCTION/PROCEDURE；
 * 该 helper 只负责把 dollar-quoted routine body 继续交给 full-grammer 包内的 typed
 * body grammar 和 visitor。它不 import token-event 包，也不把 token-event 事件混入
 * full-grammer。
 *
 * <p>EN: PostgreSQL version full-grammer parses the outer CREATE
 * FUNCTION/PROCEDURE statement. This helper parses the dollar-quoted routine
 * body with a full-grammer-local typed body grammar and visitor. It does not
 * import token-event packages or merge token-event events into full-grammer.
 */
public final class PostgresFullGrammerRoutineBodyParser {
    private PostgresFullGrammerRoutineBodyParser() {
    }

    public static List<StructuredSqlEvent> extract(SqlStatementRecord statement) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        PostgresFullGrammerRoutineBodySqlLexer lexer =
                new PostgresFullGrammerRoutineBodySqlLexer(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PostgresFullGrammerRoutineBodySqlParser parser = new PostgresFullGrammerRoutineBodySqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        PostgresFullGrammerRoutineBodySqlParser.ScriptContext root = parser.script();
        tokens.fill();
        if (errors.count() > 0) {
            return List.of();
        }
        return new PostgresFullGrammerRoutineBodyParseTreeVisitor(statement).collect(root);
    }
}
