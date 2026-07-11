package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

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
        int selectIndex = directKeywordIndex(tree, "select");
        int fromIndex = directKeywordIndex(tree, "from");
        if (selectIndex >= 0 && fromIndex > selectIndex) {
            FromBinding binding = bindingFromDirectFrom(tree, fromIndex + 1);
            List<FullGrammerColumnReference> columns = new ArrayList<>();
            for (int index = selectIndex + 1; index < fromIndex; index++) {
                ParseTree child = tree.getChild(index);
                if (child instanceof TerminalNode terminal && terminal.getText().equals(",")) {
                    continue;
                }
                List<FullGrammerColumnReference> childColumns =
                        directColumns.directTargetItemColumns(child, binding.qualifier());
                if (childColumns.isEmpty()) {
                    return Optional.empty();
                }
                for (FullGrammerColumnReference childColumn : childColumns) {
                    String alias = source.clean(childColumn.qualifier());
                    String column = source.clean(childColumn.column());
                    if (alias.isBlank() || column.isBlank()) {
                        return Optional.empty();
                    }
                    columns.add(new FullGrammerColumnReference(alias, column));
                }
            }
            return columns.isEmpty()
                    ? Optional.empty()
                    : Optional.of(new SelectColumns(columns.stream().distinct().toList(), binding.table()));
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            Optional<SelectColumns> selected = selectColumns(tree.getChild(index));
            if (selected.isPresent()) {
                return selected;
            }
        }
        return Optional.empty();
    }

    private Optional<SelectColumns> selectColumnsFromTypedSelect(ParseTree tree) {
        if (directKeywordIndex(tree, "select") < 0) {
            return Optional.empty();
        }
        ParseTree targetList = parseTreeAdapter.firstDirectChild(
                tree, FullGrammerParseTreeAdapter.Role.SELECT_TARGET_LIST);
        ParseTree fromClause = parseTreeAdapter.firstDirectChild(
                tree, FullGrammerParseTreeAdapter.Role.FROM_CLAUSE);
        if (targetList == null || fromClause == null) {
            return Optional.empty();
        }
        FromBinding binding = bindingFromFromNode(fromClause);
        List<FullGrammerColumnReference> columns = targetListColumns(targetList, binding.qualifier());
        return columns.isEmpty() ? Optional.empty() : Optional.of(new SelectColumns(columns, binding.table()));
    }

    private FromBinding bindingFromFromNode(ParseTree fromClause) {
        ParseTree tableSourceItem = parseTreeAdapter.firstDescendant(
                fromClause, FullGrammerParseTreeAdapter.Role.TABLE_SOURCE_ITEM);
        if (tableSourceItem != null) {
            return bindingFromTableSourceItem(tableSourceItem);
        }
        List<String> identifiers = source.identifiers(fromClause);
        if (identifiers.isEmpty()) {
            return new FromBinding("", "");
        }
        String table = identifiers.get(0);
        String qualifier = identifiers.size() >= 2 ? identifiers.get(1) : table;
        return new FromBinding(qualifier, table);
    }

    private FromBinding bindingFromTableSourceItem(ParseTree tableSourceItem) {
        List<String> identifiers = source.identifiers(tableSourceItem);
        if (identifiers.isEmpty()) {
            return new FromBinding("", "");
        }
        if (identifiers.size() >= 3) {
            String table = identifiers.get(identifiers.size() - 2);
            String qualifier = identifiers.get(identifiers.size() - 1);
            return new FromBinding(qualifier, table);
        }
        if (identifiers.size() == 2) {
            String text = tableSourceItem.getText();
            if (text.contains(".")) {
                String table = identifiers.get(1);
                return new FromBinding(table, table);
            }
            return new FromBinding(identifiers.get(1), identifiers.get(0));
        }
        String table = identifiers.get(0);
        return new FromBinding(table, table);
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
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectTargetListItems(tree.getChild(index), items);
        }
    }

    private FromBinding bindingFromDirectFrom(ParseTree tree, int startIndex) {
        for (int index = startIndex; index < tree.getChildCount(); index++) {
            ParseTree child = tree.getChild(index);
            if (child instanceof TerminalNode terminal
                    && Set.of("where", "group", "having", "order", "limit", "union")
                    .contains(terminal.getText().toLowerCase(Locale.ROOT))) {
                break;
            }
            List<String> identifiers = source.identifiers(child);
            if (!identifiers.isEmpty()) {
                String table = identifiers.get(0);
                String qualifier = identifiers.size() >= 2 ? identifiers.get(1) : table;
                return new FromBinding(qualifier, table);
            }
        }
        return new FromBinding("", "");
    }

    private int directKeywordIndex(ParseTree tree, String keyword) {
        for (int index = 0; index < tree.getChildCount(); index++) {
            if (tree.getChild(index) instanceof TerminalNode terminal
                    && terminal.getText().equalsIgnoreCase(keyword)) {
                return index;
            }
        }
        return -1;
    }

    record SelectColumns(List<FullGrammerColumnReference> columns, String table) {
    }

    private record FromBinding(String qualifier, String table) {
    }
}
