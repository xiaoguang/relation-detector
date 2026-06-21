package com.relationdetector.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.WarningMessage;
import com.relationdetector.api.Enums.WarningType;
import com.relationdetector.core.antlr.RelationSqlLexer;
import com.relationdetector.core.antlr.RelationSqlParser;

/**
 * Low-level ANTLR SQL support for token-event parsers.
 *
 * <p>This class owns lexer/parser setup, visible token extraction, syntax
 * diagnostics, and dynamic SQL warning creation. It deliberately does not build
 * relationship or lineage events; token-event builders consume the returned
 * tokens and own all business extraction semantics.
 */
public final class AntlrSqlParseSupport {
    private final SqlDialect dialect;

    public AntlrSqlParseSupport(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
        RelationSqlLexer lexer = new RelationSqlLexer(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        RelationSqlParser parser = new RelationSqlParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        /*
         * Parse before inspecting tokens. The grammar is intentionally tolerant,
         * but this still collects syntax diagnostics and keeps a stable ANTLR
         * entry point for future richer visitors.
         */
        parser.script();
        tokens.fill();

        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new ParsedSql("RelationSql",
                RelationSqlLexer.class.getSimpleName(),
                RelationSqlParser.class.getSimpleName(),
                visibleTokens);
    }

    public java.util.Optional<WarningMessage> detectDynamicSql(SqlStatementRecord statement) {
        String lower = statement.sql().toLowerCase(Locale.ROOT);
        if (!lower.matches("(?s).*\\b(prepare|execute\\s+immediate|execute)\\b.*")) {
            return java.util.Optional.empty();
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("rawStatement", statement.sql());
        attributes.put("statementSourceType", statement.sourceType().name());
        attributes.put("dialect", dialect.name());
        attributes.putAll(statement.attributes());
        return java.util.Optional.of(WarningMessage.warn(WarningType.PARSE_WARNING,
                "DYNAMIC_SQL_UNRESOLVED",
                "dynamic SQL is present but cannot be statically resolved",
                statement.sourceName(),
                statement.startLine(),
                attributes));
    }

    public record ParsedSql(
            String grammarName,
            String lexerName,
            String parserName,
            List<Token> visibleTokens
    ) {
    }

    public static final class SyntaxErrorCounter extends BaseErrorListener {
        private int count;

        public int count() {
            return count;
        }

        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e
        ) {
            count++;
        }
    }
}
