package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * full-grammer 表达式分析基础类。
 *
 * <p>CN: 方言 visitor 把 grammar expression context 传入这里，产出 sourceAliases、
 * sourceColumns、transformType 和 flowKind。当前实现递归 parse tree 节点与 terminal
 * leaves，不读取整段 SQL 文本做正则结构判断；最终 transform 含义仍由 core lineage
 * 语义层消费。
 *
 * <p>EN: Base expression analyzer for full-grammer profiles. Dialect visitors
 * pass grammar expression contexts here and receive source aliases, source
 * columns, transform type, and flow kind. The implementation walks parse-tree
 * nodes and terminal leaves rather than regex-matching full SQL text; final
 * transform semantics are consumed by the core lineage layer.
 */
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

    /**
     * 分析一个表达式 context，提取字段来源和粗粒度 transform。
     *
     * <p>EN: Analyzes one expression context and extracts source columns plus a
     * coarse transform classification.
     */
    public FullGrammerExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        Set<ExpressionColumn> columns = new LinkedHashSet<>();
        String transform = transform(expression);
        collectExpressionSourceColumns(expression, defaultQualifier, transform, columns);
        String flowKind = transform.equals("CASE_WHEN") ? "CONTROL" : "VALUE";
        return new FullGrammerExpressionAnalysis(
                columns.stream().map(ExpressionColumn::qualifier).toList(),
                columns.stream().map(ExpressionColumn::column).toList(),
                transform,
                flowKind);
    }

    /**
     * 分析关系谓词中的“裸列表达式”。
     *
     * <p>CN: JOIN/EXISTS/IN 关系只能从裸列或裸列 tuple 产生。拼接、算术、函数、
     * 聚合、literal 等表达式仍可参与 lineage，但不能成为 relationship endpoint。
     *
     * <p>EN: Analyzes a relation-predicate column expression. JOIN/EXISTS/IN
     * relationships are emitted only for bare columns or bare-column tuples.
     * Concatenation, arithmetic, functions, aggregates, and literals may still
     * feed lineage, but must not become relationship endpoints.
     */
    public FullGrammerExpressionAnalysis analyzeRelationColumnExpression(ParseTree expression, String defaultQualifier) {
        if (hasRelationExpressionDisqualifier(expression)) {
            return emptyAnalysis();
        }
        FullGrammerExpressionAnalysis analysis = analyze(expression, defaultQualifier);
        if (!"DIRECT".equals(analysis.transformType())) {
            return emptyAnalysis();
        }
        return analysis;
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

    public List<FullGrammerExpressionAnalysis> caseWriteAnalyses(ParseTree expression, String defaultQualifier) {
        ParseTree caseNode = singleCaseContext(expression);
        if (!isCaseContext(caseNode)) {
            return List.of();
        }
        List<ParseTree> controlExpressions = new ArrayList<>();
        List<ParseTree> valueExpressions = new ArrayList<>();
        boolean elseValue = false;
        boolean sawSwitchSection = false;
        for (int index = 0; index < caseNode.getChildCount(); index++) {
            ParseTree child = caseNode.getChild(index);
            String terminal = terminalText(child);
            if ("CASE".equals(terminal) || "END".equals(terminal)) {
                continue;
            }
            if ("ELSE".equals(terminal)) {
                elseValue = true;
                continue;
            }
            if (elseValue) {
                valueExpressions.add(child);
                continue;
            }
            if (isSwitchSectionContext(child)) {
                sawSwitchSection = true;
                collectSwitchSectionExpressions(child, controlExpressions, valueExpressions);
                continue;
            }
            if (!sawSwitchSection && !(child instanceof TerminalNode)) {
                controlExpressions.add(child);
            }
        }
        List<FullGrammerExpressionAnalysis> result = new ArrayList<>();
        FullGrammerExpressionAnalysis value = caseAnalysis(valueExpressions, defaultQualifier, "VALUE");
        if (value.hasSources()) {
            result.add(value);
        }
        FullGrammerExpressionAnalysis control = caseAnalysis(controlExpressions, defaultQualifier, "CONTROL");
        if (control.hasSources()) {
            result.add(control);
        }
        return result;
    }

    private ParseTree singleCaseContext(ParseTree expression) {
        ParseTree unwrapped = unwrapSingleChildContexts(expression);
        if (isCaseContext(unwrapped)) {
            return unwrapped;
        }
        List<ParseTree> caseContexts = new ArrayList<>();
        collectCaseContexts(expression, caseContexts);
        return caseContexts.size() == 1 ? caseContexts.get(0) : null;
    }

    private void collectCaseContexts(ParseTree tree, List<ParseTree> result) {
        if (tree == null) {
            return;
        }
        if (isCaseContext(tree)) {
            result.add(tree);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectCaseContexts(tree.getChild(index), result);
        }
    }

    private void collectSwitchSectionExpressions(
            ParseTree section,
            List<ParseTree> controlExpressions,
            List<ParseTree> valueExpressions
    ) {
        boolean thenValue = false;
        for (int index = 0; index < section.getChildCount(); index++) {
            ParseTree child = section.getChild(index);
            String terminal = terminalText(child);
            if ("WHEN".equals(terminal)) {
                continue;
            }
            if ("THEN".equals(terminal)) {
                thenValue = true;
                continue;
            }
            if (child instanceof TerminalNode) {
                continue;
            }
            if (thenValue) {
                valueExpressions.add(child);
            } else {
                controlExpressions.add(child);
            }
        }
    }

    private FullGrammerExpressionAnalysis caseAnalysis(
            List<ParseTree> expressions,
            String defaultQualifier,
            String flowKind
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree expression : expressions) {
            FullGrammerExpressionAnalysis analysis = analyze(expression, defaultQualifier);
            for (int index = 0; index < Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size()); index++) {
                String alias = analysis.sourceAliases().get(index);
                String column = analysis.sourceColumns().get(index);
                String key = alias + "\u0000" + column;
                if (seen.add(key)) {
                    aliases.add(alias);
                    columns.add(column);
                }
            }
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", flowKind);
    }

    private boolean isSwitchSectionContext(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        String contextName = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return contextName.contains("switch") && contextName.contains("section");
    }

    private String terminalText(ParseTree tree) {
        if (tree instanceof TerminalNode terminal) {
            return terminal.getText().toUpperCase(Locale.ROOT);
        }
        return "";
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

    private void collectExpressionSourceColumns(
            ParseTree expression,
            String defaultQualifier,
            String transform,
            Set<ExpressionColumn> result
    ) {
        Set<ExpressionColumn> aggregateColumns = new LinkedHashSet<>();
        collectAggregateArgumentColumns(expression, defaultQualifier, aggregateColumns);
        Set<ExpressionColumn> outsideScalarSubqueryColumns = new LinkedHashSet<>();
        collectColumnsOutsideAggregateScalarSubquery(expression, defaultQualifier, outsideScalarSubqueryColumns);
        if (preferAggregateArgumentSourcesOnly()
                && !aggregateColumns.isEmpty()
                && outsideScalarSubqueryColumns.isEmpty()
                && (transform.equals("AGGREGATE") || transform.equals("CASE_WHEN"))) {
            result.addAll(aggregateColumns);
            return;
        }
        collectColumns(expression, defaultQualifier, result);
    }

    protected boolean preferAggregateArgumentSourcesOnly() {
        return true;
    }

    private void collectColumnsOutsideAggregateScalarSubquery(
            ParseTree tree,
            String defaultQualifier,
            Set<ExpressionColumn> result
    ) {
        if (tree == null) {
            return;
        }
        if (isAggregateScalarSubqueryContext(tree)) {
            return;
        }
        String contextName = tree.getClass().getSimpleName();
        if (isColumnReferenceContext(contextName)) {
            ExpressionColumn column = expressionColumn(tree.getText(), defaultQualifier);
            if (column != null) {
                result.add(column);
                return;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectColumnsOutsideAggregateScalarSubquery(tree.getChild(index), defaultQualifier, result);
        }
    }

    private boolean isAggregateScalarSubqueryContext(ParseTree tree) {
        String contextName = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (contextName.contains("subquery") && containsAggregateFunction(tree)) {
            return true;
        }
        return contextName.contains("select")
                && containsAggregateFunction(tree)
                && containsTerminal(tree, "from");
    }

    private boolean containsAggregateFunction(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isAggregateFunctionContext(tree)) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsAggregateFunction(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTerminal(ParseTree tree, String expectedLowerText) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof TerminalNode terminal
                && terminal.getText().equalsIgnoreCase(expectedLowerText)) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsTerminal(tree.getChild(index), expectedLowerText)) {
                return true;
            }
        }
        return false;
    }

    private void collectAggregateArgumentColumns(ParseTree tree, String defaultQualifier, Set<ExpressionColumn> result) {
        if (tree == null) {
            return;
        }
        if (isAggregateFunctionContext(tree)) {
            collectColumns(tree, defaultQualifier, result);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectAggregateArgumentColumns(tree.getChild(index), defaultQualifier, result);
        }
    }

    private boolean isAggregateFunctionContext(ParseTree tree) {
        String contextName = tree.getClass().getSimpleName();
        if (contextName.contains("Sum")) {
            return true;
        }
        if (!contextName.equals("FunctionCallContext") && !contextName.equals("Func_applicationContext")) {
            return false;
        }
        return isAggregateFunction(firstLeafText(tree).toLowerCase(Locale.ROOT));
    }

    private boolean isColumnReferenceContext(String contextName) {
        return contextName.equals("ColumnrefContext")
                || contextName.equals("ColumnRefContext")
                || contextName.equals("SimpleExprColumnRefContext")
                || contextName.equals("Full_column_nameContext");
    }

    private void collectColumns(ParseTree tree, String defaultQualifier, Set<ExpressionColumn> result) {
        if (tree == null) {
            return;
        }
        String contextName = tree.getClass().getSimpleName();
        if (isColumnReferenceContext(contextName)) {
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
            if (isAggregateFunction(lower) || isWindowFunction(lower) || isCoalesceFunction(lower)
                    || isConcatOrFormatFunction(lower)) {
                classifyFunctionName(lower, flags);
            }
            if (text.equals("||")) {
                flags.concatFormat = true;
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
        } else if (isCoalesceFunction(name)) {
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

    protected boolean isCoalesceFunction(String value) {
        return value.equals("coalesce");
    }

    private boolean isConcatOrFormatFunction(String value) {
        return value.equals("concat")
                || value.equals("format")
                || value.equals("to_char")
                || value.equals("group_concat");
    }

    private boolean isArithmeticOperator(String value) {
        return value.equals("+") || value.equals("-") || value.equals("*") || value.equals("/");
    }

    private boolean hasRelationExpressionDisqualifier(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            String clean = cleanIdentifier(text);
            if (text.startsWith("'") || text.startsWith("$") || isNumericLiteral(text)) {
                return true;
            }
            return isRelationOperator(clean);
        }
        String contextName = tree.getClass().getSimpleName();
        if (contextName.contains("Func")
                || contextName.contains("Case")
                || contextName.contains("Sum")
                || contextName.contains("Window")
                || contextName.contains("Over_clause")) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (hasRelationExpressionDisqualifier(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private boolean isRelationOperator(String value) {
        return value.equals("||")
                || value.equals("+")
                || value.equals("-")
                || value.equals("*")
                || value.equals("/")
                || value.equals("%")
                || value.equals("?")
                || value.equals(":")
                || value.equals("::");
    }

    private boolean isNumericLiteral(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        boolean sawDigit = false;
        boolean sawDot = false;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (Character.isDigit(ch)) {
                sawDigit = true;
            } else if (ch == '.' && !sawDot) {
                sawDot = true;
            } else {
                return false;
            }
        }
        return sawDigit;
    }

    private FullGrammerExpressionAnalysis emptyAnalysis() {
        return new FullGrammerExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", "VALUE");
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

    public boolean isNonColumnIdentifier(String value) {
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
