package com.relationdetector.core.fullgrammer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

public abstract class FullGrammerExpressionAnalyzer {
    private final Set<String> nonColumnIdentifiers = new LinkedHashSet<>();

    public void ignoreIdentifier(String identifier) {
        String clean = cleanIdentifier(identifier);
        if (!clean.isBlank()) {
            nonColumnIdentifiers.add(clean.toLowerCase(Locale.ROOT));
        }
    }

    public FullGrammerExpressionAnalysis analyze(ParseTree expression) {
        return analyze(expression, "");
    }

    public FullGrammerExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        Set<ExpressionColumn> columns = new LinkedHashSet<>();
        collectColumns(expression, defaultQualifier, columns);
        String transform = transform(expression);
        String flowKind = transform.equals("CASE_WHEN") ? "CONTROL" : "VALUE";
        return new FullGrammerExpressionAnalysis(
                columns.stream().map(ExpressionColumn::qualifier).toList(),
                columns.stream().map(ExpressionColumn::column).toList(),
                transform,
                flowKind);
    }

    public boolean isTopLevelCaseExpression(ParseTree expression) {
        ParseTree unwrapped = unwrapSingleChildContexts(expression);
        return unwrapped != null && isCaseContext(unwrapped);
    }

    public List<FullGrammerExpressionAnalysis> caseExpressionAnalyses(ParseTree expression, String defaultQualifier) {
        java.util.ArrayList<FullGrammerExpressionAnalysis> analyses = new java.util.ArrayList<>();
        collectCaseExpressionAnalyses(expression, defaultQualifier, analyses);
        return analyses;
    }

    private void collectCaseExpressionAnalyses(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammerExpressionAnalysis> result
    ) {
        if (tree == null) {
            return;
        }
        if (isCaseContext(tree)) {
            FullGrammerExpressionAnalysis analysis = analyze(tree, defaultQualifier);
            if (analysis.hasSources()) {
                result.add(analysis);
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectCaseExpressionAnalyses(tree.getChild(index), defaultQualifier, result);
        }
    }

    private ParseTree unwrapSingleChildContexts(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && current.getChildCount() == 1 && !(current instanceof TerminalNode)) {
            current = current.getChild(0);
        }
        return current;
    }

    private boolean isCaseContext(ParseTree tree) {
        return tree != null && tree.getClass().getSimpleName().contains("Case");
    }

    private void collectColumns(ParseTree tree, String defaultQualifier, Set<ExpressionColumn> result) {
        if (tree == null) {
            return;
        }
        String contextName = tree.getClass().getSimpleName();
        if (contextName.equals("ColumnrefContext") || contextName.equals("ColumnRefContext")
                || contextName.equals("SimpleExprColumnRefContext")) {
            ExpressionColumn column = expressionColumn(tree.getText(), defaultQualifier);
            if (column != null) {
                result.add(column);
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectColumns(tree.getChild(index), defaultQualifier, result);
        }
    }

    private ExpressionColumn expressionColumn(String raw, String defaultQualifier) {
        String text = cleanIdentifier(raw);
        if (text.isBlank() || text.contains("(")) {
            return null;
        }
        if (!defaultQualifier.isBlank() && text.startsWith("@")) {
            return null;
        }
        List<String> parts = splitQualifiedName(text);
        if (parts.size() >= 2) {
            String qualifier = cleanIdentifier(parts.get(parts.size() - 2));
            String column = cleanIdentifier(parts.get(parts.size() - 1));
            if (isIdentifier(qualifier) && isIdentifier(column)
                    && !isIgnoredIdentifier(qualifier) && !isIgnoredIdentifier(column)
                    && !isNonColumnIdentifier(qualifier) && !isNonColumnIdentifier(column)) {
                return new ExpressionColumn(qualifier, column);
            }
        }
        if (!defaultQualifier.isBlank() && isIdentifier(text)
                && !isIgnoredIdentifier(text) && !isLiteralLikeIdentifier(text)
                && !isNonColumnIdentifier(text)) {
            return new ExpressionColumn(defaultQualifier, text);
        }
        return null;
    }

    private String transform(ParseTree expression) {
        TransformFlags flags = new TransformFlags();
        visitTransform(expression, flags);
        if (flags.caseExpression) {
            return "CASE_WHEN";
        }
        if (flags.cumulative) {
            return "CUMULATIVE";
        }
        if (flags.aggregate) {
            return "AGGREGATE";
        }
        if (flags.window) {
            return "WINDOW_DERIVED";
        }
        if (flags.coalesce) {
            return "COALESCE";
        }
        if (flags.concatFormat) {
            return "CONCAT_FORMAT";
        }
        if (flags.arithmetic) {
            return "ARITHMETIC";
        }
        if (flags.functionCall) {
            return "FUNCTION_CALL";
        }
        return "DIRECT";
    }

    private void visitTransform(ParseTree tree, TransformFlags flags) {
        if (tree == null) {
            return;
        }
        String contextName = tree.getClass().getSimpleName();
        if (contextName.contains("Case")) {
            flags.caseExpression = true;
        }
        if (contextName.contains("Sum")) {
            flags.aggregate = true;
            flags.functionCall = true;
        }
        if (contextName.contains("Window") || contextName.contains("Over_clause")) {
            flags.window = true;
            flags.functionCall = true;
        }
        if (contextName.contains("Concat")) {
            flags.concatFormat = true;
            flags.functionCall = true;
        }
        if (contextName.equals("FunctionCallContext") || contextName.equals("Func_applicationContext")) {
            classifyFunctionName(firstLeafText(tree), flags);
        }
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            String lower = text.toLowerCase(Locale.ROOT);
            if (isAggregateFunction(lower) || isWindowFunction(lower) || lower.equals("coalesce")
                    || isConcatOrFormatFunction(lower)) {
                classifyFunctionName(lower, flags);
            }
            if (isArithmeticOperator(text)) {
                flags.arithmetic = true;
            }
            if (isCumulativeToken(text, lower)) {
                flags.cumulative = true;
            }
            if (lower.equals("over")) {
                flags.window = true;
            }
            if (text.equals("||")) {
                flags.concatFormat = true;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            visitTransform(tree.getChild(index), flags);
        }
    }

    private void classifyFunctionName(String raw, TransformFlags flags) {
        String name = cleanIdentifier(raw).toLowerCase(Locale.ROOT);
        if (name.isBlank()) {
            return;
        }
        flags.functionCall = true;
        if (isAggregateFunction(name)) {
            flags.aggregate = true;
        } else if (isWindowFunction(name)) {
            flags.window = true;
        } else if (name.equals("coalesce")) {
            flags.coalesce = true;
        } else if (isConcatOrFormatFunction(name)) {
            flags.concatFormat = true;
        }
    }

    private String firstLeafText(ParseTree tree) {
        if (tree == null) {
            return "";
        }
        if (tree.getChildCount() == 0) {
            return tree.getText();
        }
        return firstLeafText(tree.getChild(0));
    }

    private boolean isAggregateFunction(String value) {
        return value.equals("sum")
                || value.equals("avg")
                || value.equals("count")
                || value.equals("min")
                || value.equals("max");
    }

    private boolean isCumulativeToken(String text, String lower) {
        return text.equals(":=");
    }

    private boolean isWindowFunction(String value) {
        return value.equals("row_number")
                || value.equals("rank")
                || value.equals("dense_rank")
                || value.equals("ntile")
                || value.equals("lag")
                || value.equals("lead");
    }

    private boolean isConcatOrFormatFunction(String value) {
        return value.equals("concat")
                || value.equals("format")
                || value.equals("to_char")
                || value.equals("group_concat")
                || value.equals("json_array_append")
                || value.equals("array_append");
    }

    private boolean isArithmeticOperator(String value) {
        return value.equals("+") || value.equals("-") || value.equals("*") || value.equals("/");
    }

    public String cleanIdentifier(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        if (clean.indexOf('.') >= 0) {
            return splitQualifiedName(clean).stream()
                    .map(this::cleanIdentifier)
                    .filter(part -> !part.isBlank())
                    .collect(java.util.stream.Collectors.joining("."));
        }
        while ((clean.startsWith("`") && clean.endsWith("`"))
                || (clean.startsWith("\"") && clean.endsWith("\""))
                || (clean.startsWith("[") && clean.endsWith("]"))) {
            if (clean.length() < 2) {
                return "";
            }
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean;
    }

    private List<String> splitQualifiedName(String text) {
        java.util.ArrayList<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '.') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private boolean isIdentifier(String text) {
        return text != null
                && !text.isBlank()
                && text.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_' || ch == '$');
    }

    private boolean isLiteralLikeIdentifier(String value) {
        if (value.isBlank()) {
            return true;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return Character.isDigit(value.charAt(0))
                || normalized.equals("true")
                || normalized.equals("false")
                || normalized.equals("null")
                || normalized.equals("current_date")
                || normalized.equals("current_timestamp");
    }

    private boolean isNonColumnIdentifier(String value) {
        return nonColumnIdentifiers.contains(cleanIdentifier(value).toLowerCase(Locale.ROOT));
    }

    private boolean isIgnoredIdentifier(String value) {
        String token = cleanIdentifier(value).toLowerCase(Locale.ROOT);
        return token.isBlank()
                || token.equals("select")
                || token.equals("distinct")
                || token.equals("from")
                || token.equals("set")
                || token.equals("where")
                || token.equals("and")
                || token.equals("or")
                || token.equals("on")
                || token.equals("is")
                || token.equals("like")
                || token.equals("in")
                || token.equals("between")
                || token.equals("exists")
                || token.equals("join")
                || token.equals("inner")
                || token.equals("left")
                || token.equals("right")
                || token.equals("full")
                || token.equals("outer")
                || token.equals("cross")
                || token.equals("natural")
                || token.equals("straight_join")
                || token.equals("as")
                || token.equals("using")
                || token.equals("with")
                || token.equals("recursive")
                || token.equals("materialized")
                || token.equals("not")
                || token.equals("temporary")
                || token.equals("temp")
                || token.equals("table")
                || token.equals("insert")
                || token.equals("into")
                || token.equals("update")
                || token.equals("delete")
                || token.equals("merge")
                || token.equals("do")
                || token.equals("while")
                || token.equals("loop")
                || token.equals("if")
                || token.equals("values")
                || token.equals("case")
                || token.equals("when")
                || token.equals("then")
                || token.equals("else")
                || token.equals("end")
                || token.equals("over")
                || token.equals("partition")
                || token.equals("by")
                || token.equals("order");
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

    private record ExpressionColumn(String qualifier, String column) {
    }
}
