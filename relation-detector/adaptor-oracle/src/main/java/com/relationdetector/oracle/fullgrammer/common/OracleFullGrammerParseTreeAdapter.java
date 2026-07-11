package com.relationdetector.oracle.fullgrammer.common;

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
        CONCATENATION
    }

    boolean hasRole(ParseTree tree, Role role);
}
