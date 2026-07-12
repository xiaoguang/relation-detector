package com.relationdetector.mysql.fullgrammer.v5_7;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammer.AbstractFullGrammerParseTreeAdapter;
import com.relationdetector.core.fullgrammer.FullGrammerColumnReference;
import com.relationdetector.core.fullgrammer.FullGrammerIdentifiers;
import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter.EqualityOperands;
import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter.OperatorSemantic;
import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter.RowsetBinding;
import com.relationdetector.mysql.fullgrammer.common.MySqlExpressionContextAdapter;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.*;

final class MySql57ParseTreeAdapter extends AbstractFullGrammerParseTreeAdapter
        implements MySqlExpressionContextAdapter {
    MySql57ParseTreeAdapter() {
        super(
                role(Role.COLUMN_REFERENCE, ColumnRefContext.class, SimpleExprColumnRefContext.class),
                role(Role.CASE_EXPRESSION, SimpleExprCaseContext.class, CaseValueExpressionContext.class,
                        CaseStatementContext.class),
                role(Role.CASE_WHEN, WhenExpressionContext.class),
                role(Role.AGGREGATE_FUNCTION, SumExprContext.class),
                role(Role.WINDOW_FUNCTION, WindowFunctionCallContext.class, WindowingClauseContext.class),
                role(Role.CONCAT_EXPRESSION, SimpleExprConcatContext.class),
                role(Role.FUNCTION_CALL, FunctionCallContext.class, RuntimeFunctionCallContext.class),
                role(Role.QUERY_BOUNDARY, SelectStatementContext.class, QueryExpressionContext.class,
                        QueryExpressionParensContext.class, QueryExpressionWithOptLockingClausesContext.class,
                        QuerySpecificationContext.class, SubqueryContext.class, SimpleExprSubQueryContext.class),
                role(Role.SCALAR_SUBQUERY, SimpleExprSubQueryContext.class, SubqueryContext.class,
                        QueryExpressionParensContext.class),
                role(Role.SELECT_TARGET_LIST, SelectItemListContext.class),
                role(Role.SELECT_TARGET_ITEM, SelectItemContext.class),
                role(Role.FROM_CLAUSE, FromClauseContext.class),
                role(Role.TABLE_SOURCE_ITEM, SingleTableContext.class, JoinedTableContext.class),
                role(Role.EXPRESSION, ExprContext.class, BoolPriContext.class, PredicateContext.class,
                        PredicateOperationsContext.class, BitExprContext.class, SimpleExprContext.class));
    }

    @Override
    public Optional<FullGrammerColumnReference> directColumn(ParseTree tree) {
        if (tree instanceof SimpleExprColumnRefContext simple) {
            return directColumn(simple.columnRef());
        }
        if (tree instanceof ColumnRefContext column && column.fieldIdentifier() != null) {
            return FullGrammerIdentifiers.columnReference(column.fieldIdentifier().getText());
        }
        return Optional.empty();
    }

    @Override
    public List<String> identifiers(ParseTree tree) {
        if (tree == null) return List.of();
        if (tree instanceof TableAliasContext alias) {
            return alias.identifier() == null ? List.of() : identifiers(alias.identifier());
        }
        if (tree instanceof SelectAliasContext alias) {
            ParseTree identifier = alias.identifier() != null
                    ? alias.identifier()
                    : alias.textStringLiteral();
            return identifier == null ? List.of() : identifiers(identifier);
        }
        if (tree instanceof IdentifierListWithParenthesesContext list) {
            return identifiers(list.identifierList());
        }
        if (tree instanceof IdentifierListContext list) {
            return list.identifier().stream().map(ParseTree::getText)
                    .map(FullGrammerIdentifiers::clean).filter(value -> !value.isBlank()).toList();
        }
        if (tree instanceof FieldsContext fields) {
            return fields.insertIdentifier().stream().map(ParseTree::getText)
                    .map(FullGrammerIdentifiers::clean).filter(value -> !value.isBlank()).toList();
        }
        if (tree instanceof ColumnRefContext || tree instanceof FieldIdentifierContext
                || tree instanceof TableRefContext
                || tree instanceof SelectAliasContext || tree instanceof IdentifierContext
                || tree instanceof PureIdentifierContext || tree instanceof QualifiedIdentifierContext
                || tree instanceof DotIdentifierContext) {
            return FullGrammerIdentifiers.qualifiedParts(tree.getText());
        }
        return List.of();
    }

    @Override
    public Optional<String> functionName(ParseTree tree) {
        if (tree instanceof SumExprContext || tree instanceof FunctionCallContext
                || tree instanceof RuntimeFunctionCallContext) {
            return Optional.ofNullable(((org.antlr.v4.runtime.ParserRuleContext) tree).getStart())
                    .map(org.antlr.v4.runtime.Token::getText)
                    .map(FullGrammerIdentifiers::clean)
                    .filter(value -> !value.isBlank());
        }
        return Optional.empty();
    }

    @Override
    public boolean isNonColumnValue(ParseTree tree) {
        return tree instanceof SimpleExprLiteralContext
                || tree instanceof LiteralContext
                || tree instanceof SimpleExprParamMarkerContext
                || tree instanceof SimpleExprUserVariableAssignmentContext
                || tree instanceof SimpleExprDefaultContext;
    }

    @Override
    public List<EqualityOperands> directEqualities(ParseTree tree) {
        if (tree instanceof PrimaryExprCompareContext comparison
                && comparison.compOp() != null
                && comparison.compOp().EQUAL_OPERATOR() != null) {
            return List.of(new EqualityOperands(comparison.boolPri(), comparison.predicate()));
        }
        return List.of();
    }

    @Override
    public Optional<RowsetBinding> rowsetBinding(ParseTree tree) {
        if (!(tree instanceof SingleTableContext table) || table.tableRef() == null) {
            return Optional.empty();
        }
        String physical = String.join(".", FullGrammerIdentifiers.qualifiedParts(table.tableRef().getText()));
        List<String> tableParts = FullGrammerIdentifiers.qualifiedParts(physical);
        String qualifier = table.tableAlias() == null
                ? (tableParts.isEmpty() ? "" : tableParts.get(tableParts.size() - 1))
                : FullGrammerIdentifiers.clean(table.tableAlias().getText());
        return physical.isBlank() ? Optional.empty() : Optional.of(new RowsetBinding(physical, qualifier));
    }

    @Override
    public boolean isArithmeticExpression(ParseTree tree) {
        if (tree instanceof BitExprContext expression) {
            return expression.op != null
                    || expression.MULT_OPERATOR() != null
                    || expression.DIV_OPERATOR() != null
                    || expression.MOD_OPERATOR() != null
                    || expression.DIV_SYMBOL() != null
                    || expression.MOD_SYMBOL() != null
                    || expression.PLUS_OPERATOR() != null
                    || expression.MINUS_OPERATOR() != null;
        }
        return tree instanceof SimpleExprUnaryContext;
    }

    @Override
    public OperatorSemantic operatorSemantic(ParseTree tree) {
        if (tree instanceof SimpleExprUserVariableAssignmentContext) {
            return OperatorSemantic.CUMULATIVE;
        }
        return MySqlExpressionContextAdapter.super.operatorSemantic(tree);
    }

    @Override
    public ConditionalParts conditionalParts(ParseTree tree) {
        List<ParseTree> values = new ArrayList<>();
        List<ParseTree> controls = new ArrayList<>();
        if (tree instanceof SimpleExprCaseContext expression) {
            expression.thenExpression().forEach(then -> values.add(then.expr()));
            if (expression.elseExpression() != null) {
                values.add(expression.elseExpression().expr());
            }
            if (expression.expr() != null) {
                controls.add(expression.expr());
            }
            expression.whenExpression().forEach(when -> controls.add(when.expr()));
            return new ConditionalParts(true, values, controls);
        }
        if (tree instanceof CaseValueExpressionContext expression) {
            expression.thenExpression().forEach(then -> values.add(then.expr()));
            if (expression.elseExpression() != null) {
                values.add(expression.elseExpression().expr());
            }
            if (expression.expr() != null) {
                controls.add(expression.expr());
            }
            expression.whenExpression().forEach(when -> controls.add(when.expr()));
            return new ConditionalParts(true, values, controls);
        }
        if (tree instanceof RuntimeFunctionCallContext runtime
                && runtime.IF_SYMBOL() != null && runtime.expr().size() >= 3) {
            values.add(runtime.expr(1));
            values.add(runtime.expr(2));
            controls.add(runtime.expr(0));
            return new ConditionalParts(true, values, controls);
        }
        return ConditionalParts.NONE;
    }

    @Override
    public QueryParts firstQuery(ParseTree tree) {
        QuerySpecificationContext query = firstQueryContext(tree);
        if (query == null) {
            return null;
        }
        List<ParseTree> projections = query.selectItemList() == null
                ? List.of()
                : query.selectItemList().selectItem().stream()
                .map(SelectItemContext::expr).filter(java.util.Objects::nonNull).map(ParseTree.class::cast).toList();
        List<ParseTree> joinPredicates = new ArrayList<>();
        collectJoinPredicates(query.fromClause(), joinPredicates);
        return new QueryParts(
                projections,
                query.fromClause(),
                joinPredicates,
                query.whereClause() == null ? null : query.whereClause().expr(),
                query.groupByClause(),
                query.havingClause() == null ? null : query.havingClause().expr());
    }

    @Override
    public String singleProjectionQualifier(ParseTree fromClause, String fallback) {
        if (fromClause == null) {
            return fallback;
        }
        List<SingleTableContext> tables = new ArrayList<>();
        collectSingleTables(fromClause, tables);
        if (tables.size() != 1) {
            return fallback;
        }
        SingleTableContext table = tables.get(0);
        List<String> identifiers = identifiers(
                table.tableAlias() != null ? table.tableAlias() : table.tableRef());
        return identifiers.isEmpty() ? fallback : identifiers.get(identifiers.size() - 1);
    }

    @Override
    public Optional<List<ParseTree>> runtimeDateArguments(ParseTree tree) {
        if (!(tree instanceof RuntimeFunctionCallContext runtime)
                || runtime.DATE_ADD_SYMBOL() == null
                && runtime.DATE_SUB_SYMBOL() == null
                && runtime.ADDDATE_SYMBOL() == null
                && runtime.SUBDATE_SYMBOL() == null
                && runtime.DATE_SYMBOL() == null) {
            return Optional.empty();
        }
        return Optional.of(runtime.expr().stream().map(ParseTree.class::cast).toList());
    }

    @Override
    public String joinKind(ParseTree tree) {
        if (!(tree instanceof JoinedTableContext joined)) return "JOIN_ON";
        if (joined.outerJoinType() != null) return joined.outerJoinType().getText().toUpperCase(Locale.ROOT);
        if (joined.innerJoinType() != null) return joined.innerJoinType().getText().toUpperCase(Locale.ROOT);
        if (joined.naturalJoinType() != null) return joined.naturalJoinType().getText().toUpperCase(Locale.ROOT);
        return "JOIN_ON";
    }

    @Override
    public String firstTableName(ParseTree tree) {
        if (tree == null) return "";
        if (tree instanceof SingleTableContext single && single.tableRef() != null) return single.tableRef().getText();
        for (int index = 0; index < tree.getChildCount(); index++) {
            String found = firstTableName(tree.getChild(index));
            if (!found.isBlank()) return found;
        }
        return "";
    }

    @Override
    public List<String> insertTargets(ParseTree tree) {
        if (!(tree instanceof FieldsContext fields)) return List.of();
        return fields.insertIdentifier().stream().map(ParseTree::getText)
                .map(this::cleanIdentifier).filter(value -> !value.isBlank()).toList();
    }

    @Override
    public List<ProjectionItem> selectItems(ParseTree tree) {
        List<ProjectionItem> result = new ArrayList<>();
        collectProjectionItems(tree, result);
        return List.copyOf(result);
    }

    @Override
    public List<ProjectionItem> topLevelProjectionItems(ParseTree tree) {
        QuerySpecificationContext query = firstQueryContext(tree);
        if (query == null || query.selectItemList() == null) return List.of();
        return query.selectItemList().selectItem().stream()
                .filter(item -> item.expr() != null)
                .map(item -> new ProjectionItem(item, item.expr(), item.selectAlias()))
                .toList();
    }

    private void collectProjectionItems(ParseTree tree, List<ProjectionItem> result) {
        if (tree == null) return;
        if (tree instanceof SelectItemContext item) {
            if (item.expr() != null) result.add(new ProjectionItem(item, item.expr(), item.selectAlias()));
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) collectProjectionItems(tree.getChild(index), result);
    }

    private String cleanIdentifier(String value) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() >= 2 && ((clean.startsWith("`") && clean.endsWith("`"))
                || (clean.startsWith("\"") && clean.endsWith("\"")))
                ? clean.substring(1, clean.length() - 1) : clean;
    }

    private QuerySpecificationContext firstQueryContext(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (tree instanceof QuerySpecificationContext query) {
            return query;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            QuerySpecificationContext found = firstQueryContext(tree.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void collectJoinPredicates(ParseTree tree, List<ParseTree> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof JoinedTableContext joined && joined.expr() != null) {
            result.add(joined.expr());
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectJoinPredicates(tree.getChild(index), result);
        }
    }

    private void collectSingleTables(ParseTree tree, List<SingleTableContext> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof SingleTableContext table) {
            result.add(table);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectSingleTables(tree.getChild(index), result);
        }
    }

}
