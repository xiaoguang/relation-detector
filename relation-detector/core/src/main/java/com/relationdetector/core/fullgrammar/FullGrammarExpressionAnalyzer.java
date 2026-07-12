package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.Enums.LineageTransformType;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.CaseParts;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.OperatorSemantic;

/**
 * Shared full-grammar expression semantics over dialect-owned typed context views.
 *
 * <p>This class never interprets terminal text. Generated-context adapters expose
 * physical columns, CASE branches, function symbols, operators, and scalar-query
 * boundaries; core only combines those typed facts into VALUE/CONTROL analyses.</p>
 */
public abstract class FullGrammarExpressionAnalyzer {
    private final FullGrammarParseTreeAdapter parseTreeAdapter;
    private final Set<String> nonColumnIdentifiers = new LinkedHashSet<>();

    protected FullGrammarExpressionAnalyzer(FullGrammarParseTreeAdapter parseTreeAdapter) {
        this.parseTreeAdapter = java.util.Objects.requireNonNull(parseTreeAdapter, "parseTreeAdapter");
    }

    public final FullGrammarParseTreeAdapter parseTreeAdapter() {
        return parseTreeAdapter;
    }

    protected DialectFunctionSemanticRegistry functionRegistry() {
        return DialectFunctionSemanticRegistry.standard();
    }

    public void ignoreIdentifier(String identifier) {
        String clean = cleanIdentifier(identifier);
        if (!clean.isBlank()) {
            nonColumnIdentifiers.add(clean.toLowerCase(Locale.ROOT));
        }
    }

    public FullGrammarExpressionAnalysis analyze(ParseTree expression) {
        return analyze(expression, "");
    }

    public FullGrammarExpressionAnalysis analyze(ParseTree expression, String defaultQualifier) {
        Set<ExpressionColumn> columns = new LinkedHashSet<>();
        String transform = transform(expression);
        collectExpressionSourceColumns(expression, defaultQualifier, transform, columns);
        String flowKind = transform.equals("CASE_WHEN") ? "CONTROL" : "VALUE";
        return new FullGrammarExpressionAnalysis(
                columns.stream().map(ExpressionColumn::qualifier).toList(),
                columns.stream().map(ExpressionColumn::column).toList(),
                transform,
                flowKind);
    }

    public List<FullGrammarExpressionAnalysis> writeAnalyses(ParseTree expression, String defaultQualifier) {
        FullGrammarExpressionAnalysis analysis = analyze(expression, defaultQualifier);
        return analysis.hasSources() ? List.of(analysis) : List.of();
    }

    public boolean prefersDialectWriteAnalyses(ParseTree expression) {
        return false;
    }

    public FullGrammarExpressionAnalysis analyzeRelationColumnExpression(
            ParseTree expression,
            String defaultQualifier
    ) {
        if (hasRelationExpressionDisqualifier(expression)) {
            return emptyAnalysis();
        }
        FullGrammarExpressionAnalysis analysis = analyze(expression, defaultQualifier);
        return "DIRECT".equals(analysis.transformType()) ? analysis : emptyAnalysis();
    }

    public boolean isTopLevelCaseExpression(ParseTree expression) {
        ParseTree unwrapped = unwrapSingleChildContexts(expression);
        return unwrapped != null && isCaseContext(unwrapped);
    }

    public List<FullGrammarExpressionAnalysis> caseExpressionAnalyses(
            ParseTree expression,
            String defaultQualifier
    ) {
        List<FullGrammarExpressionAnalysis> analyses = new ArrayList<>();
        collectCaseExpressionAnalyses(expression, defaultQualifier, analyses);
        return List.copyOf(analyses);
    }

    public List<FullGrammarExpressionAnalysis> caseWriteAnalyses(
            ParseTree expression,
            String defaultQualifier
    ) {
        ParseTree caseNode = singleCaseContext(expression);
        if (!isCaseContext(caseNode)) {
            return List.of();
        }
        CaseParts parts = parseTreeAdapter.caseParts(caseNode);
        if (!parts.conditional()) {
            return List.of();
        }
        List<FullGrammarExpressionAnalysis> result = new ArrayList<>(2);
        FullGrammarExpressionAnalysis value = caseAnalysis(parts.values(), defaultQualifier, "VALUE");
        if (value.hasSources()) {
            result.add(value);
        }
        FullGrammarExpressionAnalysis control = caseAnalysis(parts.controls(), defaultQualifier, "CONTROL");
        if (control.hasSources()) {
            result.add(control);
        }
        return List.copyOf(result);
    }

