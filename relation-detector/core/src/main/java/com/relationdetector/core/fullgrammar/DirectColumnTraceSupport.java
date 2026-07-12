package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

/** Resolves direct physical-column traces without owning predicate semantics. */
final class DirectColumnTraceSupport {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammarExpressionAnalyzer expressionAnalyzer;
    private final FullGrammarParseTreeAdapter parseTreeAdapter;

    DirectColumnTraceSupport(
            SourceLocationSupport source,
            RowsetScopeSink rowsets,
            FullGrammarExpressionAnalyzer expressionAnalyzer,
            FullGrammarParseTreeAdapter parseTreeAdapter
    ) {
        this.source = source;
        this.rowsets = rowsets;
        this.expressionAnalyzer = expressionAnalyzer;
        this.parseTreeAdapter = parseTreeAdapter;
    }

    Optional<FullGrammarColumnReference> singlePredicateColumn(ParseTree tree, ParseTree oppositeTree) {
        if (containsQueryBoundary(tree)) {
            return Optional.empty();
        }
        Optional<FullGrammarColumnReference> explicit = singleDirectColumnNoDefault(tree);
        if (explicit.isPresent()) {
            return explicit;
        }
        if (singleDirectColumnNoDefault(oppositeTree).isEmpty()) {
            return Optional.empty();
        }
        return singleDirectColumnWithDefault(tree);
    }

    private boolean containsQueryBoundary(ParseTree tree) {
        return parseTreeAdapter.firstDescendant(
                        tree, FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY) != null
                || parseTreeAdapter.firstDescendant(
                        tree, FullGrammarParseTreeAdapter.Role.QUERY_BOUNDARY) != null;
    }

    List<FullGrammarColumnReference> directColumnList(ParseTree tree) {
        Optional<FullGrammarColumnReference> naked = nakedColumn(tree);
        if (naked.isPresent()) {
            return List.of(naked.get());
        }
        List<FullGrammarColumnReference> columns = new ArrayList<>();
        if (collectBareColumnList(tree, rowsets.defaultProjectionQualifier(), columns)) {
            return columns.stream().distinct().toList();
        }
        return directExpressionColumns(tree, rowsets.defaultProjectionQualifier());
    }

    List<FullGrammarColumnReference> directTargetItemColumns(ParseTree item, String defaultQualifier) {
        List<FullGrammarColumnReference> columns = new ArrayList<>();
        if (collectBareColumnList(item, defaultQualifier, columns)) {
            return columns.stream().distinct().toList();
        }
        return directExpressionColumns(item, defaultQualifier);
    }

    private Optional<FullGrammarColumnReference> singleDirectColumnWithDefault(ParseTree tree) {
        Optional<FullGrammarColumnReference> naked = nakedColumn(tree);
        if (naked.isPresent()) {
            return naked;
        }
        List<FullGrammarColumnReference> columns = directColumnList(tree);
        return columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
    }

    private Optional<FullGrammarColumnReference> singleDirectColumnNoDefault(ParseTree tree) {
        Optional<FullGrammarColumnReference> naked = nakedColumnNoDefault(tree);
        if (naked.isPresent()) {
            return naked;
        }
        List<FullGrammarColumnReference> columns = directColumnListNoDefault(tree);
        return columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
    }

    private List<FullGrammarColumnReference> directColumnListNoDefault(ParseTree tree) {
        Optional<FullGrammarColumnReference> naked = nakedColumnNoDefault(tree);
        if (naked.isPresent()) {
            return List.of(naked.get());
        }
        return directExpressionColumns(tree, "");
    }

    private boolean collectBareColumnList(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammarColumnReference> columns
    ) {
        List<FullGrammarColumnReference> direct = directExpressionColumns(tree, defaultQualifier);
        columns.addAll(direct);
        return !direct.isEmpty();
    }

    private Optional<FullGrammarColumnReference> nakedColumn(ParseTree tree) {
        return nakedColumnWithDefault(tree, rowsets.defaultProjectionQualifier());
    }

    private Optional<FullGrammarColumnReference> nakedColumnNoDefault(ParseTree tree) {
        return nakedColumnWithDefault(tree, "");
    }

    private Optional<FullGrammarColumnReference> nakedColumnWithDefault(
            ParseTree tree, String defaultQualifier
    ) {
        ParseTree current = unwrapTransparentSingleChild(tree);
        return parseTreeAdapter.directColumn(current)
                .flatMap(column -> {
                    String qualifier = source.clean(column.qualifier());
                    String name = source.clean(column.column());
                    if (qualifier.isBlank()) {
                        qualifier = source.clean(defaultQualifier);
                    }
                    if (qualifier.isBlank() || name.isBlank()
                            || expressionAnalyzer.isNonColumnIdentifier(qualifier)
                            || expressionAnalyzer.isNonColumnIdentifier(name)) {
                        return Optional.empty();
                    }
                    return Optional.of(new FullGrammarColumnReference(qualifier, name));
                });
    }

    private ParseTree unwrapTransparentSingleChild(ParseTree tree) {
        ParseTree current = tree;
        while (current != null) {
            if (isSemanticBoundary(current)) {
                break;
            }
            List<ParseTree> children = parseTreeAdapter.typedChildren(current);
            if (children.size() != 1) {
                break;
            }
            current = children.get(0);
        }
        return current;
    }

    private boolean isSemanticBoundary(ParseTree tree) {
        return parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.QUERY_BOUNDARY)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.SCALAR_SUBQUERY)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.FUNCTION_CALL)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.CASE_EXPRESSION)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.AGGREGATE_FUNCTION)
                || parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.WINDOW_FUNCTION)
                || parseTreeAdapter.functionName(tree).isPresent()
                || parseTreeAdapter.operatorSemantic(tree)
                        != FullGrammarParseTreeAdapter.OperatorSemantic.NONE
                || parseTreeAdapter.isNonColumnValue(tree);
    }

    private List<FullGrammarColumnReference> directExpressionColumns(
            ParseTree tree, String defaultQualifier
    ) {
        FullGrammarExpressionAnalysis analysis =
                expressionAnalyzer.analyzeRelationColumnExpression(tree, defaultQualifier);
        if (!"DIRECT".equals(analysis.transformType())) {
            return List.of();
        }
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        List<FullGrammarColumnReference> columns = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String alias = source.clean(analysis.sourceAliases().get(index));
            String column = source.clean(analysis.sourceColumns().get(index));
            if (alias.isBlank() || column.isBlank()) {
                return List.of();
            }
            columns.add(new FullGrammarColumnReference(alias, column));
        }
        return columns.stream().distinct().toList();
    }
}
