package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalysis;
import com.relationdetector.core.fullgrammer.FullGrammerExpressionAnalyzer;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 * SQL Server expression analyzer.
 *
 * <p>The shared core expression analyzer already understands T-SQL bracketed
 * identifiers and, with the SQL Server grammar's {@code full_column_name}
 * context enabled in core, can extract physical column sources from typed parse
 * trees without reading raw SQL text.</p>
 */
public final class SqlServerExpressionAnalyzer extends FullGrammerExpressionAnalyzer {
    private static final Map<String, LineageTransformType> FUNCTION_EXTENSIONS = Map.of(
            "isnull", LineageTransformType.COALESCE);

    @Override
    protected boolean isCoalesceFunction(String value) {
        return LineageTransformClassifier.classifyFunction(value, false, FUNCTION_EXTENSIONS)
                == LineageTransformType.COALESCE;
    }

    @Override
    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return scalarSubquery(expression) != null || containsCase(expression);
    }

    @Override
    public List<FullGrammerExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        ParseTree scalar = scalarSubquery(expression);
        if (scalar != null) {
            FullGrammerExpressionAnalysis value = scalarProjection(scalar, defaultQualifier);
            FullGrammerExpressionAnalysis control = scalarControl(scalar, defaultQualifier);
            List<FullGrammerExpressionAnalysis> result = new ArrayList<>(2);
            if (value.hasSources()) {
                result.add(value.withTransform(value.transformType(), "VALUE"));
            }
            if (control.hasSources()) {
                result.add(control.withTransform("CASE_WHEN", "CONTROL"));
            }
            return List.copyOf(result);
        }

        if (!containsCase(expression)) {
            return super.writeAnalyses(expression, defaultQualifier);
        }

        CaseAccumulator cases = new CaseAccumulator();
        collectExpressionCases(expression, defaultQualifier, cases);
        List<FullGrammerExpressionAnalysis> result = new ArrayList<>(2);
        LineageTransformType enclosing = enclosingValueTransform(expression);
        if (cases.hasValues()) {
            LineageTransformType valueTransform = enclosing == LineageTransformType.DIRECT
                    ? LineageTransformType.CASE_WHEN
                    : enclosing;
            result.add(cases.value(valueTransform));
        }
        if (cases.hasControls()) {
            result.add(cases.control());
        }
        return List.copyOf(result);
    }

    @Override
    public FullGrammerExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        FullGrammerExpressionAnalysis analysis = super.analyze(expression, defaultQualifier);
        LineageTransformType typedTransform = enclosingValueTransform(expression);
        if (typedTransform == LineageTransformType.DIRECT) {
            return analysis;
        }
        return analysis.withTransform(typedTransform.name(), analysis.flowKind());
    }

    private FullGrammerExpressionAnalysis scalarProjection(ParseTree scalar, String defaultQualifier) {
        ParseTree selectItem = firstDescendant(scalar, "Select_list_elemContext");
        ParseTree projection = selectItem == null ? null : firstDescendant(selectItem, "ExpressionContext");
        if (projection == null) {
            return empty("VALUE");
        }
        FullGrammerExpressionAnalysis analysis = analyze(projection, defaultQualifier);
        return analysis.withTransform(analysis.transformType(), "VALUE");
    }

    private boolean containsCase(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (tree.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains("case")) {
            return true;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (containsCase(tree.getChild(index))) {
                return true;
            }
        }
        return false;
    }

    private void collectExpressionCases(ParseTree tree, String defaultQualifier, CaseAccumulator result) {
        if (tree == null) {
            return;
        }
        if (isCaseContext(tree)) {
            collectCase(tree, defaultQualifier, result);
            return;
        }
        if (!containsCase(tree)) {
            result.addValue(super.analyze(tree, defaultQualifier));
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectExpressionCases(tree.getChild(index), defaultQualifier, result);
        }
    }

    private void collectCase(ParseTree caseTree, String defaultQualifier, CaseAccumulator result) {
        boolean elseValue = false;
        boolean sawSection = false;
        for (int index = 0; index < caseTree.getChildCount(); index++) {
            ParseTree child = caseTree.getChild(index);
            String terminal = child instanceof org.antlr.v4.runtime.tree.TerminalNode node
                    ? node.getText().toUpperCase(Locale.ROOT)
                    : "";
            if (terminal.equals("ELSE")) {
                elseValue = true;
                continue;
            }
            String context = child.getClass().getSimpleName();
            if (context.equals("Switch_sectionContext")) {
                sawSection = true;
                List<ParseTree> expressions = directChildren(child, "ExpressionContext");
                if (expressions.size() >= 2) {
                    result.addControl(super.analyze(expressions.get(0), defaultQualifier));
                    collectExpressionCases(expressions.get(1), defaultQualifier, result);
                }
                continue;
            }
            if (context.equals("Switch_search_condition_sectionContext")) {
                sawSection = true;
                ParseTree condition = firstDirectChild(child, "Search_conditionContext");
                ParseTree value = firstDirectChild(child, "ExpressionContext");
                result.addControl(super.analyze(condition, defaultQualifier));
                collectExpressionCases(value, defaultQualifier, result);
                continue;
            }
            if (context.equals("ExpressionContext")) {
                if (elseValue) {
                    collectExpressionCases(child, defaultQualifier, result);
                } else if (!sawSection) {
                    result.addControl(super.analyze(child, defaultQualifier));
                }
            }
        }
    }

    private boolean isCaseContext(ParseTree tree) {
        return tree != null && tree.getClass().getSimpleName().equals("Case_expressionContext");
    }

    private List<ParseTree> directChildren(ParseTree tree, String simpleName) {
        List<ParseTree> result = new ArrayList<>();
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (child.getClass().getSimpleName().equals(simpleName)) {
                result.add(child);
            }
        }
        return result;
    }

    private ParseTree firstDirectChild(ParseTree tree, String simpleName) {
        return directChildren(tree, simpleName).stream().findFirst().orElse(null);
    }

    private LineageTransformType enclosingValueTransform(ParseTree tree) {
        TransformState state = new TransformState();
        collectTransforms(tree, state);
        if (state.aggregate) {
            return LineageTransformType.AGGREGATE;
        }
        if (state.arithmetic) {
            return LineageTransformType.ARITHMETIC;
        }
        if (state.coalesce) {
            return LineageTransformType.COALESCE;
        }
        if (state.function) {
            return LineageTransformType.FUNCTION_CALL;
        }
        return LineageTransformType.DIRECT;
    }

    private void collectTransforms(ParseTree tree, TransformState state) {
        if (tree == null) {
            return;
        }
        String context = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (context.contains("function") || context.contains("aggregate") || context.contains("convert")) {
            String name = firstTerminal(tree).toLowerCase(Locale.ROOT);
            LineageTransformType classified = LineageTransformClassifier.classifyFunction(
                    name, false, FUNCTION_EXTENSIONS);
            if (classified == LineageTransformType.AGGREGATE) {
                state.aggregate = true;
            } else if (classified == LineageTransformType.COALESCE) {
                state.coalesce = true;
            } else {
                state.function = true;
            }
        }
        if (tree instanceof org.antlr.v4.runtime.tree.TerminalNode terminal) {
            String token = terminal.getText().toLowerCase(Locale.ROOT);
            LineageTransformType classified = LineageTransformClassifier.classifyFunction(
                    token, false, FUNCTION_EXTENSIONS);
            if (classified == LineageTransformType.AGGREGATE) {
                state.aggregate = true;
            } else if (classified == LineageTransformType.COALESCE) {
                state.coalesce = true;
            } else if (token.equals("convert") || token.equals("cast") || token.equals("year")
                    || token.equals("month") || token.equals("datepart") || token.equals("datename")
                    || token.equals("dateadd") || token.equals("datediff")) {
                state.function = true;
            }
            if (token.equals("+") || token.equals("-") || token.equals("*")
                    || token.equals("/") || token.equals("%")) {
                state.arithmetic = true;
            }
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectTransforms(tree.getChild(index), state);
        }
    }

    private String firstTerminal(ParseTree tree) {
        if (tree == null) {
            return "";
        }
        if (tree instanceof org.antlr.v4.runtime.tree.TerminalNode terminal) {
            return terminal.getText();
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            String value = firstTerminal(tree.getChild(index));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private FullGrammerExpressionAnalysis scalarControl(ParseTree scalar, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String context : List.of(
                "Join_onContext", "Search_conditionContext", "Group_by_itemContext")) {
            List<ParseTree> controls = new ArrayList<>();
            collectDirectScopeContexts(scalar, scalar, context, controls);
            for (ParseTree control : controls) {
                append(aliases, columns, seen, analyze(control, defaultQualifier));
            }
        }
        return new FullGrammerExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void collectDirectScopeContexts(
            ParseTree root,
            ParseTree tree,
            String expected,
            List<ParseTree> result
    ) {
        if (tree == null) {
            return;
        }
        if (tree != root && isScalarBoundary(tree)) {
            return;
        }
        if (tree.getClass().getSimpleName().equals(expected)) {
            result.add(tree);
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDirectScopeContexts(root, tree.getChild(index), expected, result);
        }
    }

    private ParseTree scalarSubquery(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (isScalarBoundary(tree)) {
            return tree;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree found = scalarSubquery(tree.getChild(index));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isScalarBoundary(ParseTree tree) {
        String name = tree.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.equals("subquerycontext") || name.contains("scalarsubquery");
    }

    private ParseTree firstDescendant(ParseTree tree, String expectedSimpleName) {
        if (tree == null) {
            return null;
        }
        if (tree.getClass().getSimpleName().equals(expectedSimpleName)) {
            return tree;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            ParseTree found = firstDescendant(tree.getChild(index), expectedSimpleName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private void append(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammerExpressionAnalysis analysis
    ) {
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            String alias = analysis.sourceAliases().get(index);
            String column = analysis.sourceColumns().get(index);
            if (seen.add(alias + "\u0000" + column)) {
                aliases.add(alias);
                columns.add(column);
            }
        }
    }

    private FullGrammerExpressionAnalysis empty(String flowKind) {
        return new FullGrammerExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", flowKind);
    }

    private static final class TransformState {
        private boolean aggregate;
        private boolean arithmetic;
        private boolean coalesce;
        private boolean function;
    }

    private static final class CaseAccumulator {
        private final List<String> valueAliases = new ArrayList<>();
        private final List<String> valueColumns = new ArrayList<>();
        private final Set<String> valueKeys = new LinkedHashSet<>();
        private final List<String> controlAliases = new ArrayList<>();
        private final List<String> controlColumns = new ArrayList<>();
        private final Set<String> controlKeys = new LinkedHashSet<>();

        private void addValue(FullGrammerExpressionAnalysis analysis) {
            append(valueAliases, valueColumns, valueKeys, analysis);
        }

        private void addControl(FullGrammerExpressionAnalysis analysis) {
            append(controlAliases, controlColumns, controlKeys, analysis);
        }

        private boolean hasValues() {
            return !valueColumns.isEmpty();
        }

        private boolean hasControls() {
            return !controlColumns.isEmpty();
        }

        private FullGrammerExpressionAnalysis value(LineageTransformType transform) {
            return new FullGrammerExpressionAnalysis(
                    valueAliases, valueColumns, transform.name(), "VALUE");
        }

        private FullGrammerExpressionAnalysis control() {
            return new FullGrammerExpressionAnalysis(
                    controlAliases, controlColumns, "CASE_WHEN", "CONTROL");
        }

        private static void append(
                List<String> aliases,
                List<String> columns,
                Set<String> seen,
                FullGrammerExpressionAnalysis analysis
        ) {
            if (analysis == null) {
                return;
            }
            int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String alias = analysis.sourceAliases().get(index);
                String column = analysis.sourceColumns().get(index);
                if (seen.add(alias + "\u0000" + column)) {
                    aliases.add(alias);
                    columns.add(column);
                }
            }
        }
    }
}