    protected boolean preferAggregateArgumentSourcesOnly() {
        return true;
    }

    protected boolean isCoalesceFunction(String value) {
        return functionRegistry().classify(value) == LineageTransformType.COALESCE;
    }

    public String cleanIdentifier(String value) {
        if (value == null) {
            return "";
        }
        List<String> parts = FullGrammarIdentifiers.qualifiedParts(value);
        return parts.isEmpty() ? FullGrammarIdentifiers.clean(value) : String.join(".", parts);
    }

    public boolean isNonColumnIdentifier(String value) {
        return nonColumnIdentifiers.contains(cleanIdentifier(value).toLowerCase(Locale.ROOT));
    }

    private ParseTree singleCaseContext(ParseTree expression) {
        ParseTree unwrapped = unwrapSingleChildContexts(expression);
        if (isCaseContext(unwrapped)) {
            return unwrapped;
        }
        List<ParseTree> cases = new ArrayList<>();
        collectCaseContexts(expression, cases);
        return cases.size() == 1 ? cases.get(0) : null;
    }

    private void collectCaseContexts(ParseTree tree, List<ParseTree> result) {
        if (tree == null) {
            return;
        }
        if (isCaseContext(tree)) {
            result.add(tree);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectCaseContexts(child, result);
        }
    }

    private FullGrammarExpressionAnalysis caseAnalysis(
            List<ParseTree> expressions,
            String defaultQualifier,
            String flowKind
    ) {
        List<String> aliases = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ParseTree expression : expressions) {
            FullGrammarExpressionAnalysis analysis = analyze(expression, defaultQualifier);
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
        return new FullGrammarExpressionAnalysis(aliases, columns, "CASE_WHEN", flowKind);
    }

    private void collectCaseExpressionAnalyses(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammarExpressionAnalysis> result
    ) {
        if (tree == null) {
            return;
        }
        if (isCaseContext(tree)) {
            result.addAll(caseWriteAnalyses(tree, defaultQualifier));
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectCaseExpressionAnalyses(child, defaultQualifier, result);
        }
    }

    private ParseTree unwrapSingleChildContexts(ParseTree tree) {
        ParseTree current = tree;
        while (current instanceof ParserRuleContext) {
            List<ParseTree> children = parseTreeAdapter.typedChildren(current);
            if (children.size() != 1) {
                break;
            }
            current = children.get(0);
        }
        return current;
    }

