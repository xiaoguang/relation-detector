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
 *
 * Shared Oracle full-grammar ANTLR lifecycle.
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
