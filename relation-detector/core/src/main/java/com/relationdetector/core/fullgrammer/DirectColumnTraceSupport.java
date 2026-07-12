package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

/** Resolves direct physical-column traces without owning predicate semantics. */
final class DirectColumnTraceSupport {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammerExpressionAnalyzer expressionAnalyzer;
    private final FullGrammerParseTreeAdapter parseTreeAdapter;

    DirectColumnTraceSupport(
            SourceLocationSupport source,
            RowsetScopeSink rowsets,
            FullGrammerExpressionAnalyzer expressionAnalyzer,
            FullGrammerParseTreeAdapter parseTreeAdapter
    ) {
        this.source = source;
        this.rowsets = rowsets;
        this.expressionAnalyzer = expressionAnalyzer;
        this.parseTreeAdapter = parseTreeAdapter;
    }

    Optional<FullGrammerColumnReference> singlePredicateColumn(ParseTree tree, ParseTree oppositeTree) {
        if (containsQueryBoundary(tree)) {
            return Optional.empty();
        }
        Optional<FullGrammerColumnReference> explicit = singleDirectColumnNoDefault(tree);
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
                        tree, FullGrammerParseTreeAdapter.Role.SCALAR_SUBQUERY) != null
                || parseTreeAdapter.firstDescendant(
                        tree, FullGrammerParseTreeAdapter.Role.QUERY_BOUNDARY) != null;
    }

    List<FullGrammerColumnReference> directColumnList(ParseTree tree) {
        Optional<FullGrammerColumnReference> naked = nakedColumn(tree);
        if (naked.isPresent()) {
            return List.of(naked.get());
        }
        List<FullGrammerColumnReference> columns = new ArrayList<>();
        if (collectBareColumnList(tree, rowsets.defaultProjectionQualifier(), columns)) {
            return columns.stream().distinct().toList();
        }
        return directExpressionColumns(tree, rowsets.defaultProjectionQualifier());
    }

    List<FullGrammerColumnReference> directTargetItemColumns(ParseTree item, String defaultQualifier) {
        List<FullGrammerColumnReference> columns = new ArrayList<>();
        if (collectBareColumnList(item, defaultQualifier, columns)) {
            return columns.stream().distinct().toList();
        }
        return directExpressionColumns(item, defaultQualifier);
    }

    private Optional<FullGrammerColumnReference> singleDirectColumnWithDefault(ParseTree tree) {
        Optional<FullGrammerColumnReference> naked = nakedColumn(tree);
        if (naked.isPresent()) {
            return naked;
        }
        List<FullGrammerColumnReference> columns = directColumnList(tree);
        return columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
    }

    private Optional<FullGrammerColumnReference> singleDirectColumnNoDefault(ParseTree tree) {
        Optional<FullGrammerColumnReference> naked = nakedColumnNoDefault(tree);
        if (naked.isPresent()) {
            return naked;
        }
        List<FullGrammerColumnReference> columns = directColumnListNoDefault(tree);
        return columns.size() == 1 ? Optional.of(columns.get(0)) : Optional.empty();
    }

    private List<FullGrammerColumnReference> directColumnListNoDefault(ParseTree tree) {
        Optional<FullGrammerColumnReference> naked = nakedColumnNoDefault(tree);
        if (naked.isPresent()) {
            return List.of(naked.get());
        }
        return directExpressionColumns(tree, "");
    }

    private boolean collectBareColumnList(
            ParseTree tree,
            String defaultQualifier,
            List<FullGrammerColumnReference> columns
    ) {
        List<FullGrammerColumnReference> direct = directExpressionColumns(tree, defaultQualifier);
        columns.addAll(direct);
        return !direct.isEmpty();
    }

    private Optional<FullGrammerColumnReference> nakedColumn(ParseTree tree) {
        return nakedColumnWithDefault(tree, rowsets.defaultProjectionQualifier());
    }

    private Optional<FullGrammerColumnReference> nakedColumnNoDefault(ParseTree tree) {
        return nakedColumnWithDefault(tree, "");
    }

    private Optional<FullGrammerColumnReference> nakedColumnWithDefault(
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
                    return Optional.of(new FullGrammerColumnReference(qualifier, name));
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
        return parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.QUERY_BOUNDARY)
                || parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.SCALAR_SUBQUERY)
                || parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.FUNCTION_CALL)
                || parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.CASE_EXPRESSION)
                || parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.AGGREGATE_FUNCTION)
                || parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.WINDOW_FUNCTION)
                || parseTreeAdapter.functionName(tree).isPresent()
                || parseTreeAdapter.operatorSemantic(tree)
                        != FullGrammerParseTreeAdapter.OperatorSemantic.NONE
                || parseTreeAdapter.isNonColumnValue(tree);
    }

    private List<FullGrammerColumnReference> directExpressionColumns(
            ParseTree tree, String defaultQualifier
    ) {
        FullGrammerExpressionAnalysis analysis =
                expressionAnalyzer.analyzeRelationColumnExpression(tree, defaultQualifier);
        if (!"DIRECT".equals(analysis.transformType())) {
            return List.of();
        }
        int count = Math.min(analysis.sourceAliases().size(), analysis.sourceColumns().size());
        List<FullGrammerColumnReference> columns = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String alias = source.clean(analysis.sourceAliases().get(index));
            String column = source.clean(analysis.sourceColumns().get(index));
            if (alias.isBlank() || column.isBlank()) {
                return List.of();
            }
            columns.add(new FullGrammerColumnReference(alias, column));
        }
        return columns.stream().distinct().toList();
    }
}
