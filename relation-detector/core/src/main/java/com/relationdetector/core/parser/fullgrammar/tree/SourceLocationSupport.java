package com.relationdetector.core.parser.fullgrammar.tree;

import com.relationdetector.core.parser.fullgrammar.expression.FullGrammarIdentifiers;

import com.relationdetector.core.parser.fullgrammar.tree.FullGrammarParseTreeAdapter;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import com.relationdetector.contracts.parse.SourceProvenance;
import com.relationdetector.contracts.parse.SqlStatementRecord;

/**
 * CN: 将typed parser context映射到当前statement内的行号、statement scope与规范identifier；输入是单次解析的
 * statement和typed tree adapter，输出是provenance与清理后的名称。上游是full-grammar facade，下游是event sinks；
 * 本类不遍历方言语法、不生成事实，也不从raw SQL猜测结构。
 *
 * <p>EN: Maps typed parser contexts to line numbers, statement scopes, and normalized identifiers for one statement.
 * The full-grammar facade is upstream and event sinks are downstream. It does not traverse dialect grammars, create
 * facts, or infer structure from raw SQL.
 */
public final class SourceLocationSupport {
    private final SqlStatementRecord statement;
    private final FullGrammarParseTreeAdapter parseTreeAdapter;
    private final ArrayDeque<String> statementScopes = new ArrayDeque<>();
    private int nextStatementScope = 1;

    public SourceLocationSupport(SqlStatementRecord statement, FullGrammarParseTreeAdapter parseTreeAdapter) {
        this.statement = statement;
        this.parseTreeAdapter = parseTreeAdapter;
    }

    public SourceProvenance provenance(ParserRuleContext ctx) {
        return SourceProvenance.fullGrammar(statement, line(ctx), currentStatementScope(), "typed-context");
    }

    public String currentStatementScope() {
        return statementScopes.isEmpty() ? "" : statementScopes.peek();
    }

    public void withStatementScope(Runnable visitor) {
        statementScopes.push("stmt-" + nextStatementScope++);
        try {
            visitor.run();
        } finally {
            statementScopes.pop();
        }
    }

    public int line(ParserRuleContext ctx) {
        Token start = ctx == null ? null : ctx.getStart();
        long line = start == null ? statement.startLine() : statement.startLine() + Math.max(0, start.getLine() - 1);
        return Math.toIntExact(line);
    }

    public String clean(String raw) {
        List<String> parts = FullGrammarIdentifiers.qualifiedParts(raw);
        return parts.isEmpty() ? FullGrammarIdentifiers.clean(raw) : String.join(".", parts);
    }

    public String baseName(String raw) {
        List<String> parts = FullGrammarIdentifiers.qualifiedParts(raw);
        return parts.isEmpty() ? FullGrammarIdentifiers.clean(raw) : parts.get(parts.size() - 1);
    }

    public String firstIdentifier(ParseTree tree) {
        return identifiers(tree).stream().findFirst().orElse("");
    }

    public List<String> identifiers(ParseTree tree) {
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

    public Optional<String> aliasAfter(ParseTree tree, String marker) {
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

    public String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

}
