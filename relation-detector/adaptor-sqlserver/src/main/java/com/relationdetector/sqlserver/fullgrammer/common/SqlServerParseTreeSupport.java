package com.relationdetector.sqlserver.fullgrammer.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/** Stateless parse-tree access and identifier normalization for SQL Server collectors. */
abstract class SqlServerParseTreeSupport {
    private final Parser parser;

    SqlServerParseTreeSupport(Parser parser) {
        this.parser = parser;
    }

    final Optional<String> tableForAlias(ParseTree tree, String aliasOrTable) {
        String target = clean(aliasOrTable);
        if (target.isBlank()) {
            return Optional.empty();
        }
        for (ParserRuleContext item : descendants(tree, "table_source_item")) {
            Optional<ParserRuleContext> tableName = firstDirect(item, "full_table_name");
            if (tableName.isEmpty()) {
                continue;
            }
            String alias = firstDirect(item, "as_table_alias").flatMap(this::lastIdText).orElse("");
            String table = clean(tableName.get().getText());
            if (target.equalsIgnoreCase(alias) || target.equalsIgnoreCase(baseName(table))) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    final boolean isEqualityComparison(ParserRuleContext predicate) {
        return firstDirect(predicate, "comparison_operator")
                .map(operator -> "=".equals(operator.getText()))
                .orElse(false);
    }

    final Optional<ColumnEndpoint> singleColumnEndpoint(ParseTree expression) {
        Optional<ParserRuleContext> directColumn = directColumnExpression(expression);
        if (directColumn.isEmpty()) {
            return Optional.empty();
        }
        List<String> parts = splitQualified(clean(directColumn.get().getText())).stream()
                .map(this::cleanOne)
                .filter(part -> !part.isBlank())
                .toList();
        if (parts.size() < 2) {
            return Optional.empty();
        }
        return Optional.of(new ColumnEndpoint(parts.get(parts.size() - 2), parts.get(parts.size() - 1)));
    }

    final Optional<ParserRuleContext> directColumnExpression(ParseTree expression) {
        if (!(expression instanceof ParserRuleContext ctx)) {
            return Optional.empty();
        }
        List<ParserRuleContext> columns = directChildren(ctx, "full_column_name");
        if (columns.size() != 1) {
            List<ParserRuleContext> nestedExpressions = directChildren(ctx, "expression");
            if (columns.isEmpty() && nestedExpressions.size() == 1) {
                return directColumnExpression(nestedExpressions.get(0));
            }
            return Optional.empty();
        }
        for (ParserRuleContext child : directChildren(ctx)) {
            if (child != columns.get(0) && !ruleName(child).equals("id_")) {
                return Optional.empty();
            }
        }
        return Optional.of(columns.get(0));
    }

    final Optional<String> lastIdText(ParserRuleContext ctx) {
        List<ParserRuleContext> ids = descendants(ctx, "id_");
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(clean(ids.get(ids.size() - 1).getText()));
    }

    final Optional<ParserRuleContext> firstDirect(ParserRuleContext ctx, String ruleName) {
        for (ParserRuleContext child : directChildren(ctx)) {
            if (ruleName(child).equals(ruleName)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    final Optional<String> firstDirectText(ParserRuleContext ctx, String ruleName) {
        return firstDirect(ctx, ruleName).map(child -> clean(child.getText()));
    }

    final Optional<ParserRuleContext> firstDescendant(ParseTree tree, String ruleName) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals(ruleName)) {
            return Optional.of(ctx);
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            Optional<ParserRuleContext> match = firstDescendant(tree.getChild(index), ruleName);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    final List<ParserRuleContext> descendants(ParseTree tree, String ruleName) {
        List<ParserRuleContext> result = new ArrayList<>();
        collectDescendants(tree, ruleName, result);
        return result;
    }

    private void collectDescendants(ParseTree tree, String ruleName, List<ParserRuleContext> result) {
        if (tree instanceof ParserRuleContext ctx && ruleName(ctx).equals(ruleName)) {
            result.add(ctx);
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectDescendants(tree.getChild(index), ruleName, result);
        }
    }

    final List<ParserRuleContext> directChildren(ParserRuleContext ctx) {
        List<ParserRuleContext> result = new ArrayList<>();
        for (int index = 0; index < ctx.getChildCount(); index++) {
            if (ctx.getChild(index) instanceof ParserRuleContext child) {
                result.add(child);
            }
        }
        return result;
    }

    final List<ParserRuleContext> directChildren(ParserRuleContext ctx, String ruleName) {
        return directChildren(ctx).stream().filter(child -> ruleName(child).equals(ruleName)).toList();
    }

    final List<String> identifierList(ParserRuleContext ctx) {
        List<String> values = new ArrayList<>();
        for (ParserRuleContext id : descendants(ctx, "id_")) {
            String value = clean(id.getText());
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        if (!values.isEmpty()) {
            return values;
        }
        return splitTopLevelIdentifiers(ctx.getText());
    }

    private List<String> splitTopLevelIdentifiers(String text) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '(') {
                depth++;
            } else if (ch == ')' && depth > 0) {
                depth--;
            }
            if (ch == ',' && depth == 0) {
                addIdentifier(values, current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        addIdentifier(values, current.toString());
        return values;
    }

    private void addIdentifier(List<String> values, String raw) {
        String value = lastIdentifier(raw);
        if (!value.isBlank()) {
            values.add(value);
        }
    }

    final boolean hasDirectTerminal(ParserRuleContext ctx, String text) {
        return directTerminalTexts(ctx).stream().anyMatch(token -> token.equalsIgnoreCase(text));
    }

    final boolean containsDirectKeyword(ParserRuleContext ctx, String text) {
        return hasDirectTerminal(ctx, text);
    }

    private List<String> directTerminalTexts(ParserRuleContext ctx) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < ctx.getChildCount(); index++) {
            if (ctx.getChild(index) instanceof TerminalNode node) {
                result.add(node.getText());
            }
        }
        return result;
    }

    final String ruleName(ParserRuleContext ctx) {
        int index = ctx.getRuleIndex();
        String[] names = parser.getRuleNames();
        if (index < 0 || index >= names.length) {
            return "";
        }
        return names[index];
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
