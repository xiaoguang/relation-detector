package com.relationdetector.core.parser.antlr;

import java.util.List;
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

import com.relationdetector.core.antlr.common.CommonRelationSqlLexer;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;

/**
 * CN: 负责 token-event parser 的 ANTLR lexer/parser 初始化、visible tokens 与 syntax diagnostics，不生成 relationship/lineage。
 * EN: Owns ANTLR lexer/parser setup, visible tokens, and syntax diagnostics for token-event parsers without producing relationship or lineage facts.
 * It deliberately does not build relationship or lineage events; token-event
 * builders consume the returned tokens and own all business extraction semantics.
 */
public final class AntlrSqlParseSupport {
    private AntlrSqlParseSupport() {
    }

    public static ParsedSql parseAntlr(String sql, SyntaxErrorCounter errors) {
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
