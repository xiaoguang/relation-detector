package com.relationdetector.sqlserver.fullgrammer.v2025;

import com.relationdetector.core.fullgrammer.AbstractFullGrammerParseTreeAdapter;
import com.relationdetector.sqlserver.fullgrammer.v2025.SqlServerFullGrammerParser.*;

final class SqlServer2025ParseTreeAdapter extends AbstractFullGrammerParseTreeAdapter {
    SqlServer2025ParseTreeAdapter() {
        super(
                role(Role.COLUMN_REFERENCE, Full_column_nameContext.class), role(Role.CASE_EXPRESSION, Case_expressionContext.class),
                role(Role.CASE_SWITCH_SECTION, Switch_sectionContext.class), role(Role.CASE_SEARCH_SECTION, Switch_search_condition_sectionContext.class),
                role(Role.AGGREGATE_FUNCTION, Aggregate_windowed_functionContext.class), role(Role.FUNCTION_CALL, Function_callContext.class),
                role(Role.WINDOW_FUNCTION, Aggregate_windowed_functionContext.class), role(Role.QUERY_BOUNDARY, SubqueryContext.class, Query_specificationContext.class),
                role(Role.SCALAR_SUBQUERY, SubqueryContext.class), role(Role.SELECT_TARGET_LIST, Select_listContext.class),
                role(Role.SELECT_TARGET_ITEM, Select_list_elemContext.class), role(Role.FROM_CLAUSE, Table_sourcesContext.class),
                role(Role.TABLE_SOURCE_ITEM, Table_source_itemContext.class), role(Role.EXPRESSION, ExpressionContext.class),
                role(Role.ROOT_EXPRESSION, ExpressionContext.class), role(Role.CONTROL_SCOPE, Join_onContext.class, Search_conditionContext.class, Group_by_itemContext.class));
    }
}
