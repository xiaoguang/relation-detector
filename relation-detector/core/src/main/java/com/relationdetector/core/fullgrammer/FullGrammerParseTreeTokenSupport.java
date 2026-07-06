package com.relationdetector.core.fullgrammer;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Token-range helpers for full-grammer parse-tree visitors.
 *
 * <p>CN: 这个 helper 只从 typed parse-tree context 中取可见 token 范围，用于位置、
 * 原文和诊断辅助；SQL/DDL 结构判断应由方言 visitor 的 typed context 完成。</p>
 *
 * <p>EN: These helpers expose visible tokens inside a typed parse-tree context
 * for source text and diagnostics. Structural SQL/DDL recognition belongs in
 * dialect visitors, not in token-range scanning.</p>
 */
final class FullGrammerParseTreeTokenSupport {
    private FullGrammerParseTreeTokenSupport() {
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

    /**
     * Detects generated grammar context presence for narrow dialect guardrails.
     *
     * <p>CN: 仅用于 full-grammer visitor 内部的受控 grammar gap 判断；不能替代正式 typed override。</p>
     */
    static boolean containsContextNamed(ParseTree tree, String simpleNameFragment) {
        if (tree == null) {
            return false;
        }
        if (tree.getClass().getSimpleName().contains(simpleNameFragment)) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsContextNamed(tree.getChild(index), simpleNameFragment)) {
                return true;
            }
        }
        return false;
    }
}
