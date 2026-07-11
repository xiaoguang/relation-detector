package com.relationdetector.oracle.fullgrammer.common;

import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.lineage.LineageTransformClassifier;
import com.relationdetector.oracle.fullgrammer.common.OracleFullGrammerParseTreeAdapter.Role;

/** Oracle expression transform classification, independent of collector state. */
final class OracleExpressionTransformSupport extends OracleFullGrammerParseTreeSupport {
    OracleExpressionTransformSupport(
            OracleSqlEventVisitorCore core,
            OracleFullGrammerParseTreeAdapter adapter
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
        String functionName = typedFunctionName(tree);
        if (!functionName.isBlank()) {
            transform = LineageTransformClassifier.classifyFunction(functionName, false, Map.of(
                    "nvl", LineageTransformType.COALESCE,
                    "listagg", LineageTransformType.CONCAT_FORMAT));
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            transform = dominantValueTransform(transform, nestedFunctionTransform(tree.getChild(index)));
        }
        return transform;
    }

    private String typedFunctionName(ParseTree tree) {
        boolean functionContext = hasRole(tree, Role.FUNCTION_EXPRESSION);
        if (!functionContext && hasRole(tree, Role.GENERAL_ELEMENT)) {
            for (ParserRuleContext part : children(tree, "general_element_part")) {
                if (!children(part, "function_argument").isEmpty()) {
                    functionContext = true;
                    break;
                }
            }
        }
        if (!functionContext) {
            return "";
        }
        String text = core.clean(tree.getText());
        int paren = text.indexOf('(');
        if (paren <= 0) {
            return "";
        }
        String prefix = text.substring(0, paren);
        int dot = prefix.lastIndexOf('.');
        return core.clean(dot < 0 ? prefix : prefix.substring(dot + 1));
    }

    private boolean containsArithmeticOperator(ParseTree tree) {
        return containsOperator(tree, "+")
                || containsOperator(tree, "-")
                || containsOperator(tree, "*")
                || containsOperator(tree, "/");
    }

    private boolean containsConcatenationOperator(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (hasRole(tree, Role.CONCATENATION)) {
            int directBars = 0;
            for (int index = 0; index < tree.getChildCount(); index++) {
                if (tree.getChild(index) instanceof TerminalNode terminal && "|".equals(terminal.getText())) {
                    directBars++;
                }
            }
            if (directBars >= 2) {
                return true;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsConcatenationOperator(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsOperator(ParseTree tree, String operator) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof TerminalNode terminal && operator.equals(terminal.getText())) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsOperator(tree.getChild(index), operator)) {
                return true;
            }
        }
        return false;
    }
}
