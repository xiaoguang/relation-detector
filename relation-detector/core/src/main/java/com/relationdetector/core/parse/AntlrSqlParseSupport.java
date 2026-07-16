package com.relationdetector.core.parse;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.Enums.WarningType;
import com.relationdetector.core.antlr.common.CommonRelationSqlLexer;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;

/**
 *
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
        return parseAntlr(
                sql,
                errors,
                "CommonRelationSql",
                CommonRelationSqlLexer.class.getSimpleName(),
                CommonRelationSqlParser.class.getSimpleName(),
                CommonRelationSqlLexer::new,
                CommonRelationSqlParser::new,
                CommonRelationSqlParser::script);
    }

    public static <L extends Lexer, P extends Parser> ParsedSql parseAntlr(
            String sql,
            SyntaxErrorCounter errors,
            String grammarName,
            String lexerName,
            String parserName,
            Function<CharStream, L> lexerFactory,
            Function<CommonTokenStream, P> parserFactory,
            Consumer<P> entryRule
    ) {
        L lexer = lexerFactory.apply(CharStreams.fromString(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        P parser = parserFactory.apply(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);

        /*
         * Parse before inspecting tokens. The grammar is intentionally tolerant,
         * but this still collects syntax diagnostics and keeps a stable ANTLR
         * entry point for future richer visitors.
         */
        entryRule.accept(parser);
        tokens.fill();

        List<Token> visibleTokens = tokens.getTokens().stream()
                .filter(token -> token.getType() != Token.EOF)
                .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                .toList();
        return new ParsedSql(grammarName, lexerName, parserName, visibleTokens);
    }

    public java.util.Optional<WarningMessage> detectDynamicSql(SqlStatementRecord statement) {
        if (!containsDynamicSql(statement.sql())) {
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

    private boolean containsDynamicSql(String sql) {
        List<String> words = sqlWords(sql);
        for (int i = 0; i < words.size(); i++) {
            String word = words.get(i);
            if ("prepare".equals(word)) {
                return true;
            }
            if (!"execute".equals(word)) {
                continue;
            }
            String next = i + 1 < words.size() ? words.get(i + 1) : "";
            if ("function".equals(next) || "procedure".equals(next)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private List<String> sqlWords(String sql) {
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '_') {
                current.append(Character.toLowerCase(ch));
                continue;
            }
            if (!current.isEmpty()) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
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
