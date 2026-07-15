package com.relationdetector.sqlserver.fullgrammar.v2017;

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;
import com.relationdetector.sqlserver.fullgrammar.common.AbstractSqlServerParseTreeAdapter;
import com.relationdetector.sqlserver.fullgrammar.v2017.SqlServerFullGrammarParser.*;

final class SqlServerParseTreeAdapter extends AbstractSqlServerParseTreeAdapter {
    SqlServerParseTreeAdapter() {
        super(
                role(Role.COLUMN_REFERENCE, Full_column_nameContext.class), role(Role.CASE_EXPRESSION, Case_expressionContext.class),
                role(Role.CASE_SWITCH_SECTION, Switch_sectionContext.class), role(Role.CASE_SEARCH_SECTION, Switch_search_condition_sectionContext.class),
                role(Role.AGGREGATE_FUNCTION, Aggregate_windowed_functionContext.class), role(Role.FUNCTION_CALL, Function_callContext.class),
                role(Role.WINDOW_FUNCTION, Aggregate_windowed_functionContext.class), role(Role.GROUPING_SCOPE, Group_by_itemContext.class),
                role(Role.WINDOW_CONTROL_SCOPE, Over_clauseContext.class), role(Role.QUERY_BOUNDARY, SubqueryContext.class, Query_specificationContext.class),
                role(Role.SCALAR_SUBQUERY, SubqueryContext.class), role(Role.SELECT_TARGET_LIST, Select_listContext.class),
                role(Role.SELECT_TARGET_ITEM, Select_list_elemContext.class), role(Role.FROM_CLAUSE, Table_sourcesContext.class),
                role(Role.TABLE_SOURCE_ITEM, Table_source_itemContext.class), role(Role.EXPRESSION, ExpressionContext.class),
                role(Role.ROOT_EXPRESSION, ExpressionContext.class), role(Role.CONTROL_SCOPE, Join_onContext.class, Search_conditionContext.class, Group_by_itemContext.class),
                role(Role.SQL_CLAUSE, Sql_clausesContext.class), role(Role.COMMON_TABLE_EXPRESSION, Common_table_expressionContext.class),
                role(Role.QUERY_SPECIFICATION, Query_specificationContext.class), role(Role.JOIN_ON, Join_onContext.class),
                role(Role.CROSS_JOIN, Cross_joinContext.class), role(Role.APPLY, Apply_Context.class), role(Role.PREDICATE, PredicateContext.class),
                role(Role.INSERT_STATEMENT, Insert_statementContext.class), role(Role.UPDATE_STATEMENT, Update_statementContext.class),
                role(Role.MERGE_STATEMENT, Merge_statementContext.class), role(Role.DML_TRIGGER, Create_or_alter_dml_triggerContext.class),
                role(Role.CREATE_TABLE, Create_tableContext.class), role(Role.ALTER_TABLE, Alter_tableContext.class), role(Role.CREATE_INDEX, Create_indexContext.class),
                role(Role.FULL_TABLE_NAME, Full_table_nameContext.class), role(Role.TABLE_ALIAS, As_table_aliasContext.class),
                role(Role.COLUMN_ALIAS, As_column_aliasContext.class), role(Role.IDENTIFIER, Id_Context.class),
                role(Role.COMPARISON_OPERATOR, Comparison_operatorContext.class), role(Role.SELECT_STATEMENT, Select_statementContext.class),
                role(Role.SELECT_LIST, Select_listContext.class), role(Role.TABLE_SOURCES, Table_sourcesContext.class), role(Role.SEARCH_CONDITION, Search_conditionContext.class),
                role(Role.DERIVED_TABLE, Derived_tableContext.class), role(Role.ROWSET_FUNCTION, Rowset_functionContext.class), role(Role.OPEN_XML, Open_xmlContext.class),
                role(Role.OPEN_JSON, Open_jsonContext.class), role(Role.CHANGE_TABLE, Change_tableContext.class), role(Role.NODES_METHOD, Nodes_methodContext.class),
                role(Role.TABLE_SOURCE, Table_sourceContext.class), role(Role.DDL_OBJECT, Ddl_objectContext.class), role(Role.INSERT_COLUMN_LIST, Insert_column_name_listContext.class),
                role(Role.INSERT_VALUE, Insert_statement_valueContext.class), role(Role.WITH_EXPRESSION, With_expressionContext.class), role(Role.UPDATE_ELEMENT, Update_elemContext.class),
                role(Role.COLUMN_LIST, Column_name_listContext.class), role(Role.ORDERED_COLUMN_LIST, Column_name_list_with_orderContext.class),
                role(Role.EXPRESSION_LIST, Expression_list_Context.class), role(Role.MERGE_UPDATE_ELEMENT, Update_elem_mergeContext.class),
                role(Role.MERGE_NOT_MATCHED, Merge_not_matchedContext.class), role(Role.TABLE_NAME, Table_nameContext.class),
                role(Role.COLUMN_DEFINITION, Column_definitionContext.class), role(Role.TABLE_CONSTRAINT, Table_constraintContext.class),
                role(Role.FOREIGN_KEY_OPTIONS, Foreign_key_optionsContext.class));
    }

