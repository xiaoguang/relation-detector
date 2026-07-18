package com.relationdetector.core.parse;

import java.util.function.Function;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.parse.AntlrSqlParseSupport.SyntaxErrorCounter;

/**
 * CN: 对 deterministic token-event grammar 先使用 SLL，仅在 cancellation 或 lexer error 后以同一 token stream 安全重试 LL。
 * EN: Runs deterministic token-event grammars in SLL first and safely retries the same token stream in LL when required.
 */
public final class AntlrSllParseSupport {
    private AntlrSllParseSupport() {
    }

    public static <P extends Parser, R extends ParseTree> ParseOutcome<R> parse(
            CommonTokenStream tokens,
            Function<CommonTokenStream, P> parserFactory,
            Function<P, R> entryRule,
            SyntaxErrorCounter errors
    ) {
        tokens.fill();
        tokens.seek(0);
        if (errors.count() == 0) {
            P sllParser = parserFactory.apply(tokens);
            sllParser.removeErrorListeners();
            sllParser.setErrorHandler(new BailErrorStrategy());
            sllParser.getInterpreter().setPredictionMode(PredictionMode.SLL);
            try {
                return new ParseOutcome<>(entryRule.apply(sllParser), false);
            } catch (ParseCancellationException ignored) {
                tokens.seek(0);
            }
        }

        P llParser = parserFactory.apply(tokens);
        llParser.removeErrorListeners();
        llParser.addErrorListener(errors);
        llParser.getInterpreter().setPredictionMode(PredictionMode.LL);
        return new ParseOutcome<>(entryRule.apply(llParser), true);
    }

    public record ParseOutcome<R extends ParseTree>(R root, boolean fallbackUsed) {
    }
}
