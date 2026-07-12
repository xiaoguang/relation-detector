package com.relationdetector.postgres.fullgrammer.v17;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammer.AbstractFullGrammerParseTreeAdapter;
import com.relationdetector.core.fullgrammer.FullGrammerColumnReference;
import com.relationdetector.core.fullgrammer.FullGrammerIdentifiers;
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

    @Override
    public Optional<FullGrammerColumnReference> directColumn(ParseTree tree) {
        return tree instanceof ColumnrefContext column
                ? FullGrammerIdentifiers.columnReference(column.getText()) : Optional.empty();
    }

    @Override
    public List<String> identifiers(ParseTree tree) {
        if (tree instanceof Alias_clauseContext alias && alias.colid() != null) {
            return List.of(FullGrammerIdentifiers.clean(alias.colid().getText()));
        }
        if (tree instanceof Qualified_nameContext || tree instanceof ColumnrefContext
                || tree instanceof ColidContext || tree instanceof NameContext
                || tree instanceof BareColLabelContext || tree instanceof ColLabelContext
                || tree instanceof Func_nameContext) {
            return FullGrammerIdentifiers.qualifiedParts(tree.getText());
        }
        return List.of();
    }

    @Override
    public Optional<String> functionName(ParseTree tree) {
        if (tree instanceof Func_applicationContext function && function.func_name() != null) {
            return Optional.of(FullGrammerIdentifiers.clean(function.func_name().getText()));
        }
        if (tree instanceof Func_expr_common_subexprContext function) {
            if (function.COALESCE() != null) return Optional.of("COALESCE");
            if (function.CAST() != null) return Optional.of("CAST");
            if (function.NULLIF() != null) return Optional.of("NULLIF");
        }
        if (tree instanceof A_expr_typecastContext cast && !cast.typename().isEmpty()) return Optional.of("CAST");
        return Optional.empty();
    }

    @Override
    public OperatorSemantic operatorSemantic(ParseTree tree) {
        if (tree instanceof A_expr_addContext add && (!add.PLUS().isEmpty() || !add.MINUS().isEmpty())
                || tree instanceof A_expr_mulContext multiply
                && (!multiply.STAR().isEmpty() || !multiply.SLASH().isEmpty() || !multiply.PERCENT().isEmpty())
                || tree instanceof A_expr_caretContext caret && caret.CARET() != null
                || tree instanceof A_expr_unary_signContext sign && (sign.PLUS() != null || sign.MINUS() != null)) {
            return OperatorSemantic.ARITHMETIC;
        }
        if (tree instanceof A_expr_qual_opContext qualified) {
            for (Qual_opContext operator : qualified.qual_op()) {
                if ("||".equals(operator.getText())) return OperatorSemantic.CONCAT_FORMAT;
            }
        }
        return OperatorSemantic.NONE;
    }

    @Override
    public boolean isNonColumnValue(ParseTree tree) {
        return tree instanceof AexprconstContext || tree instanceof PlsqlvariablenameContext;
    }

    @Override
    public CaseParts caseParts(ParseTree tree) {
        if (!(tree instanceof Case_exprContext expression) || expression.when_clause_list() == null) return CaseParts.NONE;
        List<ParseTree> values = new ArrayList<>();
        List<ParseTree> controls = new ArrayList<>();
        if (expression.case_arg() != null && expression.case_arg().a_expr() != null) controls.add(expression.case_arg().a_expr());
        for (When_clauseContext clause : expression.when_clause_list().when_clause()) {
            if (clause.a_expr().size() == 2) {
                controls.add(clause.a_expr(0));
                values.add(clause.a_expr(1));
            }
        }
        if (expression.case_default() != null && expression.case_default().a_expr() != null) values.add(expression.case_default().a_expr());
        return new CaseParts(true, values, controls);
    }

    @Override
    public List<EqualityOperands> directEqualities(ParseTree tree) {
        if (tree instanceof A_expr_compareContext comparison
                && comparison.EQUAL() != null && comparison.a_expr_like().size() == 2) {
            return List.of(new EqualityOperands(comparison.a_expr_like(0), comparison.a_expr_like(1)));
        }
        if (tree instanceof A_expr_is_notContext comparison
                && comparison.IS() != null && comparison.NOT() != null
                && comparison.DISTINCT() != null && comparison.FROM() != null
                && comparison.a_expr_compare() != null && comparison.a_expr() != null) {
            return List.of(new EqualityOperands(comparison.a_expr_compare(), comparison.a_expr()));
        }
        return List.of();
    }

    @Override
    public Optional<RowsetBinding> rowsetBinding(ParseTree tree) {
        if (!(tree instanceof Table_primaryContext table)
                || table.relation_expr() == null || table.relation_expr().qualified_name() == null) return Optional.empty();
        String physical = String.join(".", FullGrammerIdentifiers.qualifiedParts(table.relation_expr().qualified_name().getText()));
        String qualifier = table.alias_clause() == null || table.alias_clause().colid() == null
                ? lastPart(physical) : FullGrammerIdentifiers.clean(table.alias_clause().colid().getText());
        return physical.isBlank() ? Optional.empty() : Optional.of(new RowsetBinding(physical, qualifier));
    }

    private String lastPart(String qualified) {
        List<String> parts = FullGrammerIdentifiers.qualifiedParts(qualified);
        return parts.isEmpty() ? "" : parts.get(parts.size() - 1);
    }
}
