package com.relationdetector.postgres.fullgrammer.v17;

import com.relationdetector.core.fullgrammer.AbstractFullGrammerParseTreeAdapter;
import com.relationdetector.postgres.fullgrammer.v17.Postgres17FullGrammerParser.*;

final class Postgres17ParseTreeAdapter extends AbstractFullGrammerParseTreeAdapter {
    Postgres17ParseTreeAdapter() {
        super(
                role(Role.COLUMN_REFERENCE, ColumnrefContext.class),
                role(Role.CASE_EXPRESSION, Case_exprContext.class),
                role(Role.CASE_WHEN_LIST, When_clause_listContext.class),
                role(Role.CASE_WHEN, When_clauseContext.class),
                role(Role.CASE_DEFAULT, Case_defaultContext.class),
                role(Role.FUNCTION_CALL, Func_applicationContext.class),
                role(Role.WINDOW_FUNCTION, Window_clauseContext.class, Over_clauseContext.class),
                role(Role.QUERY_BOUNDARY, SelectstmtContext.class, Select_with_parensContext.class,
                        PreparablestmtContext.class),
                role(Role.SCALAR_SUBQUERY, Select_with_parensContext.class),
                role(Role.SELECT_TARGET_LIST, Target_listContext.class, Target_list_Context.class),
                role(Role.SELECT_TARGET_ITEM, Target_labelContext.class),
                role(Role.FROM_CLAUSE, From_clauseContext.class),
                role(Role.TABLE_SOURCE_ITEM, Table_primaryContext.class),
                role(Role.EXPRESSION, A_exprContext.class, B_exprContext.class, C_exprContext.class,
                        Func_exprContext.class, ColumnrefContext.class, Subquery_OpContext.class),
                role(Role.ROOT_EXPRESSION, A_exprContext.class),
                role(Role.CONTROL_SCOPE, Join_qualContext.class, Where_clauseContext.class,
                        Having_clauseContext.class, Group_clauseContext.class));
    }
}
