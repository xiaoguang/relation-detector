package com.relationdetector.postgres.routine;

import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

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

    public static PostgresRoutineParseOutcome parse(SqlStatementRecord statement) {
        RoutineSyntaxErrors errors = new RoutineSyntaxErrors();
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
        List<StructuredSqlEvent> events = new PostgresRoutineBodyParseTreeVisitor(statement).collect(root);
        int unsupported = errors.statementCount();
        int parsed = Math.max(0, root.statement().size() - unsupported);
        List<WarningMessage> warnings = new java.util.ArrayList<>();
        if (errors.count > 0) {
            warnings.add(warning(statement, "POSTGRES_ROUTINE_PARTIAL_PARSE",
                    "PostgreSQL routine body recovered after " + errors.count + " syntax error(s)",
                    statement.startLine() + Math.max(0, errors.firstLine - 1), errors.count, unsupported));
        }
        for (int index = 0; index < errors.statementCount(); index++) {
            warnings.add(warning(statement, "POSTGRES_ROUTINE_UNSUPPORTED_STATEMENT",
                    "PostgreSQL routine body contains unsupported syntax: " + errors.messageAt(index),
                    statement.startLine() + Math.max(0, errors.lines.get(index) - 1),
                    errors.count, unsupported, errors.messageAt(index)));
        }
        return new PostgresRoutineParseOutcome(events, warnings, parsed, unsupported);
    }

    public static List<StructuredSqlEvent> extract(SqlStatementRecord statement) {
        return parse(statement).events();
    }

    private static String text(SqlStatementRecord statement, String key) {
        Object value = statement.attributes().get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static WarningMessage warning(
            SqlStatementRecord statement,
            String code,
            String message,
            long line,
            int syntaxErrors,
            int unsupportedStatements
    ) {
        return warning(statement, code, message, line, syntaxErrors, unsupportedStatements, "", "");
    }

    private static WarningMessage warning(
            SqlStatementRecord statement,
            String code,
            String message,
            long line,
            int syntaxErrors,
            int unsupportedStatements,
            String syntaxError
    ) {
        return warning(statement, code, message, line, syntaxErrors, unsupportedStatements, syntaxError, "");
    }

    private static WarningMessage warning(
            SqlStatementRecord statement,
            String code,
            String message,
            long line,
            int syntaxErrors,
            int unsupportedStatements,
            String syntaxError,
            String unsupportedSql
    ) {
        return WarningMessage.warn(WarningType.PARSE_WARNING, code, message,
                statement.sourceName(), line, Map.of(
                        "sourceFile", text(statement, "sourceFile"),
                        "sourceStatementId", text(statement, "sourceStatementId"),
                        "sourceBlockId", text(statement, "sourceBlockId"),
                        "sourceObjectType", text(statement, "sourceObjectType"),
                        "sourceObjectName", text(statement, "sourceObjectName"),
                        "syntaxErrorCount", syntaxErrors,
                        "unsupportedStatementCount", unsupportedStatements,
                        "syntaxError", syntaxError,
                        "unsupportedSql", unsupportedSql));
    }

    private static final class RoutineSyntaxErrors extends BaseErrorListener {
        private int count;
        private int firstLine = 1;
        private final java.util.List<Integer> lines = new java.util.ArrayList<>();
        private final java.util.List<String> messages = new java.util.ArrayList<>();

        private int statementCount() {
            return lines.size();
        }

        private String messageAt(int index) {
            return index >= 0 && index < messages.size() ? messages.get(index) : "unknown syntax error";
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String message,
                RecognitionException exception
        ) {
            if (count == 0) {
                firstLine = Math.max(1, line);
            }
            count++;
            if (lines.isEmpty() || lines.get(lines.size() - 1) != Math.max(1, line)) {
                lines.add(Math.max(1, line));
                messages.add(message == null || message.isBlank() ? "unknown syntax error" : message);
            }
        }
    }
}
