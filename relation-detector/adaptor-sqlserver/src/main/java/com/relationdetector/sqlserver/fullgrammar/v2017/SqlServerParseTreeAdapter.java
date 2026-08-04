package com.relationdetector.sqlserver.fullgrammar.v2017;

import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;
import com.relationdetector.sqlserver.fullgrammar.common.AbstractSqlServerParseTreeAdapter;
import com.relationdetector.sqlserver.fullgrammar.v2017.SqlServerFullGrammarParser.*;

final class SqlServerParseTreeAdapter extends AbstractSqlServerParseTreeAdapter {
    SqlServerParseTreeAdapter() {
        super(
                role(Role.COLUMN_REFERENCE, ctx -> ctx instanceof Full_column_nameContext),
                role(Role.CASE_EXPRESSION, ctx -> ctx instanceof Case_expressionContext),
                role(Role.CASE_SWITCH_SECTION, ctx -> ctx instanceof Switch_sectionContext),
                role(Role.CASE_SEARCH_SECTION, ctx -> ctx instanceof Switch_search_condition_sectionContext),
                role(Role.AGGREGATE_FUNCTION, ctx -> ctx instanceof Aggregate_windowed_functionContext),
                role(Role.FUNCTION_CALL, ctx -> ctx instanceof Function_callContext),
                role(Role.WINDOW_FUNCTION, ctx -> ctx instanceof Aggregate_windowed_functionContext),
                role(Role.GROUPING_SCOPE, ctx -> ctx instanceof Group_by_itemContext),
                role(Role.WINDOW_CONTROL_SCOPE, ctx -> ctx instanceof Over_clauseContext),
                role(Role.QUERY_BOUNDARY, ctx -> ctx instanceof SubqueryContext
                        || ctx instanceof Query_specificationContext),
                role(Role.SCALAR_SUBQUERY, ctx -> ctx instanceof SubqueryContext),
                role(Role.SELECT_TARGET_LIST, ctx -> ctx instanceof Select_listContext),
                role(Role.SELECT_TARGET_ITEM, ctx -> ctx instanceof Select_list_elemContext),
                role(Role.FROM_CLAUSE, ctx -> ctx instanceof Table_sourcesContext),
                role(Role.TABLE_SOURCE_ITEM, ctx -> ctx instanceof Table_source_itemContext),
                role(Role.EXPRESSION, ctx -> ctx instanceof ExpressionContext),
                role(Role.ROOT_EXPRESSION, ctx -> ctx instanceof ExpressionContext),
                role(Role.CONTROL_SCOPE, ctx -> ctx instanceof Join_onContext
                        || ctx instanceof Search_conditionContext
                        || ctx instanceof Group_by_itemContext),
                role(Role.SQL_CLAUSE, ctx -> ctx instanceof Sql_clausesContext),
                role(Role.COMMON_TABLE_EXPRESSION, ctx -> ctx instanceof Common_table_expressionContext),
                role(Role.QUERY_SPECIFICATION, ctx -> ctx instanceof Query_specificationContext),
                role(Role.JOIN_ON, ctx -> ctx instanceof Join_onContext), role(Role.CROSS_JOIN, ctx -> ctx instanceof Cross_joinContext),
                role(Role.APPLY, ctx -> ctx instanceof Apply_Context), role(Role.PREDICATE, ctx -> ctx instanceof PredicateContext),
                role(Role.INSERT_STATEMENT, ctx -> ctx instanceof Insert_statementContext),
                role(Role.UPDATE_STATEMENT, ctx -> ctx instanceof Update_statementContext),
                role(Role.MERGE_STATEMENT, ctx -> ctx instanceof Merge_statementContext),
                role(Role.DML_TRIGGER, ctx -> ctx instanceof Create_or_alter_dml_triggerContext),
                role(Role.CREATE_TABLE, ctx -> ctx instanceof Create_tableContext), role(Role.ALTER_TABLE, ctx -> ctx instanceof Alter_tableContext),
                role(Role.CREATE_INDEX, ctx -> ctx instanceof Create_indexContext), role(Role.FULL_TABLE_NAME, ctx -> ctx instanceof Full_table_nameContext),
                role(Role.TABLE_ALIAS, ctx -> ctx instanceof As_table_aliasContext), role(Role.COLUMN_ALIAS, ctx -> ctx instanceof As_column_aliasContext),
                role(Role.IDENTIFIER, ctx -> ctx instanceof Id_Context),
                role(Role.COMPARISON_OPERATOR, ctx -> ctx instanceof Comparison_operatorContext),
                role(Role.SELECT_STATEMENT, ctx -> ctx instanceof Select_statementContext), role(Role.SELECT_LIST, ctx -> ctx instanceof Select_listContext),
                role(Role.TABLE_SOURCES, ctx -> ctx instanceof Table_sourcesContext), role(Role.SEARCH_CONDITION, ctx -> ctx instanceof Search_conditionContext),
                role(Role.DERIVED_TABLE, ctx -> ctx instanceof Derived_tableContext), role(Role.ROWSET_FUNCTION, ctx -> ctx instanceof Rowset_functionContext),
                role(Role.OPEN_XML, ctx -> ctx instanceof Open_xmlContext), role(Role.OPEN_JSON, ctx -> ctx instanceof Open_jsonContext),
                role(Role.CHANGE_TABLE, ctx -> ctx instanceof Change_tableContext), role(Role.NODES_METHOD, ctx -> ctx instanceof Nodes_methodContext),
                role(Role.TABLE_SOURCE, ctx -> ctx instanceof Table_sourceContext), role(Role.DDL_OBJECT, ctx -> ctx instanceof Ddl_objectContext),
                role(Role.INSERT_COLUMN_LIST, ctx -> ctx instanceof Insert_column_name_listContext),
                role(Role.INSERT_VALUE, ctx -> ctx instanceof Insert_statement_valueContext), role(Role.WITH_EXPRESSION, ctx -> ctx instanceof With_expressionContext),
                role(Role.UPDATE_ELEMENT, ctx -> ctx instanceof Update_elemContext), role(Role.COLUMN_LIST, ctx -> ctx instanceof Column_name_listContext),
                role(Role.ORDERED_COLUMN_LIST, ctx -> ctx instanceof Column_name_list_with_orderContext),
                role(Role.EXPRESSION_LIST, ctx -> ctx instanceof Expression_list_Context),
                role(Role.MERGE_UPDATE_ELEMENT, ctx -> ctx instanceof Update_elem_mergeContext),
                role(Role.MERGE_NOT_MATCHED, ctx -> ctx instanceof Merge_not_matchedContext), role(Role.TABLE_NAME, ctx -> ctx instanceof Table_nameContext),
                role(Role.COLUMN_DEFINITION, ctx -> ctx instanceof Column_definitionContext), role(Role.TABLE_CONSTRAINT, ctx -> ctx instanceof Table_constraintContext),
                role(Role.FOREIGN_KEY_OPTIONS, ctx -> ctx instanceof Foreign_key_optionsContext));
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
