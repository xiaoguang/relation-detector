package com.relationdetector.postgres.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalyzer;

/**
 * Shared PostgreSQL full-grammar expression analyzer.
 *
 * <p>CN: 当前 PostgreSQL v16/v17/v18 表达式规则共用 core 的 parse-tree analyzer。
 * 如果某个 PostgreSQL major 需要特殊函数、operator 或 window 语义，应在对应版本包中
 * 新增子类或 hook，而不是复制整套 analyzer。
 *
 * <p>EN: Shared PostgreSQL full-grammar expression analyzer. PostgreSQL
 * v16/v17/v18 currently share the core parse-tree analyzer. If one major
 * version needs special function, operator, or window semantics, add a
 * version-specific subclass or hook instead of copying the whole analyzer.
 */
public class PostgresExpressionAnalyzer extends FullGrammarExpressionAnalyzer {
    private final boolean routineSql;

    public PostgresExpressionAnalyzer(com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter adapter) {
        this(adapter, false);
    }

    @Override
    protected com.relationdetector.core.fullgrammar.DialectFunctionSemanticRegistry functionRegistry() {
        var standard = com.relationdetector.core.fullgrammar.DialectFunctionSemanticRegistry.standard();
        return routineSql ? standard.withExtensions(java.util.Map.of(
                "to_char", com.relationdetector.contracts.Enums.LineageTransformType.FUNCTION_CALL))
                : standard;
    }

