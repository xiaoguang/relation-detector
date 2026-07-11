package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.fullgrammer.FullGrammerEventMerger;
import com.relationdetector.core.fullgrammer.FullGrammerNativeEventTypes;
import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter;
import com.relationdetector.core.fullgrammer.FullGrammerTypedSqlEventSink;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Shared state and sink helpers for MySQL full-grammer SQL visitors.
 *
 * <p>Version visitors still decide structure from their generated contexts; this
 * core only owns common state and event merging.
 */
public final class MySqlSqlEventVisitorCore {
    private final FullGrammerTypedSqlEventSink sink;
    private final FullGrammerParseTreeAdapter parseTreeAdapter;
    private final List<String> rowsetAliases = new ArrayList<>();
    private int existsDepth;

    public MySqlSqlEventVisitorCore(FullGrammerTypedSqlEventSink sink) {
        this.sink = sink;
        this.parseTreeAdapter = sink.parseTreeAdapter();
    }

    public FullGrammerTypedSqlEventSink sink() {
        return sink;
    }

    public List<StructuredSqlEvent> mergedEvents() {
        return FullGrammerEventMerger.merge(sink.events(), FullGrammerNativeEventTypes.MYSQL_NATIVE_EVENTS);
    }

    public void rememberRowset(String aliasOrTable) {
        String clean = sink.clean(aliasOrTable);
        if (!clean.isBlank()) {
            rowsetAliases.add(clean);
        }
    }

    public String lastRowsetAlias() {
        return rowsetAliases.isEmpty() ? "" : rowsetAliases.get(rowsetAliases.size() - 1);
    }

    public String firstIdentifier(ParseTree tree) {
        List<String> identifiers = sink.identifiers(tree);
        return identifiers.isEmpty() ? "" : identifiers.get(0);
    }

    public String projectedColumnName(ParseTree expression) {
        List<String> identifiers = sink.identifiers(expression);
        return identifiers.isEmpty() ? "" : identifiers.get(identifiers.size() - 1);
    }

    public ColumnParts columnParts(String raw) {
        String clean = sink.clean(raw);
        int dot = clean.lastIndexOf('.');
        if (dot < 0) {
            return new ColumnParts("", clean);
        }
        return new ColumnParts(sink.clean(clean.substring(0, dot)), sink.clean(clean.substring(dot + 1)));
    }

    public void enterExists() {
        existsDepth++;
    }

    public void leaveExists() {
        existsDepth--;
    }

    public boolean inExists() {
        return existsDepth > 0;
    }

    public boolean isExpressionContext(org.antlr.v4.runtime.ParserRuleContext ctx) {
        return parseTreeAdapter.hasRole(ctx, FullGrammerParseTreeAdapter.Role.EXPRESSION);
    }

    public record ColumnParts(String qualifier, String column) {
    }
}
