package com.relationdetector.oracle.fullgrammer.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/** Version-owned typed classification of generated Oracle grammar contexts. */
public interface OracleFullGrammerParseTreeAdapter {
    enum Role {
        ROUTINE_BODY,
        CTE,
        CREATE_TABLE,
        ALTER_TABLE,
        COLUMN_DEFINITION,
        OUT_OF_LINE_CONSTRAINT,
        FOREIGN_KEY,
        CREATE_INDEX,
        SELECT_STATEMENT,
        QUERY_BLOCK,
        TABLE_REF_AUX,
        TABLE_REF_INTERNAL,
        GENERAL_TABLE_REF,
        SELECTED_TABLEVIEW,
        JOIN_CLAUSE,
        JOIN_ON,
        JOIN_USING,
        WHERE_CLAUSE,
        GROUP_BY_ELEMENT,
        HAVING_CLAUSE,
        RELATIONAL_EXPRESSION,
        COMPOUND_EXPRESSION,
        QUANTIFIED_EXPRESSION,
        UPDATE_STATEMENT,
        UPDATE_SET_CLAUSE,
        SINGLE_TABLE_INSERT,
        MERGE_STATEMENT,
        SUBQUERY,
        COLUMN_REFERENCE,
        GENERAL_ELEMENT,
        CASE_EXPRESSION,
        FUNCTION_EXPRESSION,
        CONCATENATION,
        DML_TABLE_EXPRESSION,
        TABLEVIEW_NAME,
        TABLE_ALIAS,
        COLUMN_ALIAS,
        PAREN_COLUMN_LIST,
        COLUMN_LIST,
        COLUMN_NAME,
        REFERENCES_CLAUSE,
        TABLE_INDEX_CLAUSE,
        INDEX_EXPRESSION,
        INLINE_CONSTRAINT,
        SCHEMA_NAME,
        IDENTIFIER,
        QUOTED_STRING,
        SELECT_LIST_ELEMENT,
        SELECTED_LIST,
        EXPRESSION,
        SIMPLE_CASE_EXPRESSION,
        SEARCHED_CASE_EXPRESSION,
        CASE_WHEN_PART,
        CASE_ELSE_PART,
        TABLE_REF_INTERNAL_WRAPPER,
        GENERAL_ELEMENT_PART,
        FUNCTION_ARGUMENT,
        QUERY_NAME,
        FROM_CLAUSE,
        HIERARCHICAL_QUERY_CLAUSE,
        GROUP_BY_CLAUSE,
        MODEL_CLAUSE,
        RELATIONAL_OPERATOR,
        IN_ELEMENTS,
        SELECT_ONLY_STATEMENT,
        INSERT_INTO_CLAUSE,
        MERGE_UPDATE_CLAUSE,
        MERGE_ELEMENT,
        CONDITION,
        TABLE_NAME,
        FOREIGN_KEY_CLAUSE
    }

    enum Symbol {
        PRIMARY,
        UNIQUE,
        IN,
        NOT,
        EXISTS,
        LEFT,
        RIGHT,
        FULL,
        CROSS,
        EQUAL,
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        CONCAT
    }

    enum OperatorSemantic { NONE, ARITHMETIC, CONCAT_FORMAT }

    boolean hasRole(ParseTree tree, Role role);

    boolean hasSymbol(ParseTree tree, Symbol symbol);

    /** True only for a standalone typed '=' operator, never for <=, >=, !=, or <>. */
    boolean isDirectEquality(ParseTree tree);

    Optional<String> functionName(ParseTree tree);

    OperatorSemantic operatorSemantic(ParseTree tree);

    /** Generated parser-rule children only; terminal text never enters common semantics. */
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
}
