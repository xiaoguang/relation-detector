package com.relationdetector.mysql.fullgrammar.v5_7;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.PredicateJoinKind;
import com.relationdetector.core.parser.fullgrammar.tree.AbstractFullGrammarParseTreeAdapter;
import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarColumnReference;
import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarIdentifiers;
import com.relationdetector.core.parser.fullgrammar.tree.FullGrammarParseTreeAdapter.EqualityOperands;
import com.relationdetector.core.parser.fullgrammar.tree.FullGrammarParseTreeAdapter.OperatorSemantic;
import com.relationdetector.core.parser.fullgrammar.tree.FullGrammarParseTreeAdapter.RowsetBinding;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter;
import com.relationdetector.mysql.fullgrammar.v5_7.MySqlFullGrammarParser.*;

final class MySqlParseTreeAdapter extends AbstractFullGrammarParseTreeAdapter
        implements MySqlExpressionContextAdapter {
    MySqlParseTreeAdapter() {
        super(
                role(Role.COLUMN_REFERENCE,
                        ctx -> ctx instanceof ColumnRefContext || ctx instanceof SimpleExprColumnRefContext),
                role(Role.CASE_EXPRESSION, ctx -> ctx instanceof SimpleExprCaseContext
                        || ctx instanceof CaseValueExpressionContext || ctx instanceof CaseStatementContext),
                role(Role.CASE_WHEN, ctx -> ctx instanceof WhenExpressionContext),
                role(Role.AGGREGATE_FUNCTION, ctx -> ctx instanceof SumExprContext),
                role(Role.WINDOW_FUNCTION,
                        ctx -> ctx instanceof WindowFunctionCallContext || ctx instanceof WindowingClauseContext),
                role(Role.CONCAT_EXPRESSION, ctx -> ctx instanceof SimpleExprConcatContext),
                role(Role.FUNCTION_CALL,
                        ctx -> ctx instanceof FunctionCallContext || ctx instanceof RuntimeFunctionCallContext),
                role(Role.QUERY_BOUNDARY, ctx -> ctx instanceof SelectStatementContext
                        || ctx instanceof QueryExpressionContext || ctx instanceof QueryExpressionParensContext
                        || ctx instanceof QueryExpressionWithOptLockingClausesContext
                        || ctx instanceof QuerySpecificationContext || ctx instanceof SubqueryContext
                        || ctx instanceof SimpleExprSubQueryContext),
                role(Role.SCALAR_SUBQUERY, ctx -> ctx instanceof SimpleExprSubQueryContext
                        || ctx instanceof SubqueryContext || ctx instanceof QueryExpressionParensContext),
                role(Role.SELECT_TARGET_LIST, ctx -> ctx instanceof SelectItemListContext),
                role(Role.SELECT_TARGET_ITEM, ctx -> ctx instanceof SelectItemContext),
                role(Role.FROM_CLAUSE, ctx -> ctx instanceof FromClauseContext),
                role(Role.TABLE_SOURCE_ITEM,
                        ctx -> ctx instanceof SingleTableContext || ctx instanceof JoinedTableContext),
                role(Role.EXPRESSION, ctx -> ctx instanceof ExprContext || ctx instanceof BoolPriContext
                        || ctx instanceof PredicateContext || ctx instanceof PredicateOperationsContext
                        || ctx instanceof BitExprContext || ctx instanceof SimpleExprContext));
    }

    @Override
    public Optional<FullGrammarColumnReference> directColumn(ParseTree tree) {
        if (tree instanceof SimpleExprColumnRefContext simple) {
            return directColumn(simple.columnRef());
        }
        if (tree instanceof ColumnRefContext column && column.fieldIdentifier() != null) {
            return FullGrammarIdentifiers.columnReference(column.fieldIdentifier().getText());
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
                    .map(FullGrammarIdentifiers::clean).filter(value -> !value.isBlank()).toList();
        }
        if (tree instanceof FieldsContext fields) {
            return fields.insertIdentifier().stream().map(ParseTree::getText)
                    .map(FullGrammarIdentifiers::clean).filter(value -> !value.isBlank()).toList();
        }
        if (tree instanceof ColumnRefContext || tree instanceof FieldIdentifierContext
                || tree instanceof TableRefContext
                || tree instanceof SelectAliasContext || tree instanceof IdentifierContext
                || tree instanceof PureIdentifierContext || tree instanceof QualifiedIdentifierContext
                || tree instanceof DotIdentifierContext) {
            return FullGrammarIdentifiers.qualifiedParts(tree.getText());
        }
        return List.of();
    }

    @Override
    public Optional<String> functionName(ParseTree tree) {
        if (tree instanceof SumExprContext || tree instanceof FunctionCallContext
                || tree instanceof RuntimeFunctionCallContext) {
            return Optional.ofNullable(((org.antlr.v4.runtime.ParserRuleContext) tree).getStart())
                    .map(org.antlr.v4.runtime.Token::getText)
                    .map(FullGrammarIdentifiers::clean)
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
    public Optional<String> literalValue(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && !(current instanceof SimpleExprLiteralContext)
                && !(current instanceof LiteralContext)) {
            List<ParseTree> children = typedChildren(current);
            if (children.size() != 1) return Optional.empty();
            current = children.get(0);
        }
        if (current == null) return Optional.empty();
        String value = current.getText().strip();
        if (value.length() >= 2 && value.startsWith("'") && value.endsWith("'"))
            value = value.substring(1, value.length() - 1).replace("''", "'");
        return Optional.of(value);
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
        String physical = String.join(".", FullGrammarIdentifiers.qualifiedParts(table.tableRef().getText()));
        List<String> tableParts = FullGrammarIdentifiers.qualifiedParts(physical);
        String qualifier = table.tableAlias() == null
                ? (tableParts.isEmpty() ? "" : tableParts.get(tableParts.size() - 1))
                : FullGrammarIdentifiers.clean(table.tableAlias().getText());
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
        if (!(tree instanceof JoinedTableContext joined)) return PredicateJoinKind.JOIN_ON.name();
        if (joined.outerJoinType() != null) {
            return joined.outerJoinType().LEFT_SYMBOL() != null
                    ? PredicateJoinKind.LEFT_JOIN.name() : PredicateJoinKind.RIGHT_JOIN.name();
        }
        if (joined.innerJoinType() != null) {
            if (joined.innerJoinType().CROSS_SYMBOL() != null) return PredicateJoinKind.CROSS_JOIN.name();
            if (joined.innerJoinType().STRAIGHT_JOIN_SYMBOL() != null) {
                return PredicateJoinKind.STRAIGHT_JOIN.name();
            }
            return PredicateJoinKind.JOIN_ON.name();
        }
        if (joined.naturalJoinType() != null) {
            if (joined.naturalJoinType().LEFT_SYMBOL() != null) return PredicateJoinKind.LEFT_JOIN.name();
            if (joined.naturalJoinType().RIGHT_SYMBOL() != null) return PredicateJoinKind.RIGHT_JOIN.name();
            return PredicateJoinKind.WHERE_OR_UNKNOWN.name();
        }
        return PredicateJoinKind.JOIN_ON.name();
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
