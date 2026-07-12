package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalysis;
import com.relationdetector.core.fullgrammar.FullGrammarExpressionAnalyzer;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.CaseParts;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic;
import com.relationdetector.core.lineage.LineageTransformClassifier;

/**
 * SQL Server expression analyzer.
 *
 * <p>The shared core expression analyzer already understands T-SQL bracketed
 * identifiers and, with the SQL Server grammar's {@code full_column_name}
 * context enabled in core, can extract physical column sources from typed parse
 * trees without reading raw SQL text.</p>
 */
public final class SqlServerExpressionAnalyzer extends FullGrammarExpressionAnalyzer {
    private static final Map<String, LineageTransformType> FUNCTION_EXTENSIONS = Map.of(
            "isnull", LineageTransformType.COALESCE);

    public SqlServerExpressionAnalyzer(
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter parseTreeAdapter
    ) {
        super(parseTreeAdapter);
    }

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
    public List<FullGrammarExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        ParseTree scalar = scalarSubquery(expression);
        if (scalar != null) {
            FullGrammarExpressionAnalysis value = scalarProjection(scalar, defaultQualifier);
            FullGrammarExpressionAnalysis control = scalarControl(scalar, defaultQualifier);
            List<FullGrammarExpressionAnalysis> result = new ArrayList<>(2);
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
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>(2);
        LineageTransformType enclosing = enclosingValueTransformOutsideCases(expression);
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
    public FullGrammarExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        FullGrammarExpressionAnalysis analysis = super.analyze(expression, defaultQualifier);
        LineageTransformType typedTransform = enclosingValueTransform(expression);
        if (typedTransform == LineageTransformType.DIRECT) {
            return analysis;
        }
        return analysis.withTransform(typedTransform.name(), analysis.flowKind());
    }

    private FullGrammarExpressionAnalysis scalarProjection(ParseTree scalar, String defaultQualifier) {
        ParseTree selectItem = parseTreeAdapter().firstDescendant(
                scalar, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.SELECT_TARGET_ITEM);
        ParseTree projection = selectItem == null ? null : parseTreeAdapter().firstDescendant(
                selectItem, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.EXPRESSION);
        if (projection == null) {
            return empty("VALUE");
        }
        FullGrammarExpressionAnalysis analysis = analyze(projection, defaultQualifier);
        return analysis.withTransform(analysis.transformType(), "VALUE");
    }

