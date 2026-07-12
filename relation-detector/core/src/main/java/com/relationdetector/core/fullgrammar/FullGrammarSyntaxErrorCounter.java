package com.relationdetector.core.fullgrammar;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Counts syntax errors reported by a full-grammar generated parser.
 *
 * <p>CN: 该 listener 只统计 full-grammar 解析诊断，用于 warning/attributes；
 * 它不决定是否输出关系或血缘。</p>
 *
 * <p>EN: This listener only counts generated-parser diagnostics for warnings
 * and attributes. It does not decide relationship or lineage output.</p>
 */
public final class FullGrammarSyntaxErrorCounter extends BaseErrorListener {
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