    private boolean isCaseContext(ParseTree tree) {
        return parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION);
    }

    private void collectExpressionSourceColumns(
            ParseTree expression,
            String defaultQualifier,
            String transform,
            Set<ExpressionColumn> result
    ) {
        Set<ExpressionColumn> aggregateColumns = new LinkedHashSet<>();
        collectAggregateArgumentColumns(expression, defaultQualifier, aggregateColumns);
        if (!aggregateColumns.isEmpty()
                && containsRole(expression, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            collectColumnsOutsideAggregateFunctions(expression, defaultQualifier, result);
            result.addAll(aggregateColumns);
            return;
        }
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

    private void collectColumnsOutsideAggregateFunctions(
            ParseTree tree,
            String defaultQualifier,
            Set<ExpressionColumn> result
    ) {
        if (tree == null || isAggregateFunctionContext(tree)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            return;
        }
        ExpressionColumn direct = expressionColumn(tree, defaultQualifier);
        if (direct != null) {
            result.add(direct);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectColumnsOutsideAggregateFunctions(child, defaultQualifier, result);
        }
    }

    private boolean containsRole(ParseTree tree, FullGrammarParseTreeAdapter.Role role) {
        if (tree == null) {
            return false;
        }
        if (parseTreeAdapter.hasRole(tree, role)) {
            return true;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            if (containsRole(child, role)) {
                return true;
            }
        }
        return false;
    }

    private void collectColumnsOutsideAggregateScalarSubquery(
            ParseTree tree,
            String defaultQualifier,
            Set<ExpressionColumn> result
    ) {
        if (tree == null || isAggregateScalarSubqueryContext(tree)) {
            return;
        }
        ExpressionColumn direct = expressionColumn(tree, defaultQualifier);
        if (direct != null) {
            result.add(direct);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectColumnsOutsideAggregateScalarSubquery(child, defaultQualifier, result);
        }
    }

    private boolean isAggregateScalarSubqueryContext(ParseTree tree) {
        return parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY)
                && containsAggregateFunction(tree);
    }

    private boolean containsAggregateFunction(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (isAggregateFunctionContext(tree)) {
            return true;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            if (containsAggregateFunction(child)) {
                return true;
            }
        }
        return false;
    }

    private void collectAggregateArgumentColumns(
            ParseTree tree,
            String defaultQualifier,
            Set<ExpressionColumn> result
    ) {
        if (tree == null) {
            return;
        }
        if (isAggregateFunctionContext(tree)) {
            List<ParseTree> arguments = parseTreeAdapter.functionArgumentExpressions(tree);
            if (!arguments.isEmpty()) {
                for (ParseTree argument : arguments) {
                    collectColumns(argument, defaultQualifier, result);
                }
                return;
            }
            for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
                if (!parseTreeAdapter.hasRole(child, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
                    collectColumns(child, defaultQualifier, result);
                }
            }
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectAggregateArgumentColumns(child, defaultQualifier, result);
        }
    }

    private boolean isAggregateFunctionContext(ParseTree tree) {
        if (parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) {
            return true;
        }
        return parseTreeAdapter.functionName(tree)
                .map(functionRegistry()::classify)
                .filter(LineageTransformType.AGGREGATE::equals)
                .isPresent();
    }

    private void collectColumns(ParseTree tree, String defaultQualifier, Set<ExpressionColumn> result) {
        if (tree == null) {
            return;
        }
        ExpressionColumn direct = expressionColumn(tree, defaultQualifier);
        if (direct != null) {
            result.add(direct);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectColumns(child, defaultQualifier, result);
        }
    }

    private ExpressionColumn expressionColumn(ParseTree tree, String defaultQualifier) {
        return parseTreeAdapter.directColumn(tree)
                .map(column -> {
                    String qualifier = cleanIdentifier(column.qualifier());
                    String name = cleanIdentifier(column.column());
                    if (qualifier.isBlank()) {
                        qualifier = cleanIdentifier(defaultQualifier);
                    }
                    if (name.isBlank() || qualifier.isBlank()
                            || isNonColumnIdentifier(qualifier)
                            || isNonColumnIdentifier(name)) {
                        return null;
                    }
                    return new ExpressionColumn(qualifier, name);
                })
                .orElse(null);
    }

    private String transform(ParseTree expression) {
        TransformFlags flags = new TransformFlags();
        visitTransform(expression, flags);
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

    private void visitTransform(ParseTree tree, TransformFlags flags) {
        if (tree == null) {
            return;
        }
        if (parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)) {
            flags.caseExpression = true;
        }
        if (parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)) {
            flags.aggregate = true;
            flags.functionCall = true;
        }
        if (parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            flags.window = true;
            flags.functionCall = true;
        }
        if (parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CONCAT_EXPRESSION)) {
            flags.concatFormat = true;
            flags.functionCall = true;
        }
        parseTreeAdapter.functionName(tree).ifPresent(name -> classifyFunctionName(name, flags));
        OperatorSemantic operator = parseTreeAdapter.operatorSemantic(tree);
        if (operator == OperatorSemantic.ARITHMETIC) flags.arithmetic = true;
        if (operator == OperatorSemantic.CONCAT_FORMAT) flags.concatFormat = true;
        if (operator == OperatorSemantic.CUMULATIVE) flags.cumulative = true;
        if (operator == OperatorSemantic.BOOLEAN_EXPRESSION) flags.functionCall = true;
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            visitTransform(child, flags);
        }
    }

    private void classifyFunctionName(String name, TransformFlags flags) {
        flags.functionCall = true;
        LineageTransformType transform = isCoalesceFunction(name)
                ? LineageTransformType.COALESCE
                : functionRegistry().classify(name);
        if (transform == LineageTransformType.AGGREGATE) flags.aggregate = true;
        if (transform == LineageTransformType.WINDOW_DERIVED) flags.window = true;
        if (transform == LineageTransformType.COALESCE) flags.coalesce = true;
        if (transform == LineageTransformType.CONCAT_FORMAT) flags.concatFormat = true;
    }

    private boolean hasRelationExpressionDisqualifier(ParseTree tree) {
        if (tree == null) {
            return false;
        }
        if (parseTreeAdapter.isNonColumnValue(tree)
                || parseTreeAdapter.operatorSemantic(tree) != OperatorSemantic.NONE
                || parseTreeAdapter.functionName(tree).isPresent()
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.FUNCTION_CALL)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)) {
            return true;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            if (hasRelationExpressionDisqualifier(child)) {
                return true;
            }
        }
        return false;
    }

    private FullGrammarExpressionAnalysis emptyAnalysis() {
        return new FullGrammarExpressionAnalysis(List.of(), List.of(), "UNKNOWN_EXPRESSION", "VALUE");
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
