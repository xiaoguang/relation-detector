package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;

final class SourceLocationSupport {
    private final SqlStatementRecord statement;
    private final ArrayDeque<String> statementScopes = new ArrayDeque<>();
    private int nextStatementScope = 1;

    SourceLocationSupport(SqlStatementRecord statement) {
        this.statement = statement;
    }

    SourceProvenance provenance(ParserRuleContext ctx) {
        return SourceProvenance.fullGrammer(statement, line(ctx), currentStatementScope(), "typed-context");
    }

    String currentStatementScope() {
        return statementScopes.isEmpty() ? "" : statementScopes.peek();
    }

    void withStatementScope(Runnable visitor) {
        statementScopes.push("stmt-" + nextStatementScope++);
        try {
            visitor.run();
        } finally {
            statementScopes.pop();
        }
    }

    int line(ParserRuleContext ctx) {
        Token start = ctx == null ? null : ctx.getStart();
        long line = start == null ? statement.startLine() : statement.startLine() + Math.max(0, start.getLine() - 1);
        return Math.toIntExact(line);
    }

    String clean(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.indexOf('.') >= 0) {
            List<String> cleanParts = new ArrayList<>();
            for (String part : splitQualifiedName(text)) {
                String cleanPart = stripIdentifierQuotes(part.trim());
                if (!cleanPart.isBlank()) {
                    cleanParts.add(cleanPart);
                }
            }
            return String.join(".", cleanParts);
        }
        return stripIdentifierQuotes(text);
    }

    String baseName(String raw) {
        String text = clean(raw);
        int dot = text.lastIndexOf('.');
        return dot >= 0 ? clean(text.substring(dot + 1)) : text;
    }

    String firstIdentifier(ParseTree tree) {
        return identifiers(tree).stream().findFirst().orElse("");
    }

    List<String> identifiers(ParseTree tree) {
        List<String> result = new ArrayList<>();
        collectIdentifierLeaves(tree, result);
        return result.stream().map(this::clean).filter(s -> !s.isBlank()).toList();
    }

    Optional<String> aliasAfter(ParseTree tree, String marker) {
        List<String> identifiers = identifiers(tree);
        if (identifiers.isEmpty()) {
            return Optional.empty();
        }
        String first = identifiers.get(0);
        String last = identifiers.get(identifiers.size() - 1);
        if (!last.equals(first) && !last.equalsIgnoreCase(marker)) {
            return Optional.of(last);
        }
        return Optional.empty();
    }

    String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    List<String> splitQualifiedName(String text) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (ch == '.') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private String stripIdentifierQuotes(String raw) {
        String text = raw.trim();
        while ((text.startsWith("`") && text.endsWith("`"))
                || (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("'") && text.endsWith("'"))
                || (text.startsWith("[") && text.endsWith("]"))) {
            if (text.length() < 2) {
                return "";
            }
            text = text.substring(1, text.length() - 1).trim();
        }
        return text;
    }

    private void collectIdentifierLeaves(ParseTree tree, List<String> result) {
        if (tree == null) {
            return;
        }
        if (tree instanceof TerminalNode terminal) {
            String text = clean(terminal.getText());
            if (isIdentifier(text)) {
                result.add(text);
            }
            return;
        }
        for (int index = 0; index < tree.getChildCount(); index++) {
            collectIdentifierLeaves(tree.getChild(index), result);
        }
    }

    boolean isIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (Set.of("select", "from", "join", "inner", "left", "right", "full", "outer", "where", "on",
                "using", "with", "as", "update", "set", "insert", "into", "values", "merge", "when",
                "then", "case", "else", "end", "and", "or", "not", "in", "exists", "null", "true", "false", "only",
                "tablesample", "system", "materialized", "returning", "group", "by", "order", "having",
                "limit", "default").contains(lower)) {
            return false;
        }
        char first = value.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }
        for (int index = 1; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (!(Character.isLetterOrDigit(ch) || ch == '_' || ch == '$')) {
                return false;
            }
        }
        return true;
    }
}
