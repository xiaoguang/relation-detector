package com.relationdetector.postgres.fullgrammar.common;

import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.fullgrammar.FullGrammarSyntaxErrorCounter;

/**
 * Shared PostgreSQL full-grammar ANTLR parse support.
 *
 * <p>CN: 封装各版本完全相同的 lexer/parser/root/error lifecycle。版本包只传入
 * generated lexer/parser 构造器和 entry rule。
 *
 * <p>EN: Encapsulates the lexer/parser/root/error lifecycle shared by all
 * PostgreSQL versions. Version packages only provide generated constructors and
 * the entry rule.
 */
public final class PostgresFullGrammarParseSupport {
    private PostgresFullGrammarParseSupport() {
    }

    public static <L extends Lexer, P extends Parser>
            AbstractPostgresFullGrammarStructuredSqlParser.FullGrammarSqlParse parseSql(
                    String sql,
                    Function<CharStream, L> lexerFactory,
                    Function<CommonTokenStream, P> parserFactory,
                    Function<P, ParserRuleContext> entryRule
            ) {
        try {
            L lexer = lexerFactory.apply(CharStreams.fromString(sql));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            P parser = parserFactory.apply(tokens);
            FullGrammarSyntaxErrorCounter errors = attachErrorCounter(lexer, parser);
            ParserRuleContext root = entryRule.apply(parser);
            List<Token> visibleTokens = tokens.getTokens().stream()
                    .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                    .toList();
            return new AbstractPostgresFullGrammarStructuredSqlParser.FullGrammarSqlParse(
                    root,
                    errors.count(),
                    visibleTokens);
        } catch (RuntimeException ex) {
            return new AbstractPostgresFullGrammarStructuredSqlParser.FullGrammarSqlParse(null, 1, List.of());
        }
    }

    public static <L extends Lexer, P extends Parser>
            AbstractPostgresFullGrammarStructuredDdlParser.FullGrammarDdlParse parseDdl(
                    String ddl,
                    Function<CharStream, L> lexerFactory,
                    Function<CommonTokenStream, P> parserFactory,
                    Function<P, ParserRuleContext> entryRule
            ) {
        try {
            L lexer = lexerFactory.apply(CharStreams.fromString(ddl));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            P parser = parserFactory.apply(tokens);
            FullGrammarSyntaxErrorCounter errors = attachErrorCounter(lexer, parser);
            ParserRuleContext root = entryRule.apply(parser);
            return new AbstractPostgresFullGrammarStructuredDdlParser.FullGrammarDdlParse(root, errors.count());
        } catch (RuntimeException ex) {
            return new AbstractPostgresFullGrammarStructuredDdlParser.FullGrammarDdlParse(null, 1);
        }
    }

    private static FullGrammarSyntaxErrorCounter attachErrorCounter(Lexer lexer, Parser parser) {
        FullGrammarSyntaxErrorCounter errors = new FullGrammarSyntaxErrorCounter();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);
        return errors;
    }
}
