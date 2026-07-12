package com.relationdetector.postgres.routine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.spi.AdaptorContext;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;

public final class GeneratedPlPgSqlBodyParserSupport {
    private GeneratedPlPgSqlBodyParserSupport() {
    }

    public static <P extends Parser, R extends ParserRuleContext> PlPgSqlParseOutcome parse(
            SqlStatementRecord statement,
            AdaptorContext context,
            StructuredSqlParser embeddedSqlParser,
            Function<CharStream, ? extends Lexer> lexerFactory,
            Function<CommonTokenStream, P> parserFactory,
            Function<P, R> rootParser,
            Function<R, PlPgSqlBodyStructure> structureCollector
    ) {
        SyntaxErrors errors = new SyntaxErrors();
        Lexer lexer = lexerFactory.apply(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        P parser = parserFactory.apply(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        R root = rootParser.apply(parser);
        tokens.fill();
        PlPgSqlBodyStructure structure = structureCollector.apply(root);
        List<StructuredSqlEvent> events = new ArrayList<>();
        List<WarningMessage> warnings = new ArrayList<>();
        if (errors.count > 0) {
            warnings.add(WarningMessage.warn(WarningType.PARSE_WARNING,
                    "POSTGRES_ROUTINE_PARTIAL_PARSE",
                    "PostgreSQL routine body recovered after " + errors.count + " syntax error(s)",
                    statement.sourceName(),
                    statement.startLine() + Math.max(0, errors.firstLine - 1),
                    Map.of("sourceFile", attribute(statement, "sourceFile"),
                            "sourceStatementId", attribute(statement, "sourceStatementId"),
                            "sourceBlockId", attribute(statement, "sourceBlockId"),
                            "sourceObjectType", attribute(statement, "sourceObjectType"),
                            "sourceObjectName", attribute(statement, "sourceObjectName"),
                            "syntaxErrorCount", errors.count)));
        }
        if (embeddedSqlParser == null && !structure.staticStatements().isEmpty()) {
            warnings.add(WarningMessage.warn(WarningType.PARSE_WARNING,
                    "POSTGRES_ROUTINE_EMBEDDED_SQL_PARSER_UNAVAILABLE",
                    "PostgreSQL routine contains static SQL but no embedded SQL parser is available",
                    statement.sourceName(), statement.startLine(), provenance(statement)));
        } else if (embeddedSqlParser != null) {
            for (PlPgSqlBodyStructure.StaticSqlStatement fragment : structure.staticStatements()) {
                SqlStatementRecord embedded = embeddedStatement(
                        statement, fragment, structure.localIdentifiers());
                var result = embeddedSqlParser.parseSql(embedded, context);
                events.addAll(result.events());
                warnings.addAll(result.warnings());
            }
        }
        for (int line : structure.dynamicSqlLines()) {
            warnings.add(WarningMessage.warn(WarningType.PARSE_WARNING,
                    "POSTGRES_DYNAMIC_SQL_UNRESOLVED",
                    "PostgreSQL dynamic EXECUTE is not resolved into physical SQL facts",
                    statement.sourceName(), statement.startLine() + Math.max(0, line - 1),
                    provenance(statement)));
        }
        for (int line : structure.unsupportedLines()) {
            warnings.add(WarningMessage.warn(WarningType.PARSE_WARNING,
                    "POSTGRES_ROUTINE_UNSUPPORTED_STATEMENT",
                    "PostgreSQL routine body contains an unsupported statement",
                    statement.sourceName(), statement.startLine() + Math.max(0, line - 1),
                    Map.of("sourceFile", attribute(statement, "sourceFile"),
                            "sourceStatementId", attribute(statement, "sourceStatementId"),
                            "sourceBlockId", attribute(statement, "sourceBlockId"),
                            "sourceObjectType", attribute(statement, "sourceObjectType"),
                            "sourceObjectName", attribute(statement, "sourceObjectName"))));
        }
        int unsupported = Math.max(errors.lines.size(),
                structure.unsupportedLines().size() + structure.dynamicSqlLines().size());
        if (embeddedSqlParser == null) unsupported += structure.staticStatements().size();
        return new PlPgSqlParseOutcome(events, warnings,
                Math.max(0, structure.statementCount() - unsupported), unsupported);
    }

    private static SqlStatementRecord embeddedStatement(
            SqlStatementRecord body,
            PlPgSqlBodyStructure.StaticSqlStatement fragment,
            java.util.Set<String> localIdentifiers
    ) {
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(body.attributes());
        long start = body.startLine() + fragment.startLine() - 1L;
        long end = body.startLine() + fragment.endLine() - 1L;
        attributes.put("sourceLine", start);
        attributes.put(PostgresRoutineAttributes.EMBEDDED_SQL, true);
        attributes.put(PostgresRoutineAttributes.NON_COLUMN_IDENTIFIERS,
                PostgresRoutineAttributes.merge(attributes, localIdentifiers));
        return new SqlStatementRecord(fragment.sql(), body.sourceType(), body.sourceName(), start, end, attributes);
    }

    private static Map<String, Object> provenance(SqlStatementRecord statement) {
        return Map.of("sourceFile", attribute(statement, "sourceFile"),
                "sourceStatementId", attribute(statement, "sourceStatementId"),
                "sourceBlockId", attribute(statement, "sourceBlockId"),
                "sourceObjectType", attribute(statement, "sourceObjectType"),
                "sourceObjectName", attribute(statement, "sourceObjectName"));
    }

    private static String attribute(SqlStatementRecord statement, String name) {
        Object value = statement.attributes().get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private static final class SyntaxErrors extends BaseErrorListener {
        private int count;
        private int firstLine = 1;
        private final List<Integer> lines = new ArrayList<>();

        @Override
        public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                int charPositionInLine, String message, RecognitionException exception) {
            if (count++ == 0) firstLine = Math.max(1, line);
            int normalized = Math.max(1, line);
            if (lines.isEmpty() || lines.get(lines.size() - 1) != normalized) lines.add(normalized);
        }
    }
}
