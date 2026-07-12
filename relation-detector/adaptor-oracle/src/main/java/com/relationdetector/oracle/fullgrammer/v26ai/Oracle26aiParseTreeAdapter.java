package com.relationdetector.oracle.fullgrammer.v26ai;

import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammer.common.AbstractOracleFullGrammerParseTreeAdapter;
import com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerParser;
import com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerParser.*;

final class Oracle26aiParseTreeAdapter extends AbstractOracleFullGrammerParseTreeAdapter {
    Oracle26aiParseTreeAdapter() {
        super(Map.of(
                        Symbol.PRIMARY, OracleFullGrammerParser.PRIMARY,
                        Symbol.UNIQUE, OracleFullGrammerParser.UNIQUE,
                        Symbol.IN, OracleFullGrammerParser.IN,
                        Symbol.NOT, OracleFullGrammerParser.NOT,
                        Symbol.EXISTS, OracleFullGrammerParser.EXISTS,
                        Symbol.LEFT, OracleFullGrammerParser.LEFT,
                        Symbol.RIGHT, OracleFullGrammerParser.RIGHT,
                        Symbol.FULL, OracleFullGrammerParser.FULL,
                        Symbol.CROSS, OracleFullGrammerParser.CROSS,
                        Symbol.EQUAL, OracleFullGrammerParser.EQUALS_OP),
                role(Role.ROUTINE_BODY, Create_procedure_bodyContext.class, Create_function_bodyContext.class,
                        Create_triggerContext.class),
                role(Role.CTE, Subquery_factoring_clauseContext.class),
                role(Role.CREATE_TABLE, Create_tableContext.class), role(Role.ALTER_TABLE, Alter_tableContext.class),
                role(Role.COLUMN_DEFINITION, Column_definitionContext.class, Virtual_column_definitionContext.class),
                role(Role.OUT_OF_LINE_CONSTRAINT, Out_of_line_constraintContext.class),
                role(Role.FOREIGN_KEY, Foreign_key_clauseContext.class), role(Role.CREATE_INDEX, Create_indexContext.class),
                role(Role.SELECT_STATEMENT, Select_statementContext.class),
                role(Role.QUERY_BLOCK, Query_blockContext.class), role(Role.TABLE_REF_AUX, Table_ref_auxContext.class),
                role(Role.TABLE_REF_INTERNAL, Table_ref_aux_internal_oneContext.class, Table_ref_aux_internal_threContext.class),
                role(Role.GENERAL_TABLE_REF, General_table_refContext.class),
                role(Role.SELECTED_TABLEVIEW, Selected_tableviewContext.class), role(Role.JOIN_CLAUSE, Join_clauseContext.class),
                role(Role.JOIN_ON, Join_on_partContext.class), role(Role.JOIN_USING, Join_using_partContext.class),
                role(Role.WHERE_CLAUSE, Where_clauseContext.class), role(Role.GROUP_BY_ELEMENT, Group_by_elementsContext.class),
                role(Role.HAVING_CLAUSE, Having_clauseContext.class),
                role(Role.RELATIONAL_EXPRESSION, Relational_expressionContext.class),
                role(Role.COMPOUND_EXPRESSION, Compound_expressionContext.class),
                role(Role.QUANTIFIED_EXPRESSION, Quantified_expressionContext.class),
                role(Role.UPDATE_STATEMENT, Update_statementContext.class),
                role(Role.UPDATE_SET_CLAUSE, Column_based_update_set_clauseContext.class),
                role(Role.SINGLE_TABLE_INSERT, Single_table_insertContext.class),
                role(Role.MERGE_STATEMENT, Merge_statementContext.class), role(Role.SUBQUERY, SubqueryContext.class),
                role(Role.COLUMN_REFERENCE, Column_nameContext.class, Table_elementContext.class),
                role(Role.GENERAL_ELEMENT, General_elementContext.class), role(Role.CASE_EXPRESSION, Case_expressionContext.class),
                role(Role.FUNCTION_EXPRESSION, String_functionContext.class, Standard_functionContext.class,
                        Json_functionContext.class, Numeric_function_wrapperContext.class,
                        Numeric_functionContext.class, Other_functionContext.class),
                role(Role.CONCATENATION, ConcatenationContext.class),
                role(Role.DML_TABLE_EXPRESSION, Dml_table_expression_clauseContext.class),
                role(Role.TABLEVIEW_NAME, Tableview_nameContext.class), role(Role.TABLE_ALIAS, Table_aliasContext.class),
                role(Role.COLUMN_ALIAS, Column_aliasContext.class),
                role(Role.PAREN_COLUMN_LIST, Paren_column_listContext.class), role(Role.COLUMN_LIST, Column_listContext.class),
                role(Role.COLUMN_NAME, Column_nameContext.class), role(Role.REFERENCES_CLAUSE, References_clauseContext.class),
                role(Role.TABLE_INDEX_CLAUSE, Table_index_clauseContext.class), role(Role.INDEX_EXPRESSION, Index_exprContext.class),
                role(Role.INLINE_CONSTRAINT, Inline_constraintContext.class), role(Role.SCHEMA_NAME, Schema_nameContext.class),
                role(Role.IDENTIFIER, IdentifierContext.class), role(Role.QUOTED_STRING, Quoted_stringContext.class),
                role(Role.SELECT_LIST_ELEMENT, Select_list_elementsContext.class), role(Role.SELECTED_LIST, Selected_listContext.class),
                role(Role.EXPRESSION, ExpressionContext.class), role(Role.SIMPLE_CASE_EXPRESSION, Simple_case_expressionContext.class),
                role(Role.SEARCHED_CASE_EXPRESSION, Searched_case_expressionContext.class),
                role(Role.CASE_WHEN_PART, Case_when_part_expressionContext.class), role(Role.CASE_ELSE_PART, Case_else_part_expressionContext.class),
                role(Role.TABLE_REF_INTERNAL_WRAPPER, Table_ref_aux_internalContext.class),
                role(Role.GENERAL_ELEMENT_PART, General_element_partContext.class), role(Role.FUNCTION_ARGUMENT, Function_argumentContext.class),
                role(Role.QUERY_NAME, Query_nameContext.class), role(Role.FROM_CLAUSE, From_clauseContext.class),
                role(Role.HIERARCHICAL_QUERY_CLAUSE, Hierarchical_query_clauseContext.class), role(Role.GROUP_BY_CLAUSE, Group_by_clauseContext.class),
                role(Role.MODEL_CLAUSE, Model_clauseContext.class), role(Role.RELATIONAL_OPERATOR, Relational_operatorContext.class),
                role(Role.IN_ELEMENTS, In_elementsContext.class), role(Role.SELECT_ONLY_STATEMENT, Select_only_statementContext.class),
                role(Role.INSERT_INTO_CLAUSE, Insert_into_clauseContext.class), role(Role.MERGE_UPDATE_CLAUSE, Merge_update_clauseContext.class),
                role(Role.MERGE_ELEMENT, Merge_elementContext.class), role(Role.CONDITION, ConditionContext.class),
                role(Role.TABLE_NAME, Table_nameContext.class), role(Role.FOREIGN_KEY_CLAUSE, Foreign_key_clauseContext.class));
    }

    @Override
    public OperatorSemantic operatorSemantic(ParseTree tree) {
        if (tree instanceof Model_expressionContext expression && expression.MINUS_SIGN() != null) {
            return OperatorSemantic.ARITHMETIC;
        }
        if (tree instanceof ConcatenationContext expression) {
            if (expression.BAR().size() >= 2) return OperatorSemantic.CONCAT_FORMAT;
            if (expression.op != null) return OperatorSemantic.ARITHMETIC;
        }
        return OperatorSemantic.NONE;
    }

    @Override
    public boolean isDirectEquality(ParseTree tree) {
        if (!(tree instanceof Relational_operatorContext operator)) return false;
        return operator.EQUALS_OP() != null
                && operator.NOT_EQUAL_OP() == null
                && operator.LESS_THAN_OP() == null
                && operator.GREATER_THAN_OP() == null
                && operator.EXCLAMATION_OPERATOR_PART() == null
                && operator.CARRET_OPERATOR_PART() == null;
    }
}
