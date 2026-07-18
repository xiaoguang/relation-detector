package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.core.fullgrammar.FullGrammarSyntaxErrorCounter;
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
 * CN: 创建一次 MySQL lexer/parser、安装 syntax counter 并返回 root 与 visible tokens；版本包提供构造器和 entry rule，本类不缓存 parser state 或解释语义。
 * EN: Creates one MySQL lexer/parser, attaches syntax counting, and returns the root plus visible tokens. Version packages supply constructors and entry rules; this support neither caches parser state nor interprets semantics.
 *
 * <p>Version packages provide generated lexer/parser constructors and entry
 * rules; this support class owns error listener setup and visible-token extraction.
 */
public final class MySqlFullGrammarParseSupport {
    private MySqlFullGrammarParseSupport() {
    }

    public static <L extends Lexer, P extends Parser> FullGrammarSqlParse parseSql(
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
            return new FullGrammarSqlParse(root, errors.count(), visibleTokens);
        } catch (RuntimeException ex) {
            return new FullGrammarSqlParse(null, 1, List.of());
        }
    }

    public static <L extends Lexer, P extends Parser> FullGrammarDdlParse parseDdl(
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
            return new FullGrammarDdlParse(root, errors.count());
        } catch (RuntimeException ex) {
            return new FullGrammarDdlParse(null, 1);
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

    public record FullGrammarSqlParse(ParserRuleContext root, int syntaxErrors, List<Token> visibleTokens) {
    }

    public record FullGrammarDdlParse(ParserRuleContext root, int syntaxErrors) {
    }
}
