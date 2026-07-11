package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Dialect-owned semantic view of generated ANTLR contexts.
 *
 * <p>Core expression and event semantics depend on these roles instead of
 * generated context names. A version bridge binds its concrete generated
 * context classes to the roles it supports.</p>
 */
public interface FullGrammerParseTreeAdapter {
    enum Role {
        COLUMN_REFERENCE,
        CASE_EXPRESSION,
        CASE_SWITCH_SECTION,
        CASE_SEARCH_SECTION,
        CASE_WHEN_LIST,
        CASE_WHEN,
        CASE_DEFAULT,
        AGGREGATE_FUNCTION,
        WINDOW_FUNCTION,
        CONCAT_EXPRESSION,
        FUNCTION_CALL,
        QUERY_BOUNDARY,
        SCALAR_SUBQUERY,
        SELECT_TARGET_LIST,
        SELECT_TARGET_ITEM,
        FROM_CLAUSE,
        TABLE_SOURCE_ITEM,
        EXPRESSION,
        ROOT_EXPRESSION,
        CONTROL_SCOPE
    }

    boolean hasRole(ParseTree tree, Role role);

    default ParseTree firstDescendant(ParseTree tree, Role role) {
        if (tree == null) {
            return null;
        }
        if (hasRole(tree, role)) {
            return tree;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree found = firstDescendant(tree.getChild(index), role);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    default ParseTree firstDirectChild(ParseTree tree, Role role) {
        if (tree == null) {
            return null;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (hasRole(child, role)) {
                return child;
            }
        }
        return null;
    }

    default List<ParseTree> directChildren(ParseTree tree, Role role) {
        if (tree == null) {
            return List.of();
        }
        List<ParseTree> result = new ArrayList<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (hasRole(child, role)) {
                result.add(child);
            }
        }
        return List.copyOf(result);
    }
}
