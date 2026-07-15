package com.relationdetector.postgres.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;

/** Locates PostgreSQL scalar and direct-scope typed contexts. */
final class PostgresExpressionTreeSupport {
    private final FullGrammarParseTreeAdapter adapter;

    PostgresExpressionTreeSupport(FullGrammarParseTreeAdapter adapter) {
        this.adapter = adapter;
    }

    ParseTree scalarSubquery(ParseTree tree) {
        if (tree == null) return null;
        if (isScalarBoundary(tree)) return tree;
        for (ParseTree child : adapter.typedChildren(tree)) {
            ParseTree found = scalarSubquery(child);
            if (found != null) return found;
        }
        return null;
    }

    boolean isScalarBoundary(ParseTree tree) {
        return adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY);
    }

    void collectDirectScopeContexts(ParseTree root, ParseTree tree, List<ParseTree> result) {
        collectDirectRoleContexts(root, tree, FullGrammarParseTreeAdapter.Role.CONTROL_SCOPE, result);
    }

    void collectDirectRoleContexts(
            ParseTree root,
            ParseTree tree,
            FullGrammarParseTreeAdapter.Role role,
            List<ParseTree> result
    ) {
        if (tree == null) return;
        if (tree != root && isScalarBoundary(tree)) return;
        if (adapter.hasRole(tree, role)) {
            result.add(tree);
            return;
        }
        for (ParseTree child : adapter.typedChildren(tree)) {
            collectDirectRoleContexts(root, child, role, result);
        }
    }
}
