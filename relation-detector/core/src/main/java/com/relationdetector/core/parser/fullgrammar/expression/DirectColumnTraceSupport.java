package com.relationdetector.core.parser.fullgrammar.expression;

import com.relationdetector.core.parser.fullgrammar.event.RowsetScopeSink;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarExpressionAnalyzer;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarExpressionAnalysis;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarColumnReference;

import com.relationdetector.core.parser.fullgrammar.tree.SourceLocationSupport;

import com.relationdetector.core.parser.fullgrammar.tree.FullGrammarParseTreeAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * CN: 从typed expression tree提取可证明的单一直接列引用；输入由tree adapter与expression analyzer提供，
 * 输出供predicate/projection sinks消费。上游是full-grammar facade，下游是event sinks；本类不定义谓词语义、
 * 不解析raw SQL，也不把函数或子查询降级成直接列。
 *
 * <p>EN: Extracts a provable single direct-column reference from a typed expression tree. The full-grammar facade is
 * upstream and predicate/projection sinks consume its output. It does not own predicate semantics, parse raw SQL, or
 * collapse functions and subqueries into direct columns.
 */
public final class DirectColumnTraceSupport {
    private final SourceLocationSupport source;
    private final RowsetScopeSink rowsets;
    private final FullGrammarExpressionAnalyzer expressionAnalyzer;
    private final FullGrammarParseTreeAdapter parseTreeAdapter;

    public DirectColumnTraceSupport(
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

    public Optional<FullGrammarColumnReference> singlePredicateColumn(ParseTree tree, ParseTree oppositeTree) {
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

    public List<FullGrammarColumnReference> directColumnList(ParseTree tree) {
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

    public List<FullGrammarColumnReference> directTargetItemColumns(ParseTree item, String defaultQualifier) {
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