    @Override
    public String joinKind(ParseTree tree) {
        if (!(tree instanceof Join_onContext join)) return "JOIN_ON";
        if (join.LEFT() != null) return "LEFT_JOIN";
        if (join.RIGHT() != null) return "RIGHT_JOIN";
        if (join.FULL() != null) return "FULL_JOIN";
        return "JOIN_ON";
    }

    @Override public OperatorSemantic operatorSemantic(ParseTree tree) {
        if (tree instanceof ExpressionContext expression && expression.op != null) {
            return expression.DOUBLE_BAR() != null ? OperatorSemantic.CONCAT_FORMAT : OperatorSemantic.ARITHMETIC;
        }
        if (tree instanceof Unary_operator_expressionContext unary && unary.op != null) return OperatorSemantic.ARITHMETIC;
        return OperatorSemantic.NONE;
    }
    @Override public boolean isNonColumnValue(ParseTree tree) {
        return tree instanceof Primitive_expressionContext || tree instanceof ParameterContext;
    }
    @Override public List<EqualityOperands> directEqualities(ParseTree tree) {
        if (tree instanceof PredicateContext predicate && predicate.comparison_operator() != null
                && predicate.comparison_operator().EQUAL() != null && predicate.expression().size() == 2) {
            return List.of(new EqualityOperands(predicate.expression(0), predicate.expression(1)));
        }
        return List.of();
    }
    @Override public boolean isExistsPredicate(ParseTree tree) {
        return tree instanceof PredicateContext predicate && predicate.EXISTS() != null;
    }
    @Override public boolean isInPredicate(ParseTree tree) {
        return tree instanceof PredicateContext predicate && predicate.IN() != null;
    }
    @Override public DdlConstraintSemantic ddlConstraintSemantic(ParseTree tree) {
        if (tree instanceof Column_definitionContext column) {
            for (Column_definition_elementContext element : column.column_definition_element()) {
                DdlConstraintSemantic value = classify(element.column_constraint());
                if (value != DdlConstraintSemantic.NONE) return value;
            }
        }
        if (tree instanceof Table_constraintContext constraint) return classify(constraint);
        if (tree instanceof Create_indexContext index && index.UNIQUE() != null) return DdlConstraintSemantic.UNIQUE;
        return DdlConstraintSemantic.NONE;
    }
    private DdlConstraintSemantic classify(Column_constraintContext value) {
        if (value == null) return DdlConstraintSemantic.NONE;
        if (value.FOREIGN() != null) return DdlConstraintSemantic.FOREIGN_KEY;
        if (value.PRIMARY() != null) return DdlConstraintSemantic.PRIMARY_KEY;
        return value.UNIQUE() != null ? DdlConstraintSemantic.UNIQUE : DdlConstraintSemantic.NONE;
    }
    private DdlConstraintSemantic classify(Table_constraintContext value) {
        if (value.FOREIGN() != null) return DdlConstraintSemantic.FOREIGN_KEY;
        if (value.PRIMARY() != null) return DdlConstraintSemantic.PRIMARY_KEY;
        return value.UNIQUE() != null ? DdlConstraintSemantic.UNIQUE : DdlConstraintSemantic.NONE;
    }
}
