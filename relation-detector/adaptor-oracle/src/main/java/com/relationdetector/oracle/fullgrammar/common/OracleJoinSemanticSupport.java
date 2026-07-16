package com.relationdetector.oracle.fullgrammar.common;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Symbol;

/**
 *
 * Stateless JOIN-kind classification over version-owned typed contexts.
 */
final class OracleJoinSemanticSupport {
    private final OracleFullGrammarParseTreeAdapter adapter;

    OracleJoinSemanticSupport(OracleFullGrammarParseTreeAdapter adapter) {
        this.adapter = adapter;
    }

    String joinKind(ParseTree tree) {
        if (contains(tree, Symbol.LEFT)) return "LEFT_JOIN";
        if (contains(tree, Symbol.RIGHT)) return "RIGHT_JOIN";
        if (contains(tree, Symbol.FULL)) return "FULL_JOIN";
        if (contains(tree, Symbol.CROSS)) return "CROSS_JOIN";
        return "JOIN";
    }

    private boolean contains(ParseTree tree, Symbol symbol) {
        if (tree == null) return false;
        if (adapter.hasSymbol(tree, symbol)) return true;
        for (ParseTree child : adapter.typedChildren(tree)) {
            if (contains(child, symbol)) return true;
        }
        return false;
    }
}
