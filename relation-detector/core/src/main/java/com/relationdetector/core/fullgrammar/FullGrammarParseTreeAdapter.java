package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 *
 * Dialect-owned semantic view of generated ANTLR contexts.
 *
 * <p>Core expression and event semantics depend on these roles instead of
 * generated context names. A version bridge binds its concrete generated
 * context classes to the roles it supports.</p>
 */
public interface FullGrammarParseTreeAdapter {
    enum OperatorSemantic {
        NONE,
        ARITHMETIC,
        CONCAT_FORMAT,
        CUMULATIVE,
        BOOLEAN_EXPRESSION
    }

    enum DdlConstraintSemantic {
        NONE,
        PRIMARY_KEY,
        UNIQUE,
        FOREIGN_KEY
    }

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
        CONTROL_SCOPE,
        GROUPING_SCOPE,
        WINDOW_CONTROL_SCOPE,
        SQL_CLAUSE,
        COMMON_TABLE_EXPRESSION,
        QUERY_SPECIFICATION,
        JOIN_ON,
        CROSS_JOIN,
        APPLY,
        PREDICATE,
        INSERT_STATEMENT,
        UPDATE_STATEMENT,
        MERGE_STATEMENT,
        DML_TRIGGER,
        CREATE_TABLE,
        ALTER_TABLE,
        CREATE_INDEX,
        FULL_TABLE_NAME,
        TABLE_ALIAS,
        COLUMN_ALIAS,
        IDENTIFIER,
        COMPARISON_OPERATOR,
        SELECT_STATEMENT,
        SELECT_LIST,
        TABLE_SOURCES,
        SEARCH_CONDITION,
        DERIVED_TABLE,
        ROWSET_FUNCTION,
        OPEN_XML,
        OPEN_JSON,
        CHANGE_TABLE,
        NODES_METHOD,
        TABLE_SOURCE,
        DDL_OBJECT,
        INSERT_COLUMN_LIST,
        INSERT_VALUE,
        WITH_EXPRESSION,
        UPDATE_ELEMENT,
        COLUMN_LIST,
        ORDERED_COLUMN_LIST,
        EXPRESSION_LIST,
        MERGE_UPDATE_ELEMENT,
        MERGE_NOT_MATCHED,
        TABLE_NAME,
        COLUMN_DEFINITION,
        TABLE_CONSTRAINT,
        FOREIGN_KEY_OPTIONS
    }

    boolean hasRole(ParseTree tree, Role role);

    /** Exact physical column represented by this generated context, if any. */
    default Optional<FullGrammarColumnReference> directColumn(ParseTree tree) {
        return Optional.empty();
    }

    /** Exact identifiers exposed by a typed identifier/list/alias context. */
    default List<String> identifiers(ParseTree tree) {
        return List.of();
    }

    /** Function symbol exposed by a typed function-call context. */
    default Optional<String> functionName(ParseTree tree) {
        return Optional.empty();
    }

    /** Typed argument expressions owned by a function-call context. */
    default List<ParseTree> functionArgumentExpressions(ParseTree tree) {
        return List.of();
    }

    /** Operator semantics exposed by the current generated expression context. */
    default OperatorSemantic operatorSemantic(ParseTree tree) {
        return OperatorSemantic.NONE;
    }

    /** True for a typed literal, parameter, or local-variable context. */
    default boolean isNonColumnValue(ParseTree tree) {
        return false;
    }

    /** Exact text value exposed by a typed literal context, if any. */
    default Optional<String> literalValue(ParseTree tree) {
        return Optional.empty();
    }

    /** VALUE and CONTROL branches of a typed CASE/IF expression. */
    default CaseParts caseParts(ParseTree tree) {
        return CaseParts.NONE;
    }

    /** Direct typed equality represented by this context; nested equalities are visited by core. */
    default List<EqualityOperands> directEqualities(ParseTree tree) {
        return List.of();
    }

    /** Typed SELECT projection expressions owned by this query boundary. */
    default List<ParseTree> selectProjectionExpressions(ParseTree queryBoundary) {
        return List.of();
    }

    /** Physical table and visible qualifier represented by a typed rowset context. */
    default Optional<RowsetBinding> rowsetBinding(ParseTree tree) {
        return Optional.empty();
    }

    /** Predicate kind exposed by a generated predicate context. */
    default boolean isExistsPredicate(ParseTree tree) {
        return false;
    }

    /** Predicate kind exposed by a generated predicate context. */
    default boolean isInPredicate(ParseTree tree) {
        return false;
    }

    /** Constraint/index kind exposed by a generated DDL context. */
    default DdlConstraintSemantic ddlConstraintSemantic(ParseTree tree) {
        return DdlConstraintSemantic.NONE;
    }

    /** Generated parser-rule children only; terminal leaves never enter core semantics. */
    default List<ParseTree> typedChildren(ParseTree tree) {
        if (tree == null) {
            return List.of();
        }
        List<ParseTree> result = new ArrayList<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (tree.getChild(index) instanceof ParserRuleContext child) {
                result.add(child);
            }
        }
        return List.copyOf(result);
    }

    default ParseTree firstDescendant(ParseTree tree, Role role) {
        if (tree == null) {
            return null;
        }
        if (hasRole(tree, role)) {
            return tree;
        }
        for (ParseTree child : typedChildren(tree)) {
            ParseTree found = firstDescendant(child, role);
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
        for (ParseTree child : typedChildren(tree)) {
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
        for (ParseTree child : typedChildren(tree)) {
            if (hasRole(child, role)) {
                result.add(child);
            }
        }
        return List.copyOf(result);
    }

    record CaseParts(boolean conditional, List<ParseTree> values, List<ParseTree> controls) {
        public static final CaseParts NONE = new CaseParts(false, List.of(), List.of());

        public CaseParts {
            values = List.copyOf(values);
            controls = List.copyOf(controls);
        }
    }

    record EqualityOperands(ParseTree left, ParseTree right) {
    }

    record RowsetBinding(String table, String qualifier) {
        public RowsetBinding {
            table = table == null ? "" : table;
            qualifier = qualifier == null ? "" : qualifier;
        }
    }
}
