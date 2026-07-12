package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.core.fullgrammer.FullGrammerSyntaxErrorCounter;
import java.util.List;
import java.util.function.Function;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Shared MySQL full-grammer ANTLR parse lifecycle.
 *
 * <p>Version packages provide generated lexer/parser constructors and entry
 * rules; this support class owns error listener setup and visible-token extraction.
 */
public final class MySqlFullGrammerParseSupport {
    private MySqlFullGrammerParseSupport() {
    }

    public static <L extends Lexer, P extends Parser> FullGrammerSqlParse parseSql(
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
            return new FullGrammerSqlParse(root, errors.count(), visibleTokens);
        } catch (RuntimeException ex) {
            return new FullGrammerSqlParse(null, 1, List.of());
        }
    }

    public static <L extends Lexer, P extends Parser> FullGrammerDdlParse parseDdl(
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
            return new FullGrammerDdlParse(root, errors.count());
        } catch (RuntimeException ex) {
            return new FullGrammerDdlParse(null, 1);
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

    public record FullGrammerSqlParse(ParserRuleContext root, int syntaxErrors, List<Token> visibleTokens) {
    }

    public record FullGrammerDdlParse(ParserRuleContext root, int syntaxErrors) {
    }
}
