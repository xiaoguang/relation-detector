package com.relationdetector.mysql.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalyzer;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter.ConditionalParts;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter.QueryParts;

/** Shared MySQL expression semantics; generated context access stays in version adapters. */
public class MySqlFullGrammarExpressionAnalyzer extends FullGrammarExpressionAnalyzer {
    private final MySqlExpressionContextAdapter contexts;

    public MySqlFullGrammarExpressionAnalyzer(MySqlExpressionContextAdapter contexts) {
        super(contexts);
        this.contexts = contexts;
    }

    @Override
    protected boolean preferAggregateArgumentSourcesOnly() {
        return false;
    }

    @Override
    public FullGrammarExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        ParseTree runtimeFunction = runtimeDateFunction(unwrapSingleChildContexts(expression));
        if (runtimeFunction != null) {
            FullGrammarExpressionAnalysis runtimeAnalysis =
                    analyzeRuntimeDateFunction(runtimeFunction, defaultQualifier);
            return runtimeAnalysis.withTransform(
                    MySqlTransformSemantics.valueTransform(
                            runtimeAnalysis.transformType(), containsArithmeticExpression(expression)),
                    runtimeAnalysis.flowKind());
        }
        FullGrammarExpressionAnalysis analysis = super.analyze(expression, defaultQualifier);
        ParseTree nestedRuntimeFunction = firstRuntimeDateFunction(expression);
        if (nestedRuntimeFunction != null && analysis.hasSources()) {
            FullGrammarExpressionAnalysis nested =
                    analyzeRuntimeDateFunction(nestedRuntimeFunction, defaultQualifier);
            LineageTransformType transform = LineageTransformClassifier.dominant(
                    LineageTransformType.valueOf(analysis.transformType()),
                    LineageTransformType.valueOf(nested.transformType()));
            analysis = analysis.withTransform(transform.name(), analysis.flowKind());
        }
        return analysis.withTransform(
                MySqlTransformSemantics.valueTransform(
                        analysis.transformType(), containsArithmeticExpression(expression)),
                analysis.flowKind());
    }

    @Override
    public List<FullGrammarExpressionAnalysis> writeAnalyses(
            ParseTree expression, String defaultQualifier
    ) {
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>();
        FullGrammarExpressionAnalysis value = valueOnlyAnalysis(expression, defaultQualifier);
        if (value.hasSources() && "CASE_WHEN".equals(value.transformType())
                && containsAggregateFunction(expression)) {
            value = value.withTransform("AGGREGATE", "VALUE");
        }
        if (value.hasSources()) {
            result.add(value);
        }
        List<FullGrammarExpressionAnalysis> controls = new ArrayList<>(
                nestedConditionalControlAnalyses(expression, defaultQualifier));
        FullGrammarExpressionAnalysis scalarControl =
                scalarSubqueryControlAnalysis(expression, defaultQualifier);
        if (scalarControl.hasSources()) {
            controls.add(scalarControl);
        }
        FullGrammarExpressionAnalysis mergedControl = MySqlTransformSemantics.mergeSameRole(
                controls, "CASE_WHEN", "CONTROL");
        if (mergedControl.hasSources()) {
            result.add(mergedControl);
        }
        return result;
    }

    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return containsScalarSubquery(expression) || containsConditionalExpression(expression);
    }

    private boolean containsArithmeticExpression(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (contexts.isArithmeticExpression(tree)) {
            return true;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (containsArithmeticExpression(child)) {
                return true;
            }
        }
        return false;
    }

    private FullGrammarExpressionAnalysis valueOnlyAnalysis(ParseTree tree, String defaultQualifier) {
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
        if (!containsScalarSubquery(tree) && !containsConditionalExpression(tree)) {
            return analyze(tree, defaultQualifier);
        }
        FullGrammarExpressionAnalysis full = analyze(tree, defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            appendSources(aliases, columns, seen,
                    valueOnlyAnalysis(child, defaultQualifier));
        }
        String transform = "CASE_WHEN".equals(full.transformType()) && containsAggregateFunction(tree)
                ? "AGGREGATE"
                : full.transformType();
        return new FullGrammarExpressionAnalysis(aliases, columns, transform, "VALUE");
    }

    private FullGrammarExpressionAnalysis conditionalValueOnlyAnalysis(
            ParseTree tree, String defaultQualifier
    ) {
        List<ParseTree> values = contexts.conditionalParts(
                unwrapSingleChildContexts(tree)).values();
        if (values.isEmpty()) {
            return caseWriteAnalyses(tree, defaultQualifier).stream()
                    .filter(analysis -> "VALUE".equals(analysis.flowKind()))
                    .findFirst()
                    .orElseGet(() -> empty("VALUE"));
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree value : values) {
            appendSources(aliases, columns, seen, valueOnlyAnalysis(value, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "VALUE");
    }

    private boolean containsConditionalExpression(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isTopLevelConditionalExpression(tree)) {
            return true;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (containsConditionalExpression(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTopLevelConditionalExpression(ParseTree tree) {
        return tree != null && contexts.conditionalParts(tree).conditional();
    }

    private List<FullGrammarExpressionAnalysis> nestedConditionalControlAnalyses(
            ParseTree tree, String defaultQualifier
    ) {
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>();
        collectNestedConditionalControlAnalyses(tree, defaultQualifier, result);
        return result;
    }

    private void collectNestedConditionalControlAnalyses(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammarExpressionAnalysis> result
    ) {
        if (tree == null) {
            return;
        }
        if (isTopLevelConditionalExpression(tree)) {
            FullGrammarExpressionAnalysis control = conditionalControlOnlyAnalysis(tree, defaultQualifier);
            if (control.hasSources()) {
                result.add(control);
            }
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectNestedConditionalControlAnalyses(child, defaultQualifier, result);
        }
    }

    private FullGrammarExpressionAnalysis conditionalControlOnlyAnalysis(
            ParseTree tree, String defaultQualifier
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectConditionalControlSources(tree, defaultQualifier, aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void collectConditionalControlSources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        ConditionalParts parts = contexts.conditionalParts(unwrapSingleChildContexts(tree));
        for (ParseTree control : parts.controls()) {
            appendSources(aliases, columns, seen, analyze(control, defaultQualifier));
        }
        for (ParseTree value : parts.values()) {
            collectNestedConditionalControlSources(value, defaultQualifier, aliases, columns, seen);
        }
    }

    private void collectNestedConditionalControlSources(
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
            collectConditionalControlSources(tree, defaultQualifier, aliases, columns, seen);
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectNestedConditionalControlSources(
                    child, defaultQualifier, aliases, columns, seen);
        }
    }

    private FullGrammarExpressionAnalysis scalarSubquerySelectedAnalysis(
            ParseTree subquery, String defaultQualifier
    ) {
        QueryParts query = contexts.firstQuery(subquery);
        if (query == null || query.projections().size() != 1) {
            return empty("VALUE");
        }
        return selectedProjectionValueAnalysis(
                query.projections().get(0),
                contexts.singleProjectionQualifier(query.fromClause(), defaultQualifier));
    }

    private FullGrammarExpressionAnalysis selectedProjectionValueAnalysis(
            ParseTree expression, String defaultQualifier
    ) {
        FullGrammarExpressionAnalysis selected = analyze(expression, defaultQualifier);
        if (!containsAggregateFunction(expression)) {
            return selected.withTransform(selected.transformType(), "VALUE");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectAggregateArgumentSources(expression, defaultQualifier, aliases, columns, seen);
        removeConditionalControlOnlySources(expression, defaultQualifier, aliases, columns);
        String transform = "CUMULATIVE".equals(selected.transformType())
                ? "CUMULATIVE"
                : "AGGREGATE";
        if (aliases.isEmpty()) {
            return new FullGrammarExpressionAnalysis(List.of(), List.of(), transform, "VALUE");
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, transform, "VALUE");
    }

    private void removeConditionalControlOnlySources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns
    ) {
        Set<String> controlKeys = new LinkedHashSet<>();
        Set<String> valueKeys = new LinkedHashSet<>();
        collectConditionalRoleKeys(tree, defaultQualifier, controlKeys, valueKeys);
        for (int index = aliases.size() - 1; index >= 0; index--) {
            String key = aliases.get(index) + "\u0000" + columns.get(index);
            if (controlKeys.contains(key) && !valueKeys.contains(key)) {
                aliases.remove(index);
                columns.remove(index);
            }
        }
    }

    private void collectConditionalRoleKeys(
            ParseTree tree,
            String defaultQualifier,
            Set<String> controlKeys,
            Set<String> valueKeys
    ) {
        if (tree == null) {
            return;
        }
        if (isTopLevelConditionalExpression(tree)) {
            addKeys(controlKeys, conditionalControlOnlyAnalysis(tree, defaultQualifier));
            addKeys(valueKeys, conditionalValueOnlyAnalysis(tree, defaultQualifier));
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectConditionalRoleKeys(child, defaultQualifier, controlKeys, valueKeys);
        }
    }

    private void addKeys(Set<String> keys, FullGrammarExpressionAnalysis analysis) {
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
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
            for (ParseTree child : contexts.typedChildren(tree)) {
                appendSources(aliases, columns, seen,
                        valueOnlyAnalysis(child, defaultQualifier));
            }
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectAggregateArgumentSources(
                    child, defaultQualifier, aliases, columns, seen);
        }
    }

    private boolean containsAggregateFunction(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isAggregateFunctionContext(tree)) {
            return true;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (containsAggregateFunction(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAggregateFunctionContext(ParseTree tree) {
        if (contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) {
            return true;
        }
        return contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.FUNCTION_CALL)
                && contexts.functionName(tree)
                .map(name -> LineageTransformClassifier.classifyFunction(name, false))
                .orElse(LineageTransformType.FUNCTION_CALL)
                == LineageTransformType.AGGREGATE;
    }

    private FullGrammarExpressionAnalysis scalarSubqueryControlAnalysis(
            ParseTree tree, String defaultQualifier
    ) {
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
        for (ParseTree child : contexts.typedChildren(tree)) {
            appendSources(aliases, columns, seen,
                    scalarSubqueryControlAnalysis(child, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private FullGrammarExpressionAnalysis scalarSubqueryContextAnalysis(
            ParseTree subquery, String defaultQualifier
    ) {
        QueryParts query = contexts.firstQuery(subquery);
        if (query == null) {
            return empty("CONTROL");
        }
        String scalarQualifier = contexts.singleProjectionQualifier(query.fromClause(), defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree joinPredicate : query.joinPredicates()) {
            appendSources(aliases, columns, seen, controlSources(joinPredicate, scalarQualifier));
        }
        appendControlSource(aliases, columns, seen, query.wherePredicate(), scalarQualifier);
        appendControlSource(aliases, columns, seen, query.groupBy(), scalarQualifier);
        appendControlSource(aliases, columns, seen, query.havingPredicate(), scalarQualifier);
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void appendControlSource(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            ParseTree tree,
            String defaultQualifier
    ) {
        if (tree != null) {
            appendSources(aliases, columns, seen, controlSources(tree, defaultQualifier));
        }
    }

    private FullGrammarExpressionAnalysis controlSources(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return empty("CONTROL");
        }
        ParseTree unwrapped = unwrapSingleChildContexts(tree);
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubqueryPredicateOperandControl(unwrapped, defaultQualifier);
        }
        if (!containsScalarSubquery(tree)) {
            FullGrammarExpressionAnalysis analysis = analyze(tree, defaultQualifier);
            return analysis.withTransform("CASE_WHEN", "CONTROL");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            appendSources(aliases, columns, seen,
                    controlSources(child, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private FullGrammarExpressionAnalysis scalarSubqueryPredicateOperandControl(
            ParseTree subquery, String defaultQualifier
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        appendSources(aliases, columns, seen,
                scalarSubquerySelectedAnalysis(subquery, defaultQualifier));
        appendSources(aliases, columns, seen,
                scalarSubqueryContextAnalysis(subquery, defaultQualifier));
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private boolean containsScalarSubquery(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isScalarSubqueryBoundary(unwrapSingleChildContexts(tree))) {
            return true;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (containsScalarSubquery(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean isScalarSubqueryBoundary(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY)) {
            return true;
        }
        return contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.QUERY_BOUNDARY)
                && contexts.firstQuery(tree) != null
                && !hasColumnOutsideQueryBoundary(tree);
    }

    private boolean hasColumnOutsideQueryBoundary(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.QUERY_BOUNDARY)
                && contexts.firstQuery(tree) != null) {
            return false;
        }
        if (contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.COLUMN_REFERENCE)) {
            return true;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (hasColumnOutsideQueryBoundary(child)) {
                return true;
            }
        }
        return false;
    }

    private ParseTree runtimeDateFunction(ParseTree tree) {
        return tree != null && contexts.runtimeDateArguments(tree).isPresent() ? tree : null;
    }

    private ParseTree firstRuntimeDateFunction(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (runtimeDateFunction(tree) != null) {
            return tree;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            ParseTree found = firstRuntimeDateFunction(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private FullGrammarExpressionAnalysis analyzeRuntimeDateFunction(
            ParseTree runtimeFunction, String defaultQualifier
    ) {
        Optional<List<ParseTree>> arguments = contexts.runtimeDateArguments(runtimeFunction);
        if (arguments.isEmpty()) {
            return empty("VALUE");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> transforms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree argument : arguments.get()) {
            FullGrammarExpressionAnalysis analysis = super.analyze(argument, defaultQualifier);
            transforms.add(analysis.transformType());
            appendSources(aliases, columns, seen, analysis);
        }
        List<LineageTransformType> candidates = new ArrayList<>();
        candidates.add(LineageTransformType.FUNCTION_CALL);
        transforms.stream().map(LineageTransformType::valueOf).forEach(candidates::add);
        return new FullGrammarExpressionAnalysis(
                aliases,
                columns,
                LineageTransformClassifier.dominant(
                        candidates.toArray(LineageTransformType[]::new)).name(),
                "VALUE");
    }

    private void appendSources(
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

    private FullGrammarExpressionAnalysis empty(String flowKind) {
        return new FullGrammarExpressionAnalysis(
                List.of(), List.of(), "UNKNOWN_EXPRESSION", flowKind);
    }

    private ParseTree unwrapSingleChildContexts(ParseTree tree) {
        ParseTree current = tree;
        while (current != null) {
            List<ParseTree> children = contexts.typedChildren(current);
            if (children.size() != 1) {
                break;
            }
            current = children.get(0);
        }
        return current;
    }
}
