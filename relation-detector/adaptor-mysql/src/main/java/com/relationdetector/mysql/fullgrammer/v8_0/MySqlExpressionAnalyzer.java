package com.relationdetector.mysql.fullgrammer.v8_0;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammer.*;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.ExprContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.FromClauseContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.JoinedTableContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.QuerySpecificationContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.RuntimeFunctionCallContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.SelectItemContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.SimpleExprSubQueryContext;
import com.relationdetector.mysql.fullgrammer.v8_0.MySqlFullGrammerParser.SingleTableContext;

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
        if (runtimeFunction != null) {
            return analyzeRuntimeDateFunction(runtimeFunction, defaultQualifier);
        }
        FullGrammerExpressionAnalysis analysis = super.analyze(expression, defaultQualifier);
        RuntimeFunctionCallContext nestedRuntimeFunction = firstRuntimeDateFunction(expression);
        if (nestedRuntimeFunction == null || !analysis.hasSources()) {
            return analysis;
        }
        FullGrammerExpressionAnalysis nested = analyzeRuntimeDateFunction(nestedRuntimeFunction, defaultQualifier);
        LineageTransformType transform = LineageTransformClassifier.dominant(
                LineageTransformType.valueOf(analysis.transformType()),
                LineageTransformType.valueOf(nested.transformType()));
        return analysis.withTransform(transform.name(), analysis.flowKind());
    }

    @Override
    public List<FullGrammerExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        List<FullGrammerExpressionAnalysis> result = new ArrayList<>();
        FullGrammerExpressionAnalysis value = valueOnlyAnalysis(expression, defaultQualifier);
        if (value.hasSources()) {
            result.add(value);
        }
        result.addAll(nestedConditionalControlAnalyses(expression, defaultQualifier));
        FullGrammerExpressionAnalysis control = scalarSubqueryControlAnalysis(expression, defaultQualifier);
        if (control.hasSources()) {
            result.add(control);
        }
        return result;
    }

    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return containsScalarSubquery(expression) || containsCaseExpression(expression);
    }

    private FullGrammerExpressionAnalysis valueOnlyAnalysis(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return empty("VALUE");
        }
        ParseTree unwrapped = unwrapSingleChildContexts(tree);
        if (isTopLevelConditionalExpression(unwrapped)) {
            return conditionalValueOnlyAnalysis(unwrapped, defaultQualifier);
        }
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubquerySelectedAnalysis(unwrapped, defaultQualifier);
        }
        if (!containsScalarSubquery(tree) && !containsCaseExpression(tree)) {
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
        String transform = "CASE_WHEN".equals(full.transformType()) && containsAggregateFunction(tree)
                ? "AGGREGATE"
                : full.transformType();
        return new FullGrammerExpressionAnalysis(aliases, columns, transform, "VALUE");
    }

    private FullGrammerExpressionAnalysis conditionalValueOnlyAnalysis(ParseTree tree, String defaultQualifier) {
        List<ParseTree> valueExpressions = conditionalValueExpressions(unwrapSingleChildContexts(tree));
        if (valueExpressions.isEmpty()) {
            return caseWriteAnalyses(tree, defaultQualifier).stream()
                    .filter(analysis -> "VALUE".equals(analysis.flowKind()))
                    .findFirst()
                    .orElseGet(() -> empty("VALUE"));
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree valueExpression : valueExpressions) {
            appendSources(aliases, columns, seen, valueOnlyAnalysis(valueExpression, defaultQualifier));
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "VALUE");
    }

    private List<ParseTree> conditionalValueExpressions(ParseTree caseNode) {
        List<ParseTree> result = new ArrayList<>();
        if (caseNode instanceof MySqlFullGrammerParser.SimpleExprCaseContext expression) {
            expression.thenExpression().forEach(thenExpression -> result.add(thenExpression.expr()));
            if (expression.elseExpression() != null) {
                result.add(expression.elseExpression().expr());
            }
        } else if (caseNode instanceof MySqlFullGrammerParser.CaseValueExpressionContext expression) {
            expression.thenExpression().forEach(thenExpression -> result.add(thenExpression.expr()));
            if (expression.elseExpression() != null) {
                result.add(expression.elseExpression().expr());
            }
        } else if (caseNode instanceof RuntimeFunctionCallContext runtime
                && runtime.IF_SYMBOL() != null
                && runtime.expr().size() >= 3) {
            result.add(runtime.expr(1));
            result.add(runtime.expr(2));
        }
        return result;
    }

    private boolean containsCaseExpression(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isTopLevelConditionalExpression(tree)) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsCaseExpression(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean isTopLevelConditionalExpression(ParseTree tree) {
        return isTopLevelCaseExpression(tree)
                || (tree instanceof RuntimeFunctionCallContext runtime
                && runtime.IF_SYMBOL() != null
                && runtime.expr().size() >= 3);
    }

    private List<FullGrammerExpressionAnalysis> nestedConditionalControlAnalyses(
            ParseTree tree,
            String defaultQualifier
    ) {
        List<FullGrammerExpressionAnalysis> result = new ArrayList<>();
        collectNestedCaseControlAnalyses(tree, defaultQualifier, result);
        return result;
    }

    private void collectNestedCaseControlAnalyses(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammerExpressionAnalysis> result
    ) {
        if (tree == null) {
            return;
        }
        if (isTopLevelConditionalExpression(tree)) {
            FullGrammerExpressionAnalysis control = caseControlOnlyAnalysis(tree, defaultQualifier);
            if (control.hasSources()) {
                result.add(control);
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectNestedCaseControlAnalyses(tree.getChild(index), defaultQualifier, result);
        }
    }

    private FullGrammerExpressionAnalysis caseControlOnlyAnalysis(ParseTree tree, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectCaseControlSources(tree, defaultQualifier, aliases, columns, seen);
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void collectCaseControlSources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        ParseTree caseNode = unwrapSingleChildContexts(tree);
        List<ParseTree> controls = new ArrayList<>();
        List<ParseTree> values = conditionalValueExpressions(caseNode);
        if (caseNode instanceof MySqlFullGrammerParser.SimpleExprCaseContext expression) {
            if (expression.expr() != null) {
                controls.add(expression.expr());
            }
            expression.whenExpression().forEach(when -> controls.add(when.expr()));
        } else if (caseNode instanceof MySqlFullGrammerParser.CaseValueExpressionContext expression) {
            if (expression.expr() != null) {
                controls.add(expression.expr());
            }
            expression.whenExpression().forEach(when -> controls.add(when.expr()));
        } else if (caseNode instanceof RuntimeFunctionCallContext runtime
                && runtime.IF_SYMBOL() != null
                && runtime.expr().size() >= 3) {
            controls.add(runtime.expr(0));
        }
        for (ParseTree control : controls) {
            appendSources(aliases, columns, seen, analyze(control, defaultQualifier));
        }
        for (ParseTree value : values) {
            collectNestedCaseControlSources(value, defaultQualifier, aliases, columns, seen);
        }
    }

    private void collectNestedCaseControlSources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) {
            return;
        }
        if (isTopLevelConditionalExpression(tree)) {
            collectCaseControlSources(tree, defaultQualifier, aliases, columns, seen);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectNestedCaseControlSources(tree.getChild(index), defaultQualifier, aliases, columns, seen);
        }
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
        removeCaseControlOnlySources(expression, defaultQualifier, aliases, columns);
        if (aliases.isEmpty()) {
            return new FullGrammerExpressionAnalysis(List.of(), List.of(), selected.transformType(), "VALUE");
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, selected.transformType(), "VALUE");
    }

    private void removeCaseControlOnlySources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns
    ) {
        Set<String> controlKeys = new LinkedHashSet<>();
        Set<String> valueKeys = new LinkedHashSet<>();
        collectCaseRoleKeys(tree, defaultQualifier, controlKeys, valueKeys);
        for (int index = aliases.size() - 1; index >= 0; index--) {
            String key = aliases.get(index) + "\u0000" + columns.get(index);
            if (controlKeys.contains(key) && !valueKeys.contains(key)) {
                aliases.remove(index);
                columns.remove(index);
            }
        }
    }

    private void collectCaseRoleKeys(
            ParseTree tree,
            String defaultQualifier,
            Set<String> controlKeys,
            Set<String> valueKeys
    ) {
        if (tree == null) {
            return;
        }
        if (isTopLevelConditionalExpression(tree)) {
            addKeys(controlKeys, caseControlOnlyAnalysis(tree, defaultQualifier));
            addKeys(valueKeys, conditionalValueOnlyAnalysis(tree, defaultQualifier));
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectCaseRoleKeys(tree.getChild(index), defaultQualifier, controlKeys, valueKeys);
        }
    }

    private void addKeys(Set<String> keys, FullGrammerExpressionAnalysis analysis) {
        for (int index = 0; index < Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size()); index++) {
            keys.add(analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index));
        }
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
            for (int index = 0; index < tree.getChildCount(); index++) {
                ParseTree child = tree.getChild(index);
                if (!(child instanceof TerminalNode)) {
                    appendSources(aliases, columns, seen, valueOnlyAnalysis(child, defaultQualifier));
                }
            }
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
        return LineageTransformClassifier.classifyFunction(value, false) == LineageTransformType.AGGREGATE;
    }

    private RuntimeFunctionCallContext firstRuntimeDateFunction(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (tree instanceof RuntimeFunctionCallContext runtimeFunction) {
            return runtimeFunction;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            RuntimeFunctionCallContext found = firstRuntimeDateFunction(tree.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
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
                    appendSources(aliases, columns, seen, controlSources(join.expr(), scalarDefaultQualifier));
                }
            }
        }
        if (query.whereClause() != null && query.whereClause().expr() != null) {
            appendSources(aliases, columns, seen, controlSources(query.whereClause().expr(), scalarDefaultQualifier));
        }
        if (query.groupByClause() != null) {
            appendSources(aliases, columns, seen, controlSources(query.groupByClause(), scalarDefaultQualifier));
        }
        if (query.havingClause() != null && query.havingClause().expr() != null) {
            appendSources(aliases, columns, seen, controlSources(query.havingClause().expr(), scalarDefaultQualifier));
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private FullGrammerExpressionAnalysis controlSources(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return empty("CONTROL");
        }
        ParseTree unwrapped = unwrapSingleChildContexts(tree);
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubqueryPredicateOperandControl(unwrapped, defaultQualifier);
        }
        if (!containsScalarSubquery(tree)) {
            FullGrammerExpressionAnalysis analysis = analyze(tree, defaultQualifier);
            return analysis.withTransform("CASE_WHEN", "CONTROL");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            appendSources(aliases, columns, seen, controlSources(tree.getChild(index), defaultQualifier));
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private FullGrammerExpressionAnalysis scalarSubqueryPredicateOperandControl(
            ParseTree subquery,
            String defaultQualifier
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        FullGrammerExpressionAnalysis selected = scalarSubquerySelectedAnalysis(subquery, defaultQualifier);
        appendSources(aliases, columns, seen, selected);
        appendSources(aliases, columns, seen, scalarSubqueryContextAnalysis(subquery, defaultQualifier));
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
        List<LineageTransformType> candidates = new ArrayList<>();
        candidates.add(LineageTransformType.FUNCTION_CALL);
        for (String transform : transforms) {
            candidates.add(LineageTransformType.valueOf(transform));
        }
        return LineageTransformClassifier.dominant(
                candidates.toArray(LineageTransformType[]::new)).name();
    }

    private ParseTree unwrapSingleChildContexts(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && current.getChildCount() == 1 && !(current instanceof TerminalNode)) {
            current = current.getChild(0);
        }
        return current;
    }
}
