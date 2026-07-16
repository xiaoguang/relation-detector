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
 *
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
        return scalarSubquery(expression) != null || containsCase(expression)
                || containsRole(expression,
                com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.WINDOW_CONTROL_SCOPE);
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
                result.add(control.withTransform("DIRECT", "CONTROL"));
            }
            return withWindowControl(expression, defaultQualifier, result);
        }

        if (!containsCase(expression)) {
            return withWindowControl(expression, defaultQualifier,
                    super.writeAnalyses(expression, defaultQualifier));
        }

        SqlServerCaseAccumulator cases = new SqlServerCaseAccumulator();
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
        return withWindowControl(expression, defaultQualifier, result);
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

    private boolean containsRole(
            ParseTree tree,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role role) {
        if (tree == null) {
            return false;
        }
        if (parseTreeAdapter().hasRole(tree, role)) {
            return true;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            if (containsRole(child, role)) {
                return true;
            }
        }
        return false;
    }

    private List<FullGrammarExpressionAnalysis> withWindowControl(
            ParseTree expression,
            String defaultQualifier,
            List<FullGrammarExpressionAnalysis> analyses) {
        FullGrammarExpressionAnalysis window = roleAnalysis(expression, defaultQualifier,
                com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role.WINDOW_CONTROL_SCOPE,
                "WINDOW_DERIVED");
        if (!window.hasSources()) {
            return List.copyOf(analyses);
        }
        Set<String> windowKeys = keys(window);
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>(analyses.size() + 1);
        for (FullGrammarExpressionAnalysis analysis : analyses) {
            if (!"VALUE".equals(analysis.flowKind())) {
                result.add(analysis);
                continue;
            }
            List<String> aliases = new ArrayList<>();
            List<String> columns = new ArrayList<>();
            int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
            for (int index = 0; index < count; index++) {
                String key = analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index);
                if (!windowKeys.contains(key)) {
                    aliases.add(analysis.sourceAliases().get(index));
                    columns.add(analysis.sourceColumns().get(index));
                }
            }
            if (!columns.isEmpty()) {
                result.add(new FullGrammarExpressionAnalysis(
                        aliases, columns, analysis.transformType(), analysis.flowKind()));
            }
        }
        result.add(window);
        return List.copyOf(result);
    }

    private FullGrammarExpressionAnalysis roleAnalysis(
            ParseTree tree,
            String defaultQualifier,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role role,
            String transform) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        collectRoleAnalysis(tree, defaultQualifier, role, aliases, columns, seen);
        return new FullGrammarExpressionAnalysis(aliases, columns, transform, "CONTROL");
    }

    private void collectRoleAnalysis(
            ParseTree tree,
            String defaultQualifier,
            com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role role,
            List<String> aliases,
            List<String> columns,
            Set<String> seen) {
        if (tree == null) {
            return;
        }
        if (parseTreeAdapter().hasRole(tree, role)) {
            append(aliases, columns, seen, super.analyze(tree, defaultQualifier));
            return;
        }
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectRoleAnalysis(child, defaultQualifier, role, aliases, columns, seen);
        }
    }

    private Set<String> keys(FullGrammarExpressionAnalysis analysis) {
        Set<String> result = new LinkedHashSet<>();
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        for (int index = 0; index < count; index++) {
            result.add(analysis.sourceAliases().get(index) + "\u0000" + analysis.sourceColumns().get(index));
        }
        return result;
    }

    private void collectExpressionCases(ParseTree tree, String defaultQualifier, SqlServerCaseAccumulator result) {
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

    private void collectCase(ParseTree caseTree, String defaultQualifier, SqlServerCaseAccumulator result) {
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
        SqlServerTransformState state = new SqlServerTransformState();
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
        SqlServerTransformState state = new SqlServerTransformState();
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

    private void collectTransformsOutsideCases(ParseTree tree, SqlServerTransformState state) {
        if (tree == null || isCaseContext(tree)) {
            return;
        }
        collectCurrentTransform(tree, state);
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectTransformsOutsideCases(child, state);
        }
    }

    private void collectTransforms(ParseTree tree, SqlServerTransformState state) {
        if (tree == null) {
            return;
        }
        collectCurrentTransform(tree, state);
        for (ParseTree child : parseTreeAdapter().typedChildren(tree)) {
            collectTransforms(child, state);
        }
    }

    private void collectCurrentTransform(ParseTree tree, SqlServerTransformState state) {
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
        return new FullGrammarExpressionAnalysis(aliases, columns, "DIRECT", "CONTROL");
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

}
