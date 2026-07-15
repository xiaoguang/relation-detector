package com.relationdetector.mysql.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** Handles typed MySQL runtime date-function arguments. */
final class MySqlRuntimeFunctionSupport {
    private final MySqlFullGrammarExpressionAnalyzer analyzer;
    private final MySqlExpressionContextAdapter contexts;

    MySqlRuntimeFunctionSupport(
            MySqlFullGrammarExpressionAnalyzer analyzer,
            MySqlExpressionContextAdapter contexts
    ) {
        this.analyzer = analyzer;
        this.contexts = contexts;
    }

    ParseTree direct(ParseTree tree) {
        return tree != null && contexts.runtimeDateArguments(tree).isPresent() ? tree : null;
    }

    ParseTree first(ParseTree tree) {
        if (tree == null) return null;
        if (direct(tree) != null) return tree;
        for (ParseTree child : contexts.typedChildren(tree)) {
            ParseTree found = first(child);
            if (found != null) return found;
        }
        return null;
    }

    FullGrammarExpressionAnalysis analyze(ParseTree runtimeFunction, String defaultQualifier) {
        Optional<List<ParseTree>> arguments = contexts.runtimeDateArguments(runtimeFunction);
        if (arguments.isEmpty()) return analyzer.emptyAnalysis("VALUE");
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> transforms = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree argument : arguments.get()) {
            FullGrammarExpressionAnalysis analysis = analyzer.analyzeBase(argument, defaultQualifier);
            transforms.add(analysis.transformType());
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen, analysis);
        }
        List<LineageTransformType> candidates = new ArrayList<>();
        candidates.add(LineageTransformType.FUNCTION_CALL);
        transforms.stream().map(LineageTransformType::valueOf).forEach(candidates::add);
        return new FullGrammarExpressionAnalysis(aliases, columns,
                LineageTransformClassifier.dominant(candidates.toArray(LineageTransformType[]::new)).name(),
                "VALUE");
    }
}
