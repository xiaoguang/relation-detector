package com.relationdetector.core.fullgrammar;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

/**
 * Token-range helpers for full-grammar parse-tree visitors.
 *
 * <p>CN: 这个 helper 只从 typed parse-tree context 中取可见 token 范围，用于位置、
 * 原文和诊断辅助；SQL/DDL 结构判断应由方言 visitor 的 typed context 完成。</p>
 *
 * <p>EN: These helpers expose visible tokens inside a typed parse-tree context
 * for source text and diagnostics. Structural SQL/DDL recognition belongs in
 * dialect visitors, not in token-range scanning.</p>
 */
final class FullGrammarParseTreeTokenSupport {
    private FullGrammarParseTreeTokenSupport() {
    }

    /**
     * Returns visible tokens covered by a parser rule context.
     *
     * <p>CN: 用于读取 context 原文片段；调用方不能把它当作顶层 SQL scanner。</p>
     */
    static List<Token> visibleTokensIn(ParserRuleContext context, List<Token> visibleTokens) {
        if (context == null || context.getStart() == null || context.getStop() == null) {
            return visibleTokens;
        }
        int start = context.getStart().getTokenIndex();
        int stop = context.getStop().getTokenIndex();
        return visibleTokens.stream()
                .filter(token -> token.getTokenIndex() >= start && token.getTokenIndex() <= stop)
                .toList();
    }

}
