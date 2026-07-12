package com.relationdetector.core.fullgrammar;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;

final class SourceLocationSupport {
    private final SqlStatementRecord statement;
    private final FullGrammarParseTreeAdapter parseTreeAdapter;
    private final ArrayDeque<String> statementScopes = new ArrayDeque<>();
    private int nextStatementScope = 1;

    SourceLocationSupport(SqlStatementRecord statement, FullGrammarParseTreeAdapter parseTreeAdapter) {
        this.statement = statement;
        this.parseTreeAdapter = parseTreeAdapter;
    }

    SourceProvenance provenance(ParserRuleContext ctx) {
        return SourceProvenance.fullGrammar(statement, line(ctx), currentStatementScope(), "typed-context");
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
        List<String> parts = FullGrammarIdentifiers.qualifiedParts(raw);
        return parts.isEmpty() ? FullGrammarIdentifiers.clean(raw) : String.join(".", parts);
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
        List<String> identifiers = new ArrayList<>();
        collectTypedIdentifiers(tree, identifiers);
        return identifiers.stream()
                .map(this::clean)
                .filter(identifier -> !identifier.isBlank())
                .toList();
    }

    private void collectTypedIdentifiers(ParseTree tree, List<String> result) {
        if (tree == null) {
            return;
        }
        List<String> direct = parseTreeAdapter.identifiers(tree);
        if (!direct.isEmpty()) {
            result.addAll(direct);
            return;
        }
        for (ParseTree child : parseTreeAdapter.typedChildren(tree)) {
            collectTypedIdentifiers(child, result);
        }
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

}
