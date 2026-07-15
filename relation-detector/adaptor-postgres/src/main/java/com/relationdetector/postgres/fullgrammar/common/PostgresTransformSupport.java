package com.relationdetector.postgres.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.DialectFunctionSemanticRegistry;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/** PostgreSQL operation and typed-role classification independent of source collection. */
final class PostgresTransformSupport {
    private final FullGrammarParseTreeAdapter adapter;
    private final DialectFunctionSemanticRegistry functions;

    PostgresTransformSupport(
            FullGrammarParseTreeAdapter adapter,
            DialectFunctionSemanticRegistry functions
    ) {
        this.adapter = adapter;
        this.functions = functions;
    }

    boolean containsAggregateFunction(ParseTree tree) {
        if (tree == null) return false;
        if (adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) return true;
        if (adapter.functionName(tree).map(functions::classify)
                .filter(LineageTransformType.AGGREGATE::equals).isPresent()) return true;
        for (ParseTree child : adapter.typedChildren(tree)) {
            if (containsAggregateFunction(child)) return true;
        }
        return false;
    }

    boolean containsRole(ParseTree tree, FullGrammarParseTreeAdapter.Role role) {
        if (tree == null) return false;
        if (adapter.hasRole(tree, role)) return true;
        for (ParseTree child : adapter.typedChildren(tree)) {
            if (containsRole(child, role)) return true;
        }
        return false;
    }

    FullGrammarExpressionAnalysis applyRoutineTransform(
            ParseTree expression,
            FullGrammarExpressionAnalysis analysis
    ) {
        if ("AGGREGATE".equals(analysis.transformType())
                && containsRole(expression, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            analysis = analysis.withTransform("CUMULATIVE", analysis.flowKind());
        }
        OperatorSemantic operator = topLevelOperator(expression);
        if (operator == OperatorSemantic.CONCAT_FORMAT) {
            return analysis.withTransform("CONCAT_FORMAT", analysis.flowKind());
        }
        if (operator == OperatorSemantic.ARITHMETIC
                || containsOperatorOutsideCase(expression, OperatorSemantic.ARITHMETIC)) {
            LineageTransformType dominant = LineageTransformClassifier.dominant(
                    LineageTransformType.ARITHMETIC,
                    LineageTransformType.valueOf(analysis.transformType()));
            return analysis.withTransform(dominant.name(), analysis.flowKind());
        }
        return analysis;
    }

    private boolean containsOperatorOutsideCase(ParseTree tree, OperatorSemantic expected) {
        if (tree == null) return false;
        if (adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)) return false;
        if (adapter.operatorSemantic(tree) == expected) return true;
        for (ParseTree child : adapter.typedChildren(tree)) {
            if (containsOperatorOutsideCase(child, expected)) return true;
        }
        return false;
    }

    private OperatorSemantic topLevelOperator(ParseTree tree) {
        ParseTree current = tree;
        while (current != null) {
            OperatorSemantic operator = adapter.operatorSemantic(current);
            if (operator != OperatorSemantic.NONE) return operator;
            List<ParseTree> children = adapter.typedChildren(current);
            if (children.size() != 1) break;
            current = children.get(0);
        }
        return OperatorSemantic.NONE;
    }
}
