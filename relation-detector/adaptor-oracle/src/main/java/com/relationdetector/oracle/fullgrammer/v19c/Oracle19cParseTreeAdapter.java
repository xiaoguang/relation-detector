package com.relationdetector.oracle.fullgrammer.v19c;

import com.relationdetector.oracle.fullgrammer.common.AbstractOracleFullGrammerParseTreeAdapter;
import com.relationdetector.oracle.fullgrammer.v19c.OracleFullGrammerParser.*;

final class Oracle19cParseTreeAdapter extends AbstractOracleFullGrammerParseTreeAdapter {
    Oracle19cParseTreeAdapter() {
        super(
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
                role(Role.CONCATENATION, ConcatenationContext.class));
    }
}
