package com.relationdetector.postgres.fullgrammer.common;

import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.core.fullgrammer.FullGrammerSyntaxErrorCounter;

/**
 * Shared PostgreSQL full-grammer ANTLR parse support.
 *
 * <p>CN: 封装各版本完全相同的 lexer/parser/root/error lifecycle。版本包只传入
 * generated lexer/parser 构造器和 entry rule。
 *
 * <p>EN: Encapsulates the lexer/parser/root/error lifecycle shared by all
 * PostgreSQL versions. Version packages only provide generated constructors and
 * the entry rule.
 */
public final class PostgresFullGrammerParseSupport {
    private PostgresFullGrammerParseSupport() {
    }

    public static <L extends Lexer, P extends Parser>
            AbstractPostgresFullGrammerStructuredSqlParser.FullGrammerSqlParse parseSql(
                    String sql,
                    Function<CharStream, L> lexerFactory,
                    Function<CommonTokenStream, P> parserFactory,
                    Function<P, ParserRuleContext> entryRule
            ) {
        try {
            L lexer = lexerFactory.apply(CharStreams.fromString(sql));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            P parser = parserFactory.apply(tokens);
            FullGrammerSyntaxErrorCounter errors = attachErrorCounter(lexer, parser);
            ParserRuleContext root = entryRule.apply(parser);
            List<Token> visibleTokens = tokens.getTokens().stream()
                    .filter(token -> token.getChannel() == Token.DEFAULT_CHANNEL)
                    .toList();
            return new AbstractPostgresFullGrammerStructuredSqlParser.FullGrammerSqlParse(
                    root,
                    errors.count(),
                    visibleTokens);
        } catch (RuntimeException ex) {
            return new AbstractPostgresFullGrammerStructuredSqlParser.FullGrammerSqlParse(null, 1, List.of());
        }
    }

    public static <L extends Lexer, P extends Parser>
            AbstractPostgresFullGrammerStructuredDdlParser.FullGrammerDdlParse parseDdl(
                    String ddl,
                    Function<CharStream, L> lexerFactory,
                    Function<CommonTokenStream, P> parserFactory,
                    Function<P, ParserRuleContext> entryRule
            ) {
        try {
            L lexer = lexerFactory.apply(CharStreams.fromString(ddl));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            P parser = parserFactory.apply(tokens);
            FullGrammerSyntaxErrorCounter errors = attachErrorCounter(lexer, parser);
            ParserRuleContext root = entryRule.apply(parser);
            return new AbstractPostgresFullGrammerStructuredDdlParser.FullGrammerDdlParse(root, errors.count());
        } catch (RuntimeException ex) {
            return new AbstractPostgresFullGrammerStructuredDdlParser.FullGrammerDdlParse(null, 1);
        }
    }

    private static FullGrammerSyntaxErrorCounter attachErrorCounter(Lexer lexer, Parser parser) {
        FullGrammerSyntaxErrorCounter errors = new FullGrammerSyntaxErrorCounter();
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        lexer.addErrorListener(errors);
        parser.addErrorListener(errors);
        return errors;
    }
}
