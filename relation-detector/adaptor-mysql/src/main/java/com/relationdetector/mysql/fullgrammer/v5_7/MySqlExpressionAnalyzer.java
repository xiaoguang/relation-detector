package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.core.fullgrammer.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.ExprContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.FromClauseContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.JoinedTableContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.QuerySpecificationContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.RuntimeFunctionCallContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.SelectItemContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.SimpleExprSubQueryContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.SingleTableContext;

/**
 * MySQL full-grammer expression analyzer.
 *
 * <p>CN: 目前复用 core 的 parse-tree expression analyzer，并保留 MySQL 专属扩展点。
 * 如果 MySQL grammar 需要特殊函数或变量语义，应在这里覆盖，而不是改 PostgreSQL。
 *
 * <p>EN: MySQL full-grammer expression analyzer. It currently reuses the core
 * parse-tree expression analyzer and remains the MySQL-specific extension point
 * for future function or variable semantics.
 */
final class MySqlExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
    @Override
    protected boolean preferAggregateArgumentSourcesOnly() {
        return false;
    }

    @Override
    public FullGrammerExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        RuntimeFunctionCallContext runtimeFunction = runtimeDateFunction(unwrapSingleChildContexts(expression));
        if (runtimeFunction == null) {
            return super.analyze(expression, defaultQualifier);
        }
        return analyzeRuntimeDateFunction(runtimeFunction, defaultQualifier);
    }

    @Override
    public List<FullGrammerExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        List<FullGrammerExpressionAnalysis> result = new ArrayList<>();
        FullGrammerExpressionAnalysis value = valueOnlyAnalysis(expression, defaultQualifier);
        if (value.hasSources()) {
            result.add(value);
        }
        FullGrammerExpressionAnalysis control = scalarSubqueryControlAnalysis(expression, defaultQualifier);
        if (control.hasSources()) {
            result.add(control);
        }
        return result;
    }

    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return containsScalarSubquery(expression);
    }

    private FullGrammerExpressionAnalysis valueOnlyAnalysis(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return empty("VALUE");
        }
        ParseTree unwrapped = unwrapSingleChildContexts(tree);
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubquerySelectedAnalysis(unwrapped, defaultQualifier);
        }
        if (!containsScalarSubquery(tree)) {
            return analyze(tree, defaultQualifier);
        }
        FullGrammerExpressionAnalysis full = analyze(tree, defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            FullGrammerExpressionAnalysis child = valueOnlyAnalysis(tree.getChild(index), defaultQualifier);
            appendSources(aliases, columns, seen, child);
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, full.transformType(), "VALUE");
    }

    private FullGrammerExpressionAnalysis scalarSubquerySelectedAnalysis(
            ParseTree subquery,
            String defaultQualifier
    ) {
        QuerySpecificationContext query = firstQuerySpecification(subquery);
        if (query == null || query.selectItemList() == null || query.selectItemList().selectItem().size() != 1) {
            return empty("VALUE");
        }
        SelectItemContext item = query.selectItemList().selectItem(0);
        if (item.expr() == null) {
            return empty("VALUE");
        }
        return selectedProjectionValueAnalysis(item.expr(), singleProjectionQualifier(query.fromClause(), defaultQualifier));
    }

    private FullGrammerExpressionAnalysis selectedProjectionValueAnalysis(ParseTree expression, String defaultQualifier) {
        FullGrammerExpressionAnalysis selected = analyze(expression, defaultQualifier);
        if (!containsAggregateFunction(expression)) {
            return selected.withTransform(selected.transformType(), "VALUE");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectAggregateArgumentSources(expression, defaultQualifier, aliases, columns, seen);
        if (aliases.isEmpty()) {
            return selected.withTransform(selected.transformType(), "VALUE");
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, selected.transformType(), "VALUE");
    }

    private void collectAggregateArgumentSources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) {
            return;
        }
        if (isAggregateFunctionContext(tree)) {
            appendSources(aliases, columns, seen, analyze(tree, defaultQualifier));
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectAggregateArgumentSources(tree.getChild(index), defaultQualifier, aliases, columns, seen);
        }
    }

    private boolean containsAggregateFunction(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isAggregateFunctionContext(tree)) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsAggregateFunction(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAggregateFunctionContext(ParseTree tree) {
        String contextName = tree.getClass().getSimpleName();
        if (contextName.contains("Sum")) {
            return true;
        }
        if (!contextName.equals("FunctionCallContext") && !contextName.equals("Func_applicationContext")) {
            return false;
        }
        return isAggregateFunction(firstLeafText(tree).toLowerCase(java.util.Locale.ROOT));
    }

    private boolean isAggregateFunction(String value) {
        return value.equals("sum")
                || value.equals("avg")
                || value.equals("count")
                || value.equals("min")
                || value.equals("max");
    }

    private String firstLeafText(ParseTree tree) {
        if (tree == null) {
            return "";
        }
        if (tree.getChildCount() == 0) {
            return tree.getText();
        }
        return firstLeafText(tree.getChild(0));
    }

    private FullGrammerExpressionAnalysis scalarSubqueryControlAnalysis(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return empty("CONTROL");
        }
        ParseTree unwrapped = unwrapSingleChildContexts(tree);
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubqueryContextAnalysis(unwrapped, defaultQualifier);
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            appendSources(aliases, columns, seen, scalarSubqueryControlAnalysis(tree.getChild(index), defaultQualifier));
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private FullGrammerExpressionAnalysis scalarSubqueryContextAnalysis(
            ParseTree subquery,
            String defaultQualifier
    ) {
        QuerySpecificationContext query = firstQuerySpecification(subquery);
        if (query == null) {
            return empty("CONTROL");
        }
        String scalarDefaultQualifier = singleProjectionQualifier(query.fromClause(), defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (query.fromClause() != null) {
            for (JoinedTableContext join : joinedTables(query.fromClause())) {
                if (join.expr() != null) {
                    appendSources(aliases, columns, seen, analyze(join.expr(), scalarDefaultQualifier));
                }
            }
        }
        if (query.whereClause() != null && query.whereClause().expr() != null) {
            appendSources(aliases, columns, seen, analyze(query.whereClause().expr(), scalarDefaultQualifier));
        }
        if (query.groupByClause() != null) {
            appendSources(aliases, columns, seen, analyze(query.groupByClause(), scalarDefaultQualifier));
        }
        if (query.havingClause() != null && query.havingClause().expr() != null) {
            appendSources(aliases, columns, seen, analyze(query.havingClause().expr(), scalarDefaultQualifier));
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private List<JoinedTableContext> joinedTables(ParseTree tree) {
        List<JoinedTableContext> result = new ArrayList<>();
        collectJoinedTables(tree, result);
        return result;
    }

    private void collectJoinedTables(ParseTree tree, List<JoinedTableContext> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof JoinedTableContext joined) {
            result.add(joined);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectJoinedTables(tree.getChild(index), result);
        }
    }

    private String singleProjectionQualifier(FromClauseContext fromClause, String fallback) {
        if (fromClause == null) {
            return fallback;
        }
        List<SingleTableContext> tables = new ArrayList<>();
        collectSingleTables(fromClause, tables);
        if (tables.size() != 1) {
            return fallback;
        }
        SingleTableContext table = tables.get(0);
        if (table.tableAlias() != null) {
            List<String> aliasIdentifiers = identifiers(table.tableAlias());
            if (!aliasIdentifiers.isEmpty()) {
                return aliasIdentifiers.get(aliasIdentifiers.size() - 1);
            }
        }
        if (table.tableRef() != null) {
            List<String> tableIdentifiers = identifiers(table.tableRef());
            if (!tableIdentifiers.isEmpty()) {
                return tableIdentifiers.get(tableIdentifiers.size() - 1);
            }
        }
        List<String> fromIdentifiers = identifiers(fromClause);
        if (!fromIdentifiers.isEmpty()) {
            return fromIdentifiers.size() >= 2 ? fromIdentifiers.get(1) : fromIdentifiers.get(0);
        }
        return fallback;
    }

    private void collectSingleTables(ParseTree tree, List<SingleTableContext> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof SingleTableContext singleTable) {
            result.add(singleTable);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectSingleTables(tree.getChild(index), result);
        }
    }

    private QuerySpecificationContext firstQuerySpecification(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (tree instanceof QuerySpecificationContext query) {
            return query;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            QuerySpecificationContext found = firstQuerySpecification(tree.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean containsScalarSubquery(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isScalarSubqueryBoundary(unwrapSingleChildContexts(tree))) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsScalarSubquery(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalarSubqueryBoundary(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof SimpleExprSubQueryContext) {
            return true;
        }
        String name = tree.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("subquery") && firstQuerySpecification(tree) != null) {
            return true;
        }
        return (name.contains("query") || name.contains("select"))
                && firstQuerySpecification(tree) != null
                && !hasColumnOutsideQueryBoundary(tree);
    }

    private boolean hasColumnOutsideQueryBoundary(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        String name = tree.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
        if ((name.contains("query") || name.contains("subquery") || name.contains("select"))
                && firstQuerySpecification(tree) != null) {
            return false;
        }
        String className = tree.getClass().getSimpleName();
        if (className.equals("ColumnrefContext")
                || className.equals("ColumnRefContext")
                || className.equals("SimpleExprColumnRefContext")
                || className.equals("Full_column_nameContext")) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (hasColumnOutsideQueryBoundary(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private void appendSources(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammerExpressionAnalysis analysis
    ) {
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            String alias = analysis.sourceAliases().get(index);
            String column = analysis.sourceColumns().get(index);
            String key = alias + "\u0000" + column;
            if (seen.add(key)) {
                aliases.add(alias);
                columns.add(column);
            }
        }
    }

    private List<String> identifiers(ParseTree tree) {
        List<String> result = new ArrayList<>();
        collectIdentifiers(tree, result);
        return result;
    }

    private void collectIdentifiers(ParseTree tree, List<String> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof TerminalNode terminal) {
            String clean = cleanIdentifier(terminal.getText());
            if (isIdentifierToken(clean)) {
                result.add(clean);
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectIdentifiers(tree.getChild(index), result);
        }
    }

    private boolean isIdentifierToken(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (Set.of("as", "select", "from", "where", "join", "on", "left", "right", "inner", "outer").contains(lower)) {
            return false;
        }
        return value.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '$');
    }

    private FullGrammerExpressionAnalysis empty(String flowKind) {
        return new FullGrammerExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", flowKind);
    }

    private RuntimeFunctionCallContext runtimeDateFunction(ParseTree tree) {
        if (!(tree instanceof RuntimeFunctionCallContext runtimeFunction)) {
            return null;
        }
        if (runtimeFunction.DATE_ADD_SYMBOL() != null
                || runtimeFunction.DATE_SUB_SYMBOL() != null
                || runtimeFunction.ADDDATE_SYMBOL() != null
                || runtimeFunction.SUBDATE_SYMBOL() != null
                || runtimeFunction.DATE_SYMBOL() != null) {
            return runtimeFunction;
        }
        return null;
    }

    private FullGrammerExpressionAnalysis analyzeRuntimeDateFunction(
            RuntimeFunctionCallContext runtimeFunction,
            String defaultQualifier
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> transforms = new ArrayList<>();
        Set<String> seenSources = new LinkedHashSet<>();
        for (ExprContext expr : runtimeFunction.expr()) {
            FullGrammerExpressionAnalysis analysis = super.analyze(expr, defaultQualifier);
            transforms.add(analysis.transformType());
            int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String key = analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index);
                if (seenSources.add(key)) {
                    aliases.add(analysis.sourceAliases().get(index));
                    columns.add(analysis.sourceColumns().get(index));
                }
            }
        }
        return new FullGrammerExpressionAnalysis(
                aliases,
                columns,
                dominantTransform(transforms),
                "VALUE");
    }

    private String dominantTransform(List<String> transforms) {
        String dominant = "FUNCTION_CALL";
        for (String transform : transforms) {
            if (priority(transform) > priority(dominant)) {
                dominant = transform;
            }
        }
        return dominant;
    }

    private int priority(String transform) {
        return switch (transform) {
            case "CASE_WHEN" -> 8;
            case "CUMULATIVE" -> 7;
            case "AGGREGATE" -> 6;
            case "WINDOW_DERIVED" -> 5;
            case "COALESCE" -> 4;
            case "CONCAT_FORMAT" -> 3;
            case "ARITHMETIC" -> 2;
            case "FUNCTION_CALL" -> 1;
            default -> 0;
        };
    }

    private ParseTree unwrapSingleChildContexts(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && current.getChildCount() == 1 && !(current instanceof TerminalNode)) {
            current = current.getChild(0);
        }
        return current;
    }
}
