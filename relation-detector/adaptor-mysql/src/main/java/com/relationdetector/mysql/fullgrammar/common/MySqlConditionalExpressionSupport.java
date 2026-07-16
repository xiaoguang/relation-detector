package com.relationdetector.mysql.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.mysql.fullgrammar.common.MySqlExpressionContextAdapter.ConditionalParts;

/**
 *
 * Separates CASE/IF VALUE and CONTROL roles from MySQL write orchestration.
 */
final class MySqlConditionalExpressionSupport {
    private final MySqlFullGrammarExpressionAnalyzer analyzer;
    private final MySqlExpressionContextAdapter contexts;

    MySqlConditionalExpressionSupport(
            MySqlFullGrammarExpressionAnalyzer analyzer,
            MySqlExpressionContextAdapter contexts
    ) {
        this.analyzer = analyzer;
        this.contexts = contexts;
    }

    FullGrammarExpressionAnalysis valueAnalysis(ParseTree tree, String defaultQualifier) {
        List<ParseTree> values = contexts.conditionalParts(analyzer.unwrap(tree)).values();
        if (values.isEmpty()) {
            return analyzer.caseWriteAnalyses(tree, defaultQualifier).stream()
                    .filter(analysis -> "VALUE".equals(analysis.flowKind()))
                    .findFirst().orElseGet(() -> analyzer.emptyAnalysis("VALUE"));
        }
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree value : values) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen, analyzer.valueOnlyAnalysis(value, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "VALUE");
    }

    boolean contains(ParseTree tree) {
        if (tree == null) return false;
        if (isTopLevel(tree)) return true;
        for (ParseTree child : contexts.typedChildren(tree)) {
            if (contains(child)) return true;
        }
        return false;
    }

    boolean isTopLevel(ParseTree tree) {
        return tree != null && contexts.conditionalParts(tree).conditional();
    }

    List<FullGrammarExpressionAnalysis> nestedControls(ParseTree tree, String defaultQualifier) {
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>();
        collectNestedControls(tree, defaultQualifier, result);
        return result;
    }

    FullGrammarExpressionAnalysis controlAnalysis(ParseTree tree, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectControlSources(tree, defaultQualifier, aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, "DIRECT", "CONTROL");
    }

    void collectRoleKeys(
            ParseTree tree,
            String defaultQualifier,
            Set<String> controlKeys,
            Set<String> valueKeys
    ) {
        if (tree == null) return;
        if (isTopLevel(tree)) {
            addKeys(controlKeys, controlAnalysis(tree, defaultQualifier));
            addKeys(valueKeys, valueAnalysis(tree, defaultQualifier));
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectRoleKeys(child, defaultQualifier, controlKeys, valueKeys);
        }
    }

    private void collectNestedControls(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammarExpressionAnalysis> result
    ) {
        if (tree == null) return;
        if (isTopLevel(tree)) {
            FullGrammarExpressionAnalysis control = controlAnalysis(tree, defaultQualifier);
            if (control.hasSources()) result.add(control);
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectNestedControls(child, defaultQualifier, result);
        }
    }

    private void collectControlSources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        ConditionalParts parts = contexts.conditionalParts(analyzer.unwrap(tree));
        for (ParseTree control : parts.controls()) {
            MySqlExpressionAnalysisAccumulator.append(aliases, columns, seen, analyzer.analyze(control, defaultQualifier));
        }
        for (ParseTree value : parts.values()) {
            collectNestedControlSources(value, defaultQualifier, aliases, columns, seen);
        }
    }

    private void collectNestedControlSources(
            ParseTree tree,
            String defaultQualifier,
            List<String> aliases,
            List<String> columns,
            Set<String> seen
    ) {
        if (tree == null) return;
        if (isTopLevel(tree)) {
            collectControlSources(tree, defaultQualifier, aliases, columns, seen);
            return;
        }
        for (ParseTree child : contexts.typedChildren(tree)) {
            collectNestedControlSources(child, defaultQualifier, aliases, columns, seen);
        }
    }

    private void addKeys(Set<String> keys, FullGrammarExpressionAnalysis analysis) {
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            keys.add(analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index));
        }
    }
}
