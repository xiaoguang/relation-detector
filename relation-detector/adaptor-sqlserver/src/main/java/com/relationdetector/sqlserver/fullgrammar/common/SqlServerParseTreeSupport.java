package com.relationdetector.sqlserver.fullgrammar.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter;
import com.relationdetector.core.fullgrammar.FullGrammarColumnReference;
import com.relationdetector.core.fullgrammar.FullGrammarParseTreeAdapter.Role;

/**
 *
 * Stateless parse-tree access and identifier normalization for SQL Server collectors.
 */
abstract class SqlServerParseTreeSupport {
    private final FullGrammarParseTreeAdapter adapter;

    SqlServerParseTreeSupport(FullGrammarParseTreeAdapter adapter) {
        this.adapter = adapter;
    }

    final Optional<String> tableForAlias(ParseTree tree, String aliasOrTable) {
        String target = clean(aliasOrTable);
        if (target.isBlank()) {
            return Optional.empty();
        }
        for (ParserRuleContext item : descendants(tree, Role.TABLE_SOURCE_ITEM)) {
            Optional<FullGrammarParseTreeAdapter.RowsetBinding> binding = adapter.rowsetBinding(item);
            if (binding.isEmpty()) {
                continue;
            }
            String alias = clean(binding.get().qualifier());
            String table = clean(binding.get().table());
            if (target.equalsIgnoreCase(alias) || target.equalsIgnoreCase(table)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    final boolean isEqualityComparison(ParserRuleContext predicate) {
        return !adapter.directEqualities(predicate).isEmpty();
    }

    final Optional<ColumnEndpoint> singleColumnEndpoint(ParseTree expression) {
        Optional<FullGrammarColumnReference> directColumn = directColumnExpression(expression);
        if (directColumn.isEmpty()) {
            return Optional.empty();
        }
        String qualifier = clean(directColumn.get().qualifier());
        String column = clean(directColumn.get().column());
        if (qualifier.isBlank() || column.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ColumnEndpoint(qualifier, column));
    }

    final Optional<FullGrammarColumnReference> directColumnExpression(ParseTree expression) {
        Optional<FullGrammarColumnReference> direct = adapter.directColumn(expression);
        if (direct.isPresent()) return direct;
        List<ParseTree> children = adapter.typedChildren(expression);
        return children.size() == 1 ? directColumnExpression(children.get(0)) : Optional.empty();
    }

    final Optional<String> lastIdText(ParserRuleContext ctx) {
        List<String> ids = typedIdentifiers(ctx);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(clean(ids.get(ids.size() - 1)));
    }

    final Optional<ParserRuleContext> firstDirect(ParserRuleContext ctx, Role role) {
        for (ParserRuleContext child : directChildren(ctx)) {
            if (hasRole(child, role)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    final Optional<String> firstDirectText(ParserRuleContext ctx, Role role) {
        return firstDirect(ctx, role).map(child -> clean(child.getText()));
    }

    final Optional<ParserRuleContext> firstDescendant(ParseTree tree, Role role) {
        if (tree instanceof ParserRuleContext ctx && hasRole(ctx, role)) {
            return Optional.of(ctx);
        }
        for (ParseTree child : typedChildren(tree)) {
            Optional<ParserRuleContext> match = firstDescendant(child, role);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    final List<ParserRuleContext> descendants(ParseTree tree, Role role) {
        List<ParserRuleContext> result = new ArrayList<>();
        collectDescendants(tree, role, result);
        return result;
    }

    private void collectDescendants(ParseTree tree, Role role, List<ParserRuleContext> result) {
        if (tree instanceof ParserRuleContext ctx && hasRole(ctx, role)) {
            result.add(ctx);
        }
        for (ParseTree child : typedChildren(tree)) {
            collectDescendants(child, role, result);
        }
    }

    final List<ParserRuleContext> directChildren(ParserRuleContext ctx) {
        return typedChildren(ctx).stream().map(ParserRuleContext.class::cast).toList();
    }

    final List<ParserRuleContext> directChildren(ParserRuleContext ctx, Role role) {
        return directChildren(ctx).stream().filter(child -> hasRole(child, role)).toList();
    }

    final List<String> identifierList(ParserRuleContext ctx) {
        return typedIdentifiers(ctx).stream().map(this::clean).filter(value -> !value.isBlank()).toList();
    }

    private List<String> typedIdentifiers(ParseTree tree) {
        List<String> result = new ArrayList<>();
        collectTypedIdentifiers(tree, result);
        return result;
    }

    private void collectTypedIdentifiers(ParseTree tree, List<String> result) {
        List<String> direct = adapter.identifiers(tree);
        if (!direct.isEmpty()) {
            result.addAll(direct);
            return;
        }
        for (ParseTree child : adapter.typedChildren(tree)) {
            collectTypedIdentifiers(child, result);
        }
    }

    final boolean isExistsPredicate(ParseTree tree) { return adapter.isExistsPredicate(tree); }

    final boolean isInPredicate(ParseTree tree) { return adapter.isInPredicate(tree); }

    final List<FullGrammarParseTreeAdapter.EqualityOperands> directEqualities(ParseTree tree) {
        return adapter.directEqualities(tree);
    }

    final FullGrammarParseTreeAdapter.DdlConstraintSemantic ddlConstraintSemantic(ParseTree tree) {
        return adapter.ddlConstraintSemantic(tree);
    }

    final boolean hasRole(ParseTree tree, Role role) {
        return adapter.hasRole(tree, role);
    }

    final List<ParseTree> typedChildren(ParseTree tree) {
        return adapter.typedChildren(tree);
    }

    final long line(ParserRuleContext ctx) {
        return ctx.getStart() == null ? 1 : ctx.getStart().getLine();
    }

    final boolean isLocalTemp(String table) {
        return clean(table).startsWith("#");
    }

    final String baseName(String raw) {
        String value = clean(raw);
        int dot = value.lastIndexOf('.');
        return dot >= 0 ? value.substring(dot + 1) : value;
    }

    final String qualifiedTable(String raw) {
        return clean(raw);
    }

    final String lastIdentifier(String raw) {
        return baseName(raw);
    }

    final String clean(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.strip();
        if (clean.isBlank()) {
            return "";
        }
        clean = removeWhitespace(clean);
        List<String> parts = splitQualified(clean);
        if (parts.size() > 1) {
            return parts.stream().map(this::cleanOne).filter(part -> !part.isBlank())
                    .reduce((left, right) -> left + "." + right).orElse("");
        }
        return cleanOne(clean);
    }

    private String cleanOne(String value) {
        String clean = value.strip();
        while ((clean.startsWith("[") && clean.endsWith("]"))
                || (clean.startsWith("\"") && clean.endsWith("\""))
                || (clean.startsWith("`") && clean.endsWith("`"))) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean;
    }

    private List<String> splitQualified(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int bracketDepth = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            }
            if (ch == '.' && bracketDepth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private String removeWhitespace(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!Character.isWhitespace(ch)) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    record ColumnEndpoint(String qualifier, String column) {
    }
}
