package com.relationdetector.core.fullgrammar;

import java.util.function.Predicate;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic;

/**
 *
 * Classifies typed expression operations without collecting physical columns.
 */
final class FullGrammarTransformClassifier {
    private final FullGrammarParseTreeAdapter adapter;
    private final DialectFunctionSemanticRegistry functions;
    private final Predicate<String> coalesceFunction;

    FullGrammarTransformClassifier(
            FullGrammarParseTreeAdapter adapter,
            DialectFunctionSemanticRegistry functions,
            Predicate<String> coalesceFunction
    ) {
        this.adapter = adapter;
        this.functions = functions;
        this.coalesceFunction = coalesceFunction;
    }

    String classify(ParseTree expression) {
        TransformFlags flags = new TransformFlags();
        visit(expression, flags);
        if (flags.caseExpression) return "CASE_WHEN";
        if (flags.cumulative) return "CUMULATIVE";
        if (flags.aggregate) return "AGGREGATE";
        if (flags.window) return "WINDOW_DERIVED";
        if (flags.coalesce) return "COALESCE";
        if (flags.concatFormat) return "CONCAT_FORMAT";
        if (flags.arithmetic) return "ARITHMETIC";
        if (flags.functionCall) return "FUNCTION_CALL";
        return "DIRECT";
    }

    boolean disqualifiesDirectRelationship(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (adapter.isNonColumnValue(tree)
                || adapter.operatorSemantic(tree) != OperatorSemantic.NONE
                || adapter.functionName(tree).isPresent()
                || adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.FUNCTION_CALL)
                || adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)
                || adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)
                || adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            return true;
        }
        for (ParseTree child : adapter.typedChildren(tree)) {
            if (disqualifiesDirectRelationship(child)) {
                return true;
            }
        }
        return false;
    }

    private void visit(ParseTree tree, TransformFlags flags) {
        if (tree == null) {
            return;
        }
        if (adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)) {
            flags.caseExpression = true;
        }
        if (adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) {
            flags.aggregate = true;
            flags.functionCall = true;
        }
        if (adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            flags.window = true;
            flags.functionCall = true;
        }
        if (adapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CONCAT_EXPRESSION)) {
            flags.concatFormat = true;
            flags.functionCall = true;
        }
        adapter.functionName(tree).ifPresent(name -> classifyFunction(name, flags));
        OperatorSemantic operator = adapter.operatorSemantic(tree);
        if (operator == OperatorSemantic.ARITHMETIC) flags.arithmetic = true;
        if (operator == OperatorSemantic.CONCAT_FORMAT) flags.concatFormat = true;
        if (operator == OperatorSemantic.CUMULATIVE) flags.cumulative = true;
        if (operator == OperatorSemantic.BOOLEAN_EXPRESSION) flags.functionCall = true;
        for (ParseTree child : adapter.typedChildren(tree)) {
            visit(child, flags);
        }
    }

    private void classifyFunction(String name, TransformFlags flags) {
        flags.functionCall = true;
        LineageTransformType transform = coalesceFunction.test(name)
                ? LineageTransformType.COALESCE
                : functions.classify(name);
        if (transform == LineageTransformType.AGGREGATE) flags.aggregate = true;
        if (transform == LineageTransformType.WINDOW_DERIVED) flags.window = true;
        if (transform == LineageTransformType.COALESCE) flags.coalesce = true;
        if (transform == LineageTransformType.CONCAT_FORMAT) flags.concatFormat = true;
    }

    private static final class TransformFlags {
        private boolean caseExpression;
        private boolean aggregate;
        private boolean window;
        private boolean coalesce;
        private boolean concatFormat;
        private boolean arithmetic;
        private boolean functionCall;
        private boolean cumulative;
    }
}
