package com.relationdetector.mysql.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalyzer;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter.QueryParts;

/** Shared MySQL expression semantics; generated context access stays in version adapters. */
public class MySqlFullGrammarExpressionAnalyzer extends FullGrammarExpressionAnalyzer {
    private final MySqlExpressionContextAdapter contexts;
    private final MySqlConditionalExpressionSupport conditionals;
    private final MySqlRuntimeFunctionSupport runtimeFunctions;
    private final MySqlAggregateExpressionSupport aggregates;

    public MySqlFullGrammarExpressionAnalyzer(MySqlExpressionContextAdapter contexts) {
        super(contexts);
        this.contexts = contexts;
        this.conditionals = new MySqlConditionalExpressionSupport(this, contexts);
        this.runtimeFunctions = new MySqlRuntimeFunctionSupport(this, contexts);
        this.aggregates = new MySqlAggregateExpressionSupport(this, contexts);
    }

    @Override
    protected boolean preferAggregateArgumentSourcesOnly() {
        return false;
    }

    @Override
    public FullGrammarExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        ParseTree runtimeFunction = runtimeFunctions.direct(unwrap(expression));
        if (runtimeFunction != null) {
            FullGrammarExpressionAnalysis runtimeAnalysis =
                    runtimeFunctions.analyze(runtimeFunction, defaultQualifier);
            return runtimeAnalysis.withTransform(
                    MySqlTransformSemantics.valueTransform(
                            runtimeAnalysis.transformType(), containsArithmeticExpression(expression)),
                    runtimeAnalysis.flowKind());
        }
        FullGrammarExpressionAnalysis analysis = super.analyze(expression, defaultQualifier);
        ParseTree nestedRuntimeFunction = runtimeFunctions.first(expression);
        if (nestedRuntimeFunction != null && analysis.hasSources()) {
            FullGrammarExpressionAnalysis nested =
                    runtimeFunctions.analyze(nestedRuntimeFunction, defaultQualifier);
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
                && aggregates.contains(expression)) {
            value = value.withTransform("AGGREGATE", "VALUE");
        }
        if (value.hasSources()) {
            result.add(value);
        }
        List<FullGrammarExpressionAnalysis> controls = new ArrayList<>(
                conditionals.nestedControls(expression, defaultQualifier));
        FullGrammarExpressionAnalysis mergedControl = MySqlTransformSemantics.mergeSameRole(
                controls, "CASE_WHEN", "CONTROL");
        if (mergedControl.hasSources()) {
            result.add(mergedControl);
        }
        FullGrammarExpressionAnalysis scalarControl =
                scalarSubqueryControlAnalysis(expression, defaultQualifier);
        if (scalarControl.hasSources()) {
            result.add(scalarControl.withTransform("DIRECT", "CONTROL"));
        }
        FullGrammarExpressionAnalysis groupingControl =
                scalarSubqueryGroupingControlAnalysis(expression, defaultQualifier);
        if (groupingControl.hasSources()) {
            result.add(groupingControl);
        }
        FullGrammarExpressionAnalysis windowControl = windowControlAnalysis(expression, defaultQualifier);
        if (windowControl.hasSources()) {
            result.add(windowControl);
        }
        return result;
    }

    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return containsScalarSubquery(expression)
                || conditionals.contains(expression)
                || containsWindowControl(expression);
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

    FullGrammarExpressionAnalysis valueOnlyAnalysis(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return emptyAnalysis("VALUE");
        }
        ParseTree unwrapped = unwrap(tree);
        if (conditionals.isTopLevel(unwrapped)) {
            return conditionals.valueAnalysis(unwrapped, defaultQualifier);
        }
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubquerySelectedAnalysis(unwrapped, defaultQualifier);
        }
        if (containsWindowControl(tree)) {
            return valueWithoutWindowControl(tree, defaultQualifier);
        }
        if (!containsScalarSubquery(tree) && !conditionals.contains(tree)) {
            return analyze(tree, defaultQualifier);
        }
        FullGrammarExpressionAnalysis full = analyze(tree, defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                    valueOnlyAnalysis(child, defaultQualifier));
        }
        String transform = "CASE_WHEN".equals(full.transformType()) && aggregates.contains(tree)
                ? "AGGREGATE"
                : full.transformType();
        return new FullGrammarExpressionAnalysis(aliases, columns, transform, "VALUE");
    }

    private FullGrammarExpressionAnalysis valueWithoutWindowControl(
            ParseTree tree, String defaultQualifier
    ) {
        if (tree == null || contexts.isWindowControlContainer(tree)) {
            return emptyAnalysis("VALUE");
        }
        if (!containsWindowControl(tree)) {
            return valueOnlyAnalysis(tree, defaultQualifier);
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                    valueWithoutWindowControl(child, defaultQualifier));
        }
        FullGrammarExpressionAnalysis full = analyze(tree, defaultQualifier);
        String transform = "WINDOW_DERIVED".equals(full.transformType())
                ? (aggregates.contains(tree) ? "AGGREGATE" : "FUNCTION_CALL")
                : full.transformType();
        return new FullGrammarExpressionAnalysis(aliases, columns, transform, "VALUE");
    }

    private FullGrammarExpressionAnalysis windowControlAnalysis(
            ParseTree tree, String defaultQualifier
    ) {
        if (tree == null) {
            return emptyAnalysis("CONTROL");
        }
        List<ParseTree> controls = contexts.windowControlExpressions(tree);
        if (!controls.isEmpty()) {
            List<String> aliases = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (ParseTree control : controls) {
                MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen, analyze(control, defaultQualifier));
            }
            return new FullGrammarExpressionAnalysis(
                    aliases, columns, "WINDOW_DERIVED", "CONTROL");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                    windowControlAnalysis(child, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(
                aliases, columns, "WINDOW_DERIVED", "CONTROL");
    }

    private boolean containsWindowControl(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (contexts.isWindowControlContainer(tree)
                || !contexts.windowControlExpressions(tree).isEmpty()) {
            return true;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (containsWindowControl(child)) {
                return true;
            }
        }
        return false;
    }

    private FullGrammarExpressionAnalysis scalarSubquerySelectedAnalysis(
            ParseTree subquery, String defaultQualifier
    ) {
        QueryParts query = contexts.firstQuery(subquery);
        if (query == null || query.projections().size() != 1) {
            return emptyAnalysis("VALUE");
        }
        return selectedProjectionValueAnalysis(
                query.projections().get(0),
                contexts.singleProjectionQualifier(query.fromClause(), defaultQualifier));
    }

    private FullGrammarExpressionAnalysis selectedProjectionValueAnalysis(
            ParseTree expression, String defaultQualifier
    ) {
        FullGrammarExpressionAnalysis selected = analyze(expression, defaultQualifier);
        if (!aggregates.contains(expression)) {
            return selected.withTransform(selected.transformType(), "VALUE");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        aggregates.collectArguments(expression, defaultQualifier, aliases, columns, seen);
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
        conditionals.collectRoleKeys(tree, defaultQualifier, controlKeys, valueKeys);
        for (int index = aliases.size() - 1; index >= 0; index--) {
            String key = aliases.get(index) + "\u0000" + columns.get(index);
            if (controlKeys.contains(key) && !valueKeys.contains(key)) {
                aliases.remove(index);
                columns.remove(index);
            }
        }
    }

    private FullGrammarExpressionAnalysis scalarSubqueryControlAnalysis(
            ParseTree tree, String defaultQualifier
    ) {
        if (tree == null) {
            return emptyAnalysis("CONTROL");
        }
        ParseTree unwrapped = unwrap(tree);
        if (isScalarSubqueryBoundary(unwrapped)) {
            return scalarSubqueryContextAnalysis(unwrapped, defaultQualifier);
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                    scalarSubqueryControlAnalysis(child, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "DIRECT", "CONTROL");
    }

    private FullGrammarExpressionAnalysis scalarSubqueryGroupingControlAnalysis(
            ParseTree tree, String defaultQualifier
    ) {
        if (tree == null) {
            return emptyAnalysis("CONTROL");
        }
        ParseTree unwrapped = unwrap(tree);
        if (isScalarSubqueryBoundary(unwrapped)) {
            QueryParts query = contexts.firstQuery(unwrapped);
            if (query == null || query.groupBy() == null) {
                return emptyAnalysis("CONTROL");
            }
            String qualifier = contexts.singleProjectionQualifier(query.fromClause(), defaultQualifier);
            return controlSources(query.groupBy(), qualifier).withTransform("AGGREGATE", "CONTROL");
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree child : contexts.typedChildren(tree)) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                    scalarSubqueryGroupingControlAnalysis(child, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "AGGREGATE", "CONTROL");
    }

    private FullGrammarExpressionAnalysis scalarSubqueryContextAnalysis(
            ParseTree subquery, String defaultQualifier
    ) {
        QueryParts query = contexts.firstQuery(subquery);
        if (query == null) {
            return emptyAnalysis("CONTROL");
        }
        String scalarQualifier = contexts.singleProjectionQualifier(query.fromClause(), defaultQualifier);
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree joinPredicate : query.joinPredicates()) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen, controlSources(joinPredicate, scalarQualifier));
        }
        appendControlSource(aliases, columns, seen, query.wherePredicate(), scalarQualifier);
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
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen, controlSources(tree, defaultQualifier));
        }
    }

    private FullGrammarExpressionAnalysis controlSources(ParseTree tree, String defaultQualifier) {
        if (tree == null) {
            return emptyAnalysis("CONTROL");
        }
        ParseTree unwrapped = unwrap(tree);
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
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
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
        MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                scalarSubquerySelectedAnalysis(subquery, defaultQualifier));
        MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                scalarSubqueryContextAnalysis(subquery, defaultQualifier));
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private boolean containsScalarSubquery(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isScalarSubqueryBoundary(unwrap(tree))) {
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

    FullGrammarExpressionAnalysis emptyAnalysis(String flowKind) {
        return new FullGrammarExpressionAnalysis(
                List.of(), List.of(), "UNKNOWN_EXPRESSION", flowKind);
    }

    ParseTree unwrap(ParseTree tree) {
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

    FullGrammarExpressionAnalysis analyzeBase(ParseTree tree, String defaultQualifier) {
        return super.analyze(tree, defaultQualifier);
    }
}
