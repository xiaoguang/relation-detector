package com.relationdetector.oracle.fullgrammar.v19c;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.oracle.fullgrammar.common.AbstractOracleFullGrammarParseTreeAdapter;
import com.relationdetector.oracle.fullgrammar.v19c.OracleFullGrammarParser;
import com.relationdetector.oracle.fullgrammar.v19c.OracleFullGrammarParser.*;

final class OracleParseTreeAdapter extends AbstractOracleFullGrammarParseTreeAdapter {
    OracleParseTreeAdapter() {
        super(Map.of(Symbol.PRIMARY, OracleFullGrammarParser.PRIMARY, Symbol.UNIQUE, OracleFullGrammarParser.UNIQUE,
                        Symbol.IN, OracleFullGrammarParser.IN, Symbol.NOT, OracleFullGrammarParser.NOT,
                        Symbol.EXISTS, OracleFullGrammarParser.EXISTS, Symbol.LEFT, OracleFullGrammarParser.LEFT,
                        Symbol.RIGHT, OracleFullGrammarParser.RIGHT, Symbol.FULL, OracleFullGrammarParser.FULL,
                        Symbol.CROSS, OracleFullGrammarParser.CROSS, Symbol.EQUAL, OracleFullGrammarParser.EQUALS_OP),
                role(Role.ROUTINE_BODY, ctx -> ctx instanceof Create_procedure_bodyContext
                        || ctx instanceof Create_function_bodyContext
                        || ctx instanceof Create_triggerContext),
                role(Role.ROUTINE_PARAMETER, ctx -> ctx instanceof ParameterContext),
                role(Role.ROUTINE_PARAMETER_NAME, ctx -> ctx instanceof Parameter_nameContext),
                role(Role.VARIABLE_DECLARATION, ctx -> ctx instanceof Variable_declarationContext),
                role(Role.CREATE_TRIGGER, ctx -> ctx instanceof Create_triggerContext),
                role(Role.DML_EVENT_CLAUSE, ctx -> ctx instanceof Dml_event_clauseContext),
                role(Role.BIND_VARIABLE, ctx -> ctx instanceof Bind_variableContext),
                role(Role.CTE, ctx -> ctx instanceof Subquery_factoring_clauseContext),
                role(Role.CREATE_TABLE, ctx -> ctx instanceof Create_tableContext), role(Role.ALTER_TABLE, ctx -> ctx instanceof Alter_tableContext),
                role(Role.COLUMN_DEFINITION, ctx -> ctx instanceof Column_definitionContext
                        || ctx instanceof Virtual_column_definitionContext),
                role(Role.OUT_OF_LINE_CONSTRAINT, ctx -> ctx instanceof Out_of_line_constraintContext),
                role(Role.FOREIGN_KEY, ctx -> ctx instanceof Foreign_key_clauseContext), role(Role.CREATE_INDEX, ctx -> ctx instanceof Create_indexContext),
                role(Role.SELECT_STATEMENT, ctx -> ctx instanceof Select_statementContext),
                role(Role.QUERY_BLOCK, ctx -> ctx instanceof Query_blockContext), role(Role.TABLE_REF_AUX, ctx -> ctx instanceof Table_ref_auxContext),
                role(Role.TABLE_REF_INTERNAL, ctx -> ctx instanceof Table_ref_aux_internal_oneContext
                        || ctx instanceof Table_ref_aux_internal_threContext),
                role(Role.GENERAL_TABLE_REF, ctx -> ctx instanceof General_table_refContext),
                role(Role.SELECTED_TABLEVIEW, ctx -> ctx instanceof Selected_tableviewContext), role(Role.JOIN_CLAUSE, ctx -> ctx instanceof Join_clauseContext),
                role(Role.JOIN_ON, ctx -> ctx instanceof Join_on_partContext), role(Role.JOIN_USING, ctx -> ctx instanceof Join_using_partContext),
                role(Role.WHERE_CLAUSE, ctx -> ctx instanceof Where_clauseContext), role(Role.GROUP_BY_ELEMENT, ctx -> ctx instanceof Group_by_elementsContext),
                role(Role.HAVING_CLAUSE, ctx -> ctx instanceof Having_clauseContext),
                role(Role.LOGICAL_EXPRESSION, ctx -> ctx instanceof Logical_expressionContext),
                role(Role.RELATIONAL_EXPRESSION, ctx -> ctx instanceof Relational_expressionContext),
                role(Role.COMPOUND_EXPRESSION, ctx -> ctx instanceof Compound_expressionContext),
                role(Role.QUANTIFIED_EXPRESSION, ctx -> ctx instanceof Quantified_expressionContext),
                role(Role.UPDATE_STATEMENT, ctx -> ctx instanceof Update_statementContext),
                role(Role.UPDATE_SET_CLAUSE, ctx -> ctx instanceof Column_based_update_set_clauseContext),
                role(Role.SINGLE_TABLE_INSERT, ctx -> ctx instanceof Single_table_insertContext),
                role(Role.MERGE_STATEMENT, ctx -> ctx instanceof Merge_statementContext), role(Role.SUBQUERY, ctx -> ctx instanceof SubqueryContext),
                role(Role.COLUMN_REFERENCE, ctx -> ctx instanceof Column_nameContext
                        || ctx instanceof Table_elementContext),
                role(Role.GENERAL_ELEMENT, ctx -> ctx instanceof General_elementContext), role(Role.CASE_EXPRESSION, ctx -> ctx instanceof Case_expressionContext),
                role(Role.FUNCTION_EXPRESSION, ctx -> ctx instanceof String_functionContext
                        || ctx instanceof Standard_functionContext
                        || ctx instanceof Json_functionContext
                        || ctx instanceof Numeric_function_wrapperContext
                        || ctx instanceof Numeric_functionContext
                        || ctx instanceof Other_functionContext),
                role(Role.CONCATENATION, ctx -> ctx instanceof ConcatenationContext),
                role(Role.DML_TABLE_EXPRESSION, ctx -> ctx instanceof Dml_table_expression_clauseContext), role(Role.TABLEVIEW_NAME, ctx -> ctx instanceof Tableview_nameContext),
                role(Role.TABLE_ALIAS, ctx -> ctx instanceof Table_aliasContext), role(Role.COLUMN_ALIAS, ctx -> ctx instanceof Column_aliasContext),
                role(Role.PAREN_COLUMN_LIST, ctx -> ctx instanceof Paren_column_listContext),
                role(Role.COLUMN_LIST, ctx -> ctx instanceof Column_listContext), role(Role.COLUMN_NAME, ctx -> ctx instanceof Column_nameContext),
                role(Role.REFERENCES_CLAUSE, ctx -> ctx instanceof References_clauseContext), role(Role.TABLE_INDEX_CLAUSE, ctx -> ctx instanceof Table_index_clauseContext),
                role(Role.INDEX_EXPRESSION, ctx -> ctx instanceof Index_exprContext), role(Role.INLINE_CONSTRAINT, ctx -> ctx instanceof Inline_constraintContext),
                role(Role.SCHEMA_NAME, ctx -> ctx instanceof Schema_nameContext), role(Role.IDENTIFIER, ctx -> ctx instanceof IdentifierContext),
                role(Role.QUOTED_STRING, ctx -> ctx instanceof Quoted_stringContext), role(Role.SELECT_LIST_ELEMENT, ctx -> ctx instanceof Select_list_elementsContext),
                role(Role.SELECTED_LIST, ctx -> ctx instanceof Selected_listContext), role(Role.EXPRESSION, ctx -> ctx instanceof ExpressionContext),
                role(Role.SIMPLE_CASE_EXPRESSION, ctx -> ctx instanceof Simple_case_expressionContext), role(Role.SEARCHED_CASE_EXPRESSION, ctx -> ctx instanceof Searched_case_expressionContext),
                role(Role.CASE_WHEN_PART, ctx -> ctx instanceof Case_when_part_expressionContext), role(Role.CASE_ELSE_PART, ctx -> ctx instanceof Case_else_part_expressionContext),
                role(Role.TABLE_REF_INTERNAL_WRAPPER, ctx -> ctx instanceof Table_ref_aux_internalContext), role(Role.GENERAL_ELEMENT_PART, ctx -> ctx instanceof General_element_partContext),
                role(Role.FUNCTION_ARGUMENT, ctx -> ctx instanceof Function_argumentContext), role(Role.WINDOW_CLAUSE, ctx -> ctx instanceof Over_clauseContext),
                role(Role.QUERY_NAME, ctx -> ctx instanceof Query_nameContext),
                role(Role.FROM_CLAUSE, ctx -> ctx instanceof From_clauseContext), role(Role.HIERARCHICAL_QUERY_CLAUSE, ctx -> ctx instanceof Hierarchical_query_clauseContext),
                role(Role.GROUP_BY_CLAUSE, ctx -> ctx instanceof Group_by_clauseContext), role(Role.MODEL_CLAUSE, ctx -> ctx instanceof Model_clauseContext),
                role(Role.RELATIONAL_OPERATOR, ctx -> ctx instanceof Relational_operatorContext), role(Role.IN_ELEMENTS, ctx -> ctx instanceof In_elementsContext),
                role(Role.SELECT_ONLY_STATEMENT, ctx -> ctx instanceof Select_only_statementContext), role(Role.INSERT_INTO_CLAUSE, ctx -> ctx instanceof Insert_into_clauseContext),
                role(Role.MERGE_UPDATE_CLAUSE, ctx -> ctx instanceof Merge_update_clauseContext), role(Role.MERGE_ELEMENT, ctx -> ctx instanceof Merge_elementContext),
                role(Role.CONDITION, ctx -> ctx instanceof ConditionContext), role(Role.TABLE_NAME, ctx -> ctx instanceof Table_nameContext),
                role(Role.FOREIGN_KEY_CLAUSE, ctx -> ctx instanceof Foreign_key_clauseContext));
    }

