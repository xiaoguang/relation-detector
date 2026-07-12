package com.relationdetector.oracle.fullgrammar.common;

import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.Role;
import com.relationdetector.oracle.fullgrammar.common.OracleFullGrammarParseTreeAdapter.OperatorSemantic;

/** Oracle expression transform classification, independent of collector state. */
final class OracleExpressionTransformSupport extends OracleFullGrammarParseTreeSupport {
    OracleExpressionTransformSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammarParseTreeAdapter adapter
    ) {
        super(core, adapter);
    }

    LineageTransformType transformFor(ParseTree tree) {
        LineageTransformType functionTransform = nestedFunctionTransform(tree);
        if (functionTransform == LineageTransformType.CUMULATIVE
                || functionTransform == LineageTransformType.AGGREGATE
                || functionTransform == LineageTransformType.WINDOW_DERIVED) {
            return functionTransform;
        }
        if (hasRole(tree, Role.CASE_EXPRESSION)) {
            return LineageTransformType.CASE_WHEN;
        }
        if (containsConcatenationOperator(tree) || functionTransform == LineageTransformType.CONCAT_FORMAT) {
            return LineageTransformType.CONCAT_FORMAT;
        }
        if (containsArithmeticOperator(tree)) {
            return LineageTransformType.ARITHMETIC;
        }
        return functionTransform != LineageTransformType.DIRECT
                ? functionTransform
                : LineageTransformType.DIRECT;
    }

    LineageTransformType dominantValueTransform(
            LineageTransformType left,
            LineageTransformType right
    ) {
        if (left == LineageTransformType.CUMULATIVE || right == LineageTransformType.CUMULATIVE) {
            return LineageTransformType.CUMULATIVE;
        }
        if (left == LineageTransformType.AGGREGATE || right == LineageTransformType.AGGREGATE) {
            return LineageTransformType.AGGREGATE;
        }
        return LineageTransformClassifier.dominant(left, right);
    }

    private LineageTransformType nestedFunctionTransform(ParseTree tree) {
        if (tree == null) {
            return LineageTransformType.DIRECT;
        }
        LineageTransformType transform = LineageTransformType.DIRECT;
        String functionName = functionName(tree).orElse("");
        if (!functionName.isBlank()) {
            transform = LineageTransformClassifier.classifyFunction(functionName, false, Map.of(
                    "nvl", LineageTransformType.COALESCE,
                    "listagg", LineageTransformType.CONCAT_FORMAT));
        }
        for (ParseTree child : typedChildren(tree)) {
            transform = dominantValueTransform(transform, nestedFunctionTransform(child));
        }
        return transform;
    }

    private boolean containsArithmeticOperator(ParseTree tree) {
        if (tree == null) return false;
        if (operatorSemantic(tree) == OperatorSemantic.ARITHMETIC) return true;
        for (ParseTree child : typedChildren(tree)) {
            if (containsArithmeticOperator(child)) return true;
        }
        return false;
    }

    private boolean containsConcatenationOperator(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (operatorSemantic(tree) == OperatorSemantic.CONCAT_FORMAT) return true;
        for (ParseTree child : typedChildren(tree)) {
            if (containsConcatenationOperator(child)) {
                return true;
            }
        }
        return false;
    }

}
