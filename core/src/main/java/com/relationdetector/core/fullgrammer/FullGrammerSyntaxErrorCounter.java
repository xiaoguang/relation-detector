package com.relationdetector.core.fullgrammer;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public final class FullGrammerSyntaxErrorCounter extends BaseErrorListener {
    private int count;

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

    public int count() {
        return count;
    }
}