    public PostgresExpressionAnalyzer(
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter adapter,
            boolean routineSql
    ) {
        super(adapter);
        this.routineSql = routineSql;
    }

    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return scalarSubquery(expression) != null
                || containsAggregateFunction(expression)
                || containsRole(expression,
                com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION);
    }

    @Override
    public List<FullGrammarExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        FullGrammarExpressionAnalysis analysis = routineTransform(
                expression, analyze(expression, defaultQualifier));
        List<String> sourceAliases = new ArrayList<>();
        List<String> sourceColumns = new ArrayList<>();
        Set<String> sourceKeys = new LinkedHashSet<>();
        if (containsScalarBoundary(expression)) {
            collectValueSourcesReplacingScalars(
                    expression, defaultQualifier, sourceAliases, sourceColumns, sourceKeys);
        } else {
            append(sourceAliases, sourceColumns, sourceKeys, analysis);
        }
        FullGrammarExpressionAnalysis scalarControl = nestedScalarControls(expression, defaultQualifier);
        FullGrammarExpressionAnalysis caseControl = nestedCaseControls(expression, defaultQualifier);
        FullGrammarExpressionAnalysis groupingControl = nestedGroupingControls(expression, defaultQualifier);
        FullGrammarExpressionAnalysis windowControl = nestedWindowControls(expression, defaultQualifier);
        Set<String> controlKeys = new LinkedHashSet<>(keys(scalarControl));
        controlKeys.addAll(keys(caseControl));
        controlKeys.addAll(keys(groupingControl));
        controlKeys.addAll(keys(windowControl));
        Set<String> explicitValueKeys = nestedCaseValueKeys(expression, defaultQualifier);
        explicitValueKeys.addAll(nonControlValueKeys(expression, defaultQualifier));
        FullGrammarExpressionAnalysis scalarValues = nestedScalarProjectionValues(
                expression, defaultQualifier);
        Set<String> scalarValueKeys = keys(scalarValues);
        explicitValueKeys.addAll(scalarValueKeys);
        List<String> valueAliases = new ArrayList<>();
        List<String> valueColumns = new ArrayList<>();
        int count = Math.min(sourceAliases.size(), sourceColumns.size());
        for (int index = 0; index < count; index++) {
            String key = sourceAliases.get(index) + "\u0000" + sourceColumns.get(index);
            if (!controlKeys.contains(key) || explicitValueKeys.contains(key)) {
                valueAliases.add(sourceAliases.get(index));
                valueColumns.add(sourceColumns.get(index));
            }
        }
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>(2);
        if (!valueColumns.isEmpty()) {
            String valueTransform = analysis.transformType();
            if (containsAggregateFunction(expression)) {
                valueTransform = containsRole(expression,
                        com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)
                        ? "CUMULATIVE" : "AGGREGATE";
            }
            Set<String> valueKeys = new LinkedHashSet<>();
            for (int index = 0; index < valueColumns.size(); index++) {
                valueKeys.add(valueAliases.get(index) + "\u0000" + valueColumns.get(index));
            }
            if ("CASE_WHEN".equals(valueTransform)
                    && scalarValues.hasSources()
                    && scalarValueKeys.containsAll(valueKeys)) {
                valueTransform = scalarValues.transformType();
            }
            result.add(new FullGrammarExpressionAnalysis(
                    valueAliases, valueColumns, valueTransform, "VALUE"));
        }
        if (scalarControl.hasSources()) {
            result.add(scalarControl.withTransform("DIRECT", "CONTROL"));
        }
        if (caseControl.hasSources()) {
            result.add(caseControl.withTransform("CASE_WHEN", "CONTROL"));
        }
        if (groupingControl.hasSources()) {
            result.add(groupingControl.withTransform("AGGREGATE", "CONTROL"));
        }
        if (windowControl.hasSources()) {
            result.add(windowControl.withTransform("WINDOW_DERIVED", "CONTROL"));
        }
        return List.copyOf(result);
    }

    private boolean containsAggregateFunction(ParseTree tree) {
        if (tree == null) return false;
        if (parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) {
            return true;
        }
        if (parseTreeAdapter().functionName(tree)
                .map(functionRegistry()::classify)
                .filter(com.relationdetector.contracts.Enums.LineageTransformType.AGGREGATE::equals)
                .isPresent()) {
            return true;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            if (containsAggregateFunction(child)) return true;
        }
        return false;
    }

    private FullGrammarExpressionAnalysis routineTransform(
            ParseTree expression,
            FullGrammarExpressionAnalysis analysis
    ) {
        if ("AGGREGATE".equals(analysis.transformType())
                && containsRole(expression,
                        com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            analysis = analysis.withTransform("CUMULATIVE", analysis.flowKind());
        }
        var operator = topLevelOperator(expression);
        if (operator == com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic
                .CONCAT_FORMAT) {
            return analysis.withTransform("CONCAT_FORMAT", analysis.flowKind());
        }
        if (operator == com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic
                .ARITHMETIC
                || containsOperatorOutsideCase(expression,
                        com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic
                                .ARITHMETIC)) {
            var dominant = com.relationdetector.core.lineage.LineageTransformClassifier.dominant(
                    com.relationdetector.contracts.Enums.LineageTransformType.ARITHMETIC,
                    com.relationdetector.contracts.Enums.LineageTransformType.valueOf(
                            analysis.transformType()));
            return analysis.withTransform(dominant.name(), analysis.flowKind());
        }
        return analysis;
    }

    private boolean containsOperatorOutsideCase(
            ParseTree tree,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic expected
    ) {
        if (tree == null) return false;
        if (parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)) {
            return false;
        }
        if (parseTreeAdapter().operatorSemantic(tree) == expected) return true;
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            if (containsOperatorOutsideCase(child, expected)) return true;
        }
        return false;
    }

    private boolean containsRole(
            ParseTree tree,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role role
    ) {
        if (tree == null) return false;
        if (parseTreeAdapter().hasRole(tree, role)) return true;
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            if (containsRole(child, role)) return true;
        }
        return false;
    }

    private com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic topLevelOperator(
            ParseTree tree
    ) {
        ParseTree current = tree;
        while (current != null) {
            var operator = parseTreeAdapter().operatorSemantic(current);
            if (operator != com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic.NONE) {
                return operator;
            }
            List<ParseTree> children = parseTreeAdapter().typedChildren(current);
            if (children.size() != 1) break;
            current = children.get(0);
        }
        return com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic.NONE;
    }

    private void collectValueSourcesReplacingScalars(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        if (isScalarBoundary(tree)) {
            append(aliases, columns, seen, scalarProjection(tree, defaultQualifier));
            return;
        }
        if (parseTreeAdapter().directColumn(tree).isPresent()) {
            append(aliases, columns, seen, analyze(tree, defaultQualifier));
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectValueSourcesReplacingScalars(
                    child, defaultQualifier, aliases, columns, seen);
        }
    }

    private FullGrammarExpressionAnalysis scalarProjection(ParseTree scalar, String defaultQualifier) {
        ParseTree projection = parseTreeAdapter().selectProjectionExpressions(scalar).stream()
                .findFirst().orElse(null);
        if (projection == null) {
            return new FullGrammarExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", "VALUE");
        }
        String qualifier = scalarQualifier(scalar, defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        com.relationdetector.contracts.Enums.LineageTransformType transform =
                com.relationdetector.contracts.Enums.LineageTransformType.DIRECT;
        for (FullGrammarExpressionAnalysis projected : writeAnalyses(projection, qualifier)) {
            if (!"VALUE".equals(projected.flowKind())) continue;
            append(aliases, columns, seen, projected);
            transform = com.relationdetector.core.lineage.LineageTransformClassifier.dominant(
                    transform,
                    com.relationdetector.contracts.Enums.LineageTransformType.valueOf(
                            projected.transformType()));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, transform.name(), "VALUE");
    }

    private FullGrammarExpressionAnalysis scalarControl(ParseTree scalar, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<ParseTree> controls = new ArrayList<>();
        collectDirectScopeContexts(scalar, scalar, controls);
        String qualifier = scalarQualifier(scalar, defaultQualifier);
        for (ParseTree control : controls) {
            append(aliases, columns, seen, analyze(control, qualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "DIRECT", "CONTROL");
    }

    private String scalarQualifier(ParseTree scalar, String defaultQualifier) {
        ParseTree from = parseTreeAdapter().firstDescendant(
                scalar, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.FROM_CLAUSE);
        ParseTree table = from == null ? null : parseTreeAdapter().firstDescendant(
                from, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.TABLE_SOURCE_ITEM);
        return table == null ? defaultQualifier : parseTreeAdapter().rowsetBinding(table)
                .map(com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.RowsetBinding::qualifier)
                .filter(value -> !value.isBlank())
                .orElse(defaultQualifier);
    }

    private boolean containsScalarBoundary(ParseTree tree) {
        if (tree == null) return false;
        if (isScalarBoundary(tree)) return true;
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            if (containsScalarBoundary(child)) return true;
        }
        return false;
    }

    private FullGrammarExpressionAnalysis nestedScalarControls(ParseTree tree, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectNestedScalarControls(tree, defaultQualifier, aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, "DIRECT", "CONTROL");
    }

    private void collectNestedScalarControls(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        if (isScalarBoundary(tree)) {
            append(aliases, columns, seen, scalarControl(tree, defaultQualifier));
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectNestedScalarControls(child, defaultQualifier, aliases, columns, seen);
        }
    }

    private FullGrammarExpressionAnalysis nestedCaseControls(ParseTree tree, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectNestedCaseControls(tree, defaultQualifier, aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void collectNestedCaseControls(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        var caseParts = parseTreeAdapter().caseParts(tree);
        if (caseParts.conditional()) {
            for (ParseTree control : caseParts.controls()) {
                FullGrammarExpressionAnalysis analysis = analyze(control, defaultQualifier);
                append(aliases, columns, seen, analysis);
            }
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectNestedCaseControls(child, defaultQualifier, aliases, columns, seen);
        }
    }

    private FullGrammarExpressionAnalysis nestedGroupingControls(ParseTree tree, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectNestedGroupingControls(tree, defaultQualifier, aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, "AGGREGATE", "CONTROL");
    }

    private void collectNestedGroupingControls(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        if (isScalarBoundary(tree)) {
            String qualifier = scalarQualifier(tree, defaultQualifier);
            List<ParseTree> groupings = new ArrayList<>();
            collectDirectRoleContexts(tree, tree,
                    com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.GROUPING_SCOPE,
                    groupings);
            for (ParseTree grouping : groupings) {
                append(aliases, columns, seen, analyze(grouping, qualifier));
            }
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectNestedGroupingControls(child, defaultQualifier, aliases, columns, seen);
        }
    }

    private FullGrammarExpressionAnalysis nestedWindowControls(ParseTree tree, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectRoleAnalyses(tree, defaultQualifier,
                com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.WINDOW_CONTROL_SCOPE,
                aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, "WINDOW_DERIVED", "CONTROL");
    }

    private void collectRoleAnalyses(
            ParseTree tree,
            String defaultQualifier,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role role,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        if (parseTreeAdapter().hasRole(tree, role)) {
            append(aliases, columns, seen, analyze(tree, defaultQualifier));
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectRoleAnalyses(child, defaultQualifier, role, aliases, columns, seen);
        }
    }

    private Set<String> nestedCaseValueKeys(ParseTree tree, String defaultQualifier) {
        Set<String> result = new LinkedHashSet<>();
        collectNestedCaseValueKeys(tree, defaultQualifier, result);
        return result;
    }

    private Set<String> nonControlValueKeys(ParseTree tree, String defaultQualifier) {
        Set<String> result = new LinkedHashSet<>();
        collectNonControlValueKeys(tree, defaultQualifier, result);
        return result;
    }

    private void collectNonControlValueKeys(ParseTree tree, String defaultQualifier, Set<String> result) {
        if (tree == null || isScalarBoundary(tree)
                || parseTreeAdapter().hasRole(
                        tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CONTROL_SCOPE)
                || parseTreeAdapter().hasRole(
                        tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.GROUPING_SCOPE)
                || parseTreeAdapter().hasRole(
                        tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.WINDOW_CONTROL_SCOPE)) {
            return;
        }
        var caseParts = parseTreeAdapter().caseParts(tree);
        if (caseParts.conditional()) {
            for (ParseTree value : caseParts.values()) {
                collectNonControlValueKeys(value, defaultQualifier, result);
            }
            return;
        }
        if (parseTreeAdapter().directColumn(tree).isPresent()) {
            result.addAll(keys(analyze(tree, defaultQualifier)));
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectNonControlValueKeys(child, defaultQualifier, result);
        }
    }

    private FullGrammarExpressionAnalysis nestedScalarProjectionValues(
            ParseTree tree,
            String defaultQualifier
    ) {
        List<FullGrammarExpressionAnalysis> projections = new ArrayList<>();
        collectNestedScalarProjectionValues(tree, defaultQualifier, projections);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        com.relationdetector.contracts.Enums.LineageTransformType transform =
                com.relationdetector.contracts.Enums.LineageTransformType.DIRECT;
        for (FullGrammarExpressionAnalysis projection : projections) {
            append(aliases, columns, seen, projection);
            transform = com.relationdetector.core.lineage.LineageTransformClassifier.dominant(
                    transform,
                    com.relationdetector.contracts.Enums.LineageTransformType.valueOf(
                            projection.transformType()));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, transform.name(), "VALUE");
    }

    private void collectNestedScalarProjectionValues(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammarExpressionAnalysis> result
    ) {
        if (tree == null) return;
        if (isScalarBoundary(tree)) {
            result.add(scalarProjection(tree, defaultQualifier));
            return;
        }
        var caseParts = parseTreeAdapter().caseParts(tree);
        if (caseParts.conditional()) {
            for (ParseTree value : caseParts.values()) {
                collectNestedScalarProjectionValues(value, defaultQualifier, result);
            }
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectNestedScalarProjectionValues(child, defaultQualifier, result);
        }
    }

    private void collectNestedCaseValueKeys(ParseTree tree, String defaultQualifier, Set<String> result) {
        if (tree == null) return;
        var caseParts = parseTreeAdapter().caseParts(tree);
        if (caseParts.conditional()) {
            for (ParseTree value : caseParts.values()) {
                FullGrammarExpressionAnalysis analysis = analyze(value, defaultQualifier);
                result.addAll(keys(analysis));
            }
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectNestedCaseValueKeys(child, defaultQualifier, result);
        }
    }

    private Set<String> keys(FullGrammarExpressionAnalysis analysis) {
        Set<String> result = new LinkedHashSet<>();
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            result.add(analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index));
        }
        return result;
    }

    private void collectDirectScopeContexts(
            ParseTree root,
            ParseTree tree,
            List<ParseTree> result
    ) {
        collectDirectRoleContexts(root, tree,
                com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CONTROL_SCOPE, result);
    }

    private void collectDirectRoleContexts(
            ParseTree root,
            ParseTree tree,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role role,
            List<ParseTree> result
    ) {
        if (tree == null) {
            return;
        }
        if (tree != root && isScalarBoundary(tree)) {
            return;
        }
        if (parseTreeAdapter().hasRole(tree, role)) {
            result.add(tree);
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectDirectRoleContexts(root, child, role, result);
        }
    }

    private ParseTree scalarSubquery(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (isScalarBoundary(tree)) {
            return tree;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            ParseTree found = scalarSubquery(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isScalarBoundary(ParseTree tree) {
        return parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY);
    }

    private void append(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammarExpressionAnalysis analysis
    ) {
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            String alias = analysis.sourceAliases().get(index);
            String column = analysis.sourceColumns().get(index);
            if (seen.add(alias + "\u0000" + column)) {
                aliases.add(alias);
                columns.add(column);
            }
        }
    }

}
