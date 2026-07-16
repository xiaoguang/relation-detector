package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 *
 * Collects typed MySQL aggregate arguments independently of CASE/control roles.
 */
final class MySqlAggregateExpressionSupport {
    private final MySqlFullGrammarExpressionAnalyzer analyzer;
    private final MySqlExpressionContextAdapter contexts;

    MySqlAggregateExpressionSupport(
            MySqlFullGrammarExpressionAnalyzer analyzer,
            MySqlExpressionContextAdapter contexts
    ) {
        this.analyzer = analyzer;
        this.contexts = contexts;
    }

    void collectArguments(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        if (isAggregate(tree)) {
            for (ParseTree child : contexts.typedChildren(tree)) {
                MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen,
                        analyzer.valueOnlyAnalysis(child, defaultQualifier));
            }
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectArguments(child, defaultQualifier, aliases, columns, seen);
        }
    }

    boolean contains(ParseTree tree) {
        if (tree == null) return false;
        if (isAggregate(tree)) return true;
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (contains(child)) return true;
        }
        return false;
    }

    private boolean isAggregate(ParseTree tree) {
        if (contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) return true;
        return contexts.hasRole(tree, FullGrammarParseTreeAdapter.Role.FUNCTION_CALL)
                && contexts.functionName(tree)
                .map(name -> LineageTransformClassifier.classifyFunction(name, false))
                .orElse(LineageTransformType.FUNCTION_CALL) == LineageTransformType.AGGREGATE;
    }
}
