package com.relationdetector.core.parser.fullgrammar.expression;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarColumnReference;

import com.relationdetector.core.parser.fullgrammar.expression.DirectColumnTraceSupport;

import com.relationdetector.core.parser.fullgrammar.tree.SourceLocationSupport;

import com.relationdetector.core.parser.fullgrammar.tree.FullGrammarParseTreeAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * CN: 沿typed SELECT target/from结构解析子查询暴露的直接投影列；输入是parse tree与direct-column helper，
 * 输出是有序列集合和绑定表，供IN/tuple-IN谓词使用。上游是full-grammar facade，下游是predicate sink；
 * 本类不推断computed projection、不扫描token文本，也不创建relationship。
 *
 * <p>EN: Resolves direct columns exposed by typed SELECT target/from structures. It returns ordered columns and the
 * bound table for IN predicates. The full-grammar facade is upstream and predicate sinks are downstream; it does not
 * infer computed projections, scan token text, or create relationships.
 */
public final class SubqueryProjectionTraceSupport {
    private final SourceLocationSupport source;
    private final FullGrammarParseTreeAdapter parseTreeAdapter;
    private final DirectColumnTraceSupport directColumns;

    public SubqueryProjectionTraceSupport(
            SourceLocationSupport source,
            FullGrammarParseTreeAdapter parseTreeAdapter,
            DirectColumnTraceSupport directColumns
    ) {
        this.source = source;
        this.parseTreeAdapter = parseTreeAdapter;
        this.directColumns = directColumns;
    }

    public Optional<SelectColumns> selectColumns(ParseTree tree) {
        Optional<SelectColumns> fromTargetList = selectColumnsFromTypedSelect(tree);
        if (fromTargetList.isPresent()) {
            return fromTargetList;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            Optional<SelectColumns> selected = selectColumns(child);
            if (selected.isPresent()) {
                return selected;
            }
        }
        return Optional.empty();
    }

    private Optional<SelectColumns> selectColumnsFromTypedSelect(ParseTree tree) {
        ParseTree targetList = parseTreeAdapter.firstDescendant(
                tree, FullGrammarParseTreeAdapter.Role.SELECT_TARGET_LIST);
        ParseTree fromClause = parseTreeAdapter.firstDescendant(
                tree, FullGrammarParseTreeAdapter.Role.FROM_CLAUSE);
        if (targetList == null) {
            return Optional.empty();
        }
        FromBinding binding = fromClause == null ? new FromBinding("", "") : bindingFromFromNode(fromClause);
        List<FullGrammarColumnReference> columns = targetListColumns(targetList, binding.qualifier());
        return columns.isEmpty() ? Optional.empty() : Optional.of(new SelectColumns(columns, binding.table()));
    }

    private FromBinding bindingFromFromNode(ParseTree fromClause) {
        ParseTree tableSourceItem = parseTreeAdapter.firstDescendant(
                fromClause, FullGrammarParseTreeAdapter.Role.TABLE_SOURCE_ITEM);
        return tableSourceItem == null
                ? new FromBinding("", "")
                : parseTreeAdapter.rowsetBinding(tableSourceItem)
                .map(binding -> new FromBinding(
                        source.clean(binding.qualifier()), source.clean(binding.table())))
                .orElseGet(() -> new FromBinding("", ""));
    }

    private List<FullGrammarColumnReference> targetListColumns(ParseTree targetList, String defaultQualifier) {
        List<ParseTree> items = new ArrayList<>();
        collectTargetListItems(targetList, items);
        List<FullGrammarColumnReference> columns = new ArrayList<>();
        for (ParseTree item : items) {
            List<FullGrammarColumnReference> itemColumns =
                    directColumns.directTargetItemColumns(item, defaultQualifier);
            if (itemColumns.size() != 1) {
                return List.of();
            }
            FullGrammarColumnReference itemColumn = itemColumns.get(0);
            String alias = source.clean(itemColumn.qualifier());
            String column = source.clean(itemColumn.column());
            if (alias.isBlank() || column.isBlank()) {
                return List.of();
            }
            columns.add(new FullGrammarColumnReference(alias, column));
        }
        return columns.stream().distinct().toList();
    }

    private void collectTargetListItems(ParseTree tree, List<ParseTree> items) {
        if (parseTreeAdapter.hasRole(tree, FullGrammarParseTreeAdapter.Role.SELECT_TARGET_ITEM)) {
            items.add(tree);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectTargetListItems(child, items);
        }
    }

    public record SelectColumns(List<FullGrammarColumnReference> columns, String table) {
    }

    private record FromBinding(String qualifier, String table) {
    }
}
