package com.relationdetector.oracle.fullgrammar.common;

import java.util.List;
import java.util.function.Function;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * CN: 创建一次 Oracle lexer/parser、安装 syntax counter 并返回 typed root；版本 binding 提供 constructors 和 entry rule，本类不缓存 parser state 或生成事实。
 * EN: Creates one Oracle lexer/parser, installs syntax counting, and returns the typed root. Version bindings supply constructors and entry rules; this lifecycle neither caches parser state nor emits facts.
 */
public final class OracleFullGrammarParseSupport {
    private OracleFullGrammarParseSupport() {
    }

    public record ParsedEvents(List<StructuredSqlEvent> events, List<Token> tokens, int syntaxErrors) {
    }

    public static <L extends Lexer, P extends Parser, R extends ParserRuleContext> ParsedEvents parse(
            SqlStatementRecord statement,
            Function<CharStream, L> lexerFactory,
            Function<CommonTokenStream, P> parserFactory,
            Function<P, R> entryRule,
            Function<R, List<StructuredSqlEvent>> eventCollector
    ) {
        return parse(statement, lexerFactory, parserFactory, entryRule, eventCollector, false);
    }

    public static <L extends Lexer, P extends Parser, R extends ParserRuleContext> ParsedEvents parse(
            SqlStatementRecord statement,
            Function<CharStream, L> lexerFactory,
            Function<CommonTokenStream, P> parserFactory,
            Function<P, R> entryRule,
            Function<R, List<StructuredSqlEvent>> eventCollector,
            boolean collectWithSyntaxErrors
    ) {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        L lexer = lexerFactory.apply(CharStreams.fromString(statement.sql()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        P parser = parserFactory.apply(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(errors);
        R root = entryRule.apply(parser);
        tokens.fill();
        List<StructuredSqlEvent> events = errors.count() == 0 || collectWithSyntaxErrors
                ? eventCollector.apply(root)
                : List.of();
        return new ParsedEvents(events, List.copyOf(tokens.getTokens()), errors.count());
    }
}
