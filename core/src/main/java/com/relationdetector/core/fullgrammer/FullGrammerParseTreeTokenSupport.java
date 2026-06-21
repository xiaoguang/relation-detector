package com.relationdetector.core.fullgrammer;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

final class FullGrammerParseTreeTokenSupport {
    private FullGrammerParseTreeTokenSupport() {
    }

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