    @Override
    public Optional<GeneralElementView> generalElementView(ParseTree tree) {
        if (!(tree instanceof General_elementContext element)) {
            return Optional.empty();
        }
        if (hasRecoveredPeriodAlias(element)) {
            return Optional.of(GeneralElementView.suppressed());
        }
        List<General_element_partContext> parts = new ArrayList<>();
        if (!collectGeneralElementParts(element, parts) || parts.isEmpty()) {
            return Optional.of(GeneralElementView.suppressed());
        }
        List<String> nameParts = new ArrayList<>();
        List<ParseTree> argumentExpressions = new ArrayList<>();
        boolean function = false;
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            General_element_partContext part = parts.get(partIndex);
            if (part.id_expression() == null
                    || part.link_name() != null
                    || isRecoveredPeriodIdentifier(part.id_expression())) {
                return Optional.of(GeneralElementView.suppressed());
            }
            String name = part.id_expression().getText();
            if (name == null || name.isBlank()) {
                return Optional.of(GeneralElementView.suppressed());
            }
            nameParts.add(name);
            List<Function_argumentContext> functionArguments = part.function_argument();
            if (functionArguments.size() > 1
                    || (!functionArguments.isEmpty() && partIndex != parts.size() - 1)) {
                return Optional.of(GeneralElementView.suppressed());
            }
            for (Function_argumentContext arguments : functionArguments) {
                function = true;
                for (ArgumentContext argument : arguments.argument()) {
                    if (argument.expression() == null) {
                        return Optional.of(GeneralElementView.suppressed());
                    }
                    argumentExpressions.add(argument.expression());
                }
            }
        }
        return Optional.of(new GeneralElementView(
                function ? GeneralElementKind.FUNCTION : GeneralElementKind.COLUMN_CANDIDATE,
                nameParts,
                argumentExpressions));
    }

    private boolean isRecoveredPeriodIdentifier(Id_expressionContext identifier) {
        Regular_idContext regular = identifier.regular_id();
        Non_reserved_keywords_in_12cContext keyword = regular == null
                ? null : regular.non_reserved_keywords_in_12c();
        return keyword != null && keyword.PERIOD() != null;
    }

    private boolean hasRecoveredPeriodAlias(General_elementContext element) {
        for (ParseTree ancestor = element.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor instanceof Select_list_elementsContext item) {
                Column_aliasContext alias = item.column_alias();
                IdentifierContext identifier = alias == null ? null : alias.identifier();
                return identifier != null
                        && identifier.id_expression() != null
                        && isRecoveredPeriodIdentifier(identifier.id_expression());
            }
        }
        return false;
    }

    private boolean collectGeneralElementParts(
            General_elementContext element,
            List<General_element_partContext> result
    ) {
        General_elementContext nested = element.general_element();
        if (nested != null && !collectGeneralElementParts(nested, result)) {
            return false;
        }
        List<General_element_partContext> direct = element.general_element_part();
        if (!element.PERIOD().isEmpty() && direct.isEmpty()) {
            return false;
        }
        if (nested == null && direct.isEmpty()) {
            return false;
        }
        result.addAll(direct);
        return true;
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

    @Override
    public boolean isConjunction(ParseTree tree) {
        return tree instanceof Logical_expressionContext logical && logical.AND() != null;
    }
}
