package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.tree.ParseTree;

/** Resolves the direct projection exposed by a typed SELECT subquery. */
final class SubqueryProjectionTraceSupport {
    private final SourceLocationSupport source;
    private final FullGrammerParseTreeAdapter parseTreeAdapter;
    private final DirectColumnTraceSupport directColumns;

    SubqueryProjectionTraceSupport(
            SourceLocationSupport source,
            FullGrammerParseTreeAdapter parseTreeAdapter,
            DirectColumnTraceSupport directColumns
    ) {
        this.source = source;
        this.parseTreeAdapter = parseTreeAdapter;
        this.directColumns = directColumns;
    }

    Optional<SelectColumns> selectColumns(ParseTree tree) {
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
                tree, FullGrammerParseTreeAdapter.Role.SELECT_TARGET_LIST);
        ParseTree fromClause = parseTreeAdapter.firstDescendant(
                tree, FullGrammerParseTreeAdapter.Role.FROM_CLAUSE);
        if (targetList == null) {
            return Optional.empty();
        }
        FromBinding binding = fromClause == null ? new FromBinding("", "") : bindingFromFromNode(fromClause);
        List<FullGrammerColumnReference> columns = targetListColumns(targetList, binding.qualifier());
        return columns.isEmpty() ? Optional.empty() : Optional.of(new SelectColumns(columns, binding.table()));
    }

    private FromBinding bindingFromFromNode(ParseTree fromClause) {
        ParseTree tableSourceItem = parseTreeAdapter.firstDescendant(
                fromClause, FullGrammerParseTreeAdapter.Role.TABLE_SOURCE_ITEM);
        return tableSourceItem == null
                ? new FromBinding("", "")
                : parseTreeAdapter.rowsetBinding(tableSourceItem)
                .map(binding -> new FromBinding(
                        source.clean(binding.qualifier()), source.clean(binding.table())))
                .orElseGet(() -> new FromBinding("", ""));
    }

    private List<FullGrammerColumnReference> targetListColumns(ParseTree targetList, String defaultQualifier) {
        List<ParseTree> items = new ArrayList<>();
        collectTargetListItems(targetList, items);
        List<FullGrammerColumnReference> columns = new ArrayList<>();
        for (ParseTree item : items) {
            List<FullGrammerColumnReference> itemColumns =
                    directColumns.directTargetItemColumns(item, defaultQualifier);
            if (itemColumns.size() != 1) {
                return List.of();
            }
            FullGrammerColumnReference itemColumn = itemColumns.get(0);
            String alias = source.clean(itemColumn.qualifier());
            String column = source.clean(itemColumn.column());
            if (alias.isBlank() || column.isBlank()) {
                return List.of();
            }
            columns.add(new FullGrammerColumnReference(alias, column));
        }
        return columns.stream().distinct().toList();
    }

    private void collectTargetListItems(ParseTree tree, List<ParseTree> items) {
        if (parseTreeAdapter.hasRole(tree, FullGrammerParseTreeAdapter.Role.SELECT_TARGET_ITEM)) {
            items.add(tree);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectTargetListItems(child, items);
        }
    }

    record SelectColumns(List<FullGrammerColumnReference> columns, String table) {
    }

    private record FromBinding(String qualifier, String table) {
    }
}
