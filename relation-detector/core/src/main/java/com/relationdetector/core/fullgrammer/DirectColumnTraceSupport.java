package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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
        Optional<FullGrammerColumnReference> explicit = singleDirectColumnNoDefault(tree);
        if (explicit.isPresent()) {
            return explicit;
        }
        if (singleDirectColumnNoDefault(oppositeTree).isEmpty()) {
            return Optional.empty();
        }
        return singleDirectColumnWithDefault(tree);
    }

    Optional<FullGrammerColumnReference> singlePredicateColumnInChildren(
            ParseTree tree,
            int startInclusive,
            int endExclusive,
            int oppositeStartInclusive,
            int oppositeEndExclusive
    ) {
        Optional<FullGrammerColumnReference> explicit =
                singleDirectColumnInChildrenNoDefault(tree, startInclusive, endExclusive);
        if (explicit.isPresent()) {
            return explicit;
        }
        if (singleDirectColumnInChildrenNoDefault(
                tree, oppositeStartInclusive, oppositeEndExclusive).isEmpty()) {
            return Optional.empty();
        }
        return singleDirectColumnInChildrenWithDefault(tree, startInclusive, endExclusive);
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

    private Optional<FullGrammerColumnReference> singleDirectColumnInChildrenWithDefault(
            ParseTree tree, int startInclusive, int endExclusive
    ) {
        List<FullGrammerColumnReference> columns = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            Optional<FullGrammerColumnReference> naked = nakedColumn(tree.getChild(index));
            if (naked.isPresent()) {
                columns.add(naked.get());
            } else {
                columns.addAll(directColumnList(tree.getChild(index)));
            }
        }
        List<FullGrammerColumnReference> unique = columns.stream().distinct().toList();
        return unique.size() == 1 ? Optional.of(unique.get(0)) : Optional.empty();
    }

    private Optional<FullGrammerColumnReference> singleDirectColumnInChildrenNoDefault(
            ParseTree tree, int startInclusive, int endExclusive
    ) {
        List<FullGrammerColumnReference> columns = new ArrayList<>();
        for (int index = startInclusive; index < endExclusive; index++) {
            Optional<FullGrammerColumnReference> naked = nakedColumnNoDefault(tree.getChild(index));
            if (naked.isPresent()) {
                columns.add(naked.get());
            } else {
                columns.addAll(directColumnListNoDefault(tree.getChild(index)));
            }
        }
        List<FullGrammerColumnReference> unique = columns.stream().distinct().toList();
        return unique.size() == 1 ? Optional.of(unique.get(0)) : Optional.empty();
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
        if (tree == null) {
            return false;
        }
        Optional<FullGrammerColumnReference> naked = nakedColumnWithDefault(tree, defaultQualifier);
        if (naked.isPresent()) {
            columns.add(naked.get());
            return true;
        }
        if (tree instanceof TerminalNode terminal) {
            String text = terminal.getText();
            return text.equals(",") || text.equals("(") || text.equals(")");
        }
        if (tree.getChildCount() == 0) {
            return false;
        }
        boolean sawColumn = false;
        for (int index = 0; index < tree.getChildCount(); index++) {
            List<FullGrammerColumnReference> childColumns = new ArrayList<>();
            if (!collectBareColumnList(tree.getChild(index), defaultQualifier, childColumns)) {
                return false;
            }
            if (!childColumns.isEmpty()) {
                sawColumn = true;
                columns.addAll(childColumns);
            }
        }
        return sawColumn;
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
        if (current == null
                || !parseTreeAdapter.hasRole(current, FullGrammerParseTreeAdapter.Role.COLUMN_REFERENCE)) {
            return Optional.empty();
        }
        List<String> parts = source.splitQualifiedName(source.clean(current.getText())).stream()
                .map(source::clean)
                .filter(part -> !part.isBlank())
                .toList();
        if (parts.size() >= 2
                && source.isIdentifier(parts.get(parts.size() - 2))
                && source.isIdentifier(parts.get(parts.size() - 1))) {
            String qualifier = parts.get(parts.size() - 2);
            String column = parts.get(parts.size() - 1);
            if (expressionAnalyzer.isNonColumnIdentifier(qualifier)
                    || expressionAnalyzer.isNonColumnIdentifier(column)) {
                return Optional.empty();
            }
            return Optional.of(new FullGrammerColumnReference(qualifier, column));
        }
        if (parts.size() == 1 && source.isIdentifier(parts.get(0))) {
            String column = parts.get(0);
            if (!defaultQualifier.isBlank() && !expressionAnalyzer.isNonColumnIdentifier(column)) {
                return Optional.of(new FullGrammerColumnReference(defaultQualifier, column));
            }
        }
        return Optional.empty();
    }

    private ParseTree unwrapTransparentSingleChild(ParseTree tree) {
        ParseTree current = tree;
        while (current != null && !(current instanceof TerminalNode) && current.getChildCount() == 1) {
            current = current.getChild(0);
        }
        return current;
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
