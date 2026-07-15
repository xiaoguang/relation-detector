package com.relationdetector.core.tokenevent;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.api.Test;

import com.relationdetector.core.antlr.common.CommonRelationSqlLexer;
import com.relationdetector.core.antlr.common.CommonRelationSqlParser;
import com.relationdetector.core.parse.AntlrSllParseSupport;
import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

class AntlrSllParseSupportTest {
    @Test
    void deterministicQueryUsesSllWithoutChangingTheTypedRoot() {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        CommonRelationSqlLexer lexer = new CommonRelationSqlLexer(CharStreams.fromString(
                "SELECT o.id FROM orders o JOIN customers c ON o.customer_id = c.id;"));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        var outcome = AntlrSllParseSupport.parse(
                tokens, CommonRelationSqlParser::new, CommonRelationSqlParser::script, errors);

        assertNotNull(outcome.root());
        assertFalse(outcome.fallbackUsed());
    }

    @Test
    void parseCancellationRewindsAndRetriesInLl() {
        SyntaxErrorCounter errors = new SyntaxErrorCounter();
        CommonRelationSqlLexer lexer = new CommonRelationSqlLexer(CharStreams.fromString(
                "SELECT o.id FROM orders o;"));
        lexer.removeErrorListeners();
        lexer.addErrorListener(errors);
        CommonTokenStream tokens = new CommonTokenStream(lexer);

        AtomicInteger attempts = new AtomicInteger();
        var outcome = AntlrSllParseSupport.parse(
                tokens, CommonRelationSqlParser::new, parser -> {
                    if (attempts.incrementAndGet() == 1) {
                        throw new ParseCancellationException();
                    }
                    return parser.script();
                }, errors);

        assertNotNull(outcome.root());
        assertTrue(outcome.fallbackUsed());
        assertTrue(attempts.get() == 2, "SLL cancellation must retry exactly once in LL");
        assertTrue(errors.count() == 0, "valid SQL must stay diagnostic-free after LL retry");
    }
}
