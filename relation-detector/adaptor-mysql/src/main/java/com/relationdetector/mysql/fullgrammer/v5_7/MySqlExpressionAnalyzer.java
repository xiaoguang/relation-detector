package com.relationdetector.mysql.fullgrammer.v5_7;

import com.relationdetector.core.fullgrammer.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.ExprContext;
import com.relationdetector.mysql.fullgrammer.v5_7.MySqlFullGrammerParser.RuntimeFunctionCallContext;

/**
 * MySQL full-grammer expression analyzer.
 *
 * <p>CN: 目前复用 core 的 parse-tree expression analyzer，并保留 MySQL 专属扩展点。
 * 如果 MySQL grammar 需要特殊函数或变量语义，应在这里覆盖，而不是改 PostgreSQL。
 *
 * <p>EN: MySQL full-grammer expression analyzer. It currently reuses the core
 * parse-tree expression analyzer and remains the MySQL-specific extension point
 * for future function or variable semantics.
 */
final class MySqlExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
    @Override
    protected boolean preferAggregateArgumentSourcesOnly() {
        return false;
    }

    @Override
    public FullGrammerExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        RuntimeFunctionCallContext runtimeFunction = runtimeDateFunction(unwrapSingleChildContexts(expression));
        if (runtimeFunction == null) {
            return super.analyze(expression, defaultQualifier);
        }
        return analyzeRuntimeDateFunction(runtimeFunction, defaultQualifier);
    }

    private RuntimeFunctionCallContext runtimeDateFunction(ParseTree tree) {
        if (!(tree instanceof RuntimeFunctionCallContext runtimeFunction)) {
            return null;
        }
        if (runtimeFunction.DATE_ADD_SYMBOL() != null
                || runtimeFunction.DATE_SUB_SYMBOL() != null
                || runtimeFunction.ADDDATE_SYMBOL() != null
                || runtimeFunction.SUBDATE_SYMBOL() != null
                || runtimeFunction.DATE_SYMBOL() != null) {
            return runtimeFunction;
        }
        return null;
    }

    private FullGrammerExpressionAnalysis analyzeRuntimeDateFunction(
            RuntimeFunctionCallContext runtimeFunction,
            String defaultQualifier
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        List<String> transforms = new ArrayList<>();
        Set<String> seenSources = new LinkedHashSet<>();
        for (ExprContext expr : runtimeFunction.expr()) {
            FullGrammerExpressionAnalysis analysis = super.analyze(expr, defaultQualifier);
            transforms.add(analysis.transformType());
            int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String key = analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index);
                if (seenSources.add(key)) {
                    aliases.add(analysis.sourceAliases().get(index));
                    columns.add(analysis.sourceColumns().get(index));
                }
            }
        }
        return new FullGrammerExpressionAnalysis(
                aliases,
                columns,
                dominantTransform(transforms),
                "VALUE");
    }

    private String dominantTransform(List<String> transforms) {
        String dominant = "FUNCTION_CALL";
        for (String transform : transforms) {
            if (priority(transform) > priority(dominant)) {
                dominant = transform;
            }
        }
        return dominant;
    }

    private int priority(String transform) {
        return switch (transform) {
            case "CASE_WHEN" -> 8;
            case "CUMULATIVE" -> 7;
            case "AGGREGATE" -> 6;
            case "WINDOW_DERIVED" -> 5;
            case "COALESCE" -> 4;
            case "CONCAT_FORMAT" -> 3;
            case "ARITHMETIC" -> 2;
            case "FUNCTION_CALL" -> 1;
            default -> 0;
        };
    }

    private ParseTree unwrapSingleChildContexts(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && current.getChildCount() == 1 && !(current instanceof TerminalNode)) {
            current = current.getChild(0);
        }
        return current;
    }
}