    private boolean containsCase(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)) {
            return true;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            if (containsCase(child)) {
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
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectExpressionCases(child, defaultQualifier, result);
        }
    }

    private void collectCase(ParseTree caseTree, String defaultQualifier, CaseAccumulator result) {
        CaseParts parts = parseTreeAdapter().caseParts(caseTree);
        for (ParseTree control : parts.controls()) {
            result.addControl(super.analyze(control, defaultQualifier));
        }
        for (ParseTree value : parts.values()) {
            collectExpressionCases(value, defaultQualifier, result);
        }
    }

    private boolean isCaseContext(ParseTree tree) {
        return parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION);
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
        if (state.concatFormat) {
            return LineageTransformType.CONCAT_FORMAT;
        }
        if (state.function) {
            return LineageTransformType.FUNCTION_CALL;
        }
        return LineageTransformType.DIRECT;
    }

    private LineageTransformType enclosingValueTransformOutsideCases(ParseTree tree) {
        TransformState state = new TransformState();
        collectTransformsOutsideCases(tree, state);
        if (state.aggregate) {
            return LineageTransformType.AGGREGATE;
        }
        if (state.arithmetic) {
            return LineageTransformType.ARITHMETIC;
        }
        if (state.coalesce) {
            return LineageTransformType.COALESCE;
        }
        if (state.concatFormat) {
            return LineageTransformType.CONCAT_FORMAT;
        }
        if (state.function) {
            return LineageTransformType.FUNCTION_CALL;
        }
        return LineageTransformType.DIRECT;
    }

    private void collectTransformsOutsideCases(ParseTree tree, TransformState state) {
        if (tree == null || isCaseContext(tree)) {
            return;
        }
        collectCurrentTransform(tree, state);
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectTransformsOutsideCases(child, state);
        }
    }

    private void collectTransforms(ParseTree tree, TransformState state) {
        if (tree == null) {
            return;
        }
        collectCurrentTransform(tree, state);
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectTransforms(child, state);
        }
    }

    private void collectCurrentTransform(ParseTree tree, TransformState state) {
        parseTreeAdapter().functionName(tree).ifPresent(name -> {
            LineageTransformType classified = LineageTransformClassifier.classifyFunction(
                    name, false, FUNCTION_EXTENSIONS);
            if (classified == LineageTransformType.AGGREGATE) {
                state.aggregate = true;
            } else if (classified == LineageTransformType.COALESCE) {
                state.coalesce = true;
            } else if (classified == LineageTransformType.CONCAT_FORMAT) {
                state.concatFormat = true;
            } else {
                state.function = true;
            }
        });
        OperatorSemantic operator = parseTreeAdapter().operatorSemantic(tree);
        if (operator == OperatorSemantic.ARITHMETIC) state.arithmetic = true;
        if (operator == OperatorSemantic.CONCAT_FORMAT) state.concatFormat = true;
    }

    private FullGrammarExpressionAnalysis scalarControl(ParseTree scalar, String defaultQualifier) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<ParseTree> controls = new ArrayList<>();
        collectDirectScopeContexts(scalar, scalar, controls);
        for (ParseTree control : controls) {
            append(aliases, columns, seen, analyze(control, defaultQualifier));
        }
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", "CONTROL");
    }

    private void collectDirectScopeContexts(
            ParseTree root,
            ParseTree tree,
            List<ParseTree> result
    ) {
        if (tree == null) {
            return;
        }
        if (tree != root && isScalarBoundary(tree)) {
            return;
        }
        if (parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.CONTROL_SCOPE)) {
            result.add(tree);
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectDirectScopeContexts(root, child, result);
        }
    }

    private ParseTree scalarSubquery(ParseTree tree) {
        if (tree == null) {
            return null;
        }
        if (isScalarBoundary(tree)) {
            return tree;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            ParseTree found = scalarSubquery(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private boolean isScalarBoundary(ParseTree tree) {
        return parseTreeAdapter().hasRole(
                tree, com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY);
    }

    private void append(
            List<String> aliases,
            List<String> columns,
            Set<String> seen,
            FullGrammarExpressionAnalysis analysis
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

    private FullGrammarExpressionAnalysis empty(String flowKind) {
        return new FullGrammarExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", flowKind);
    }

    private static final class TransformState {
        private boolean aggregate;
        private boolean arithmetic;
        private boolean coalesce;
        private boolean concatFormat;
        private boolean function;
    }

    private static final class CaseAccumulator {
        private final List<String> valueAliases = new ArrayList<>();
        private final List<String> valueColumns = new ArrayList<>();
        private final Set<String> valueKeys = new LinkedHashSet<>();
        private final List<String> controlAliases = new ArrayList<>();
        private final List<String> controlColumns = new ArrayList<>();
        private final Set<String> controlKeys = new LinkedHashSet<>();

        private void addValue(FullGrammarExpressionAnalysis analysis) {
            append(valueAliases, valueColumns, valueKeys, analysis);
        }

        private void addControl(FullGrammarExpressionAnalysis analysis) {
            append(controlAliases, controlColumns, controlKeys, analysis);
        }

        private boolean hasValues() {
            return !valueColumns.isEmpty();
        }

        private boolean hasControls() {
            return !controlColumns.isEmpty();
        }

        private FullGrammarExpressionAnalysis value(LineageTransformType transform) {
            return new FullGrammarExpressionAnalysis(
                    valueAliases, valueColumns, transform.name(), "VALUE");
        }

        private FullGrammarExpressionAnalysis control() {
            return new FullGrammarExpressionAnalysis(
                    controlAliases, controlColumns, "CASE_WHEN", "CONTROL");
        }

        private static void append(
                List<String> aliases,
                List<String> columns,
                Set<String> seen,
                FullGrammarExpressionAnalysis analysis
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
