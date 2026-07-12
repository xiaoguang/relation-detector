package com.relationdetector.mysql.fullgrammer.common;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.fullgrammer.FullGrammerEventMerger;
import com.relationdetector.core.fullgrammer.FullGrammerNativeEventTypes;
import com.relationdetector.core.fullgrammer.FullGrammerParseTreeAdapter;
import com.relationdetector.core.fullgrammer.FullGrammerTypedSqlEventSink;
import com.relationdetector.mysql.routine.MySqlRoutineScopePolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final ArrayDeque<Map<String, String>> queryRowsets = new ArrayDeque<>();
    private int existsDepth;

    public MySqlSqlEventVisitorCore(FullGrammerTypedSqlEventSink sink) {
        this.sink = sink;
        this.parseTreeAdapter = sink.parseTreeAdapter();
        this.queryRowsets.push(new LinkedHashMap<>());
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

    public void withQueryScope(Runnable visitor) {
        queryRowsets.push(new LinkedHashMap<>());
        try {
            visitor.run();
        } finally {
            queryRowsets.pop();
        }
    }

    public void bindPhysicalRowset(String qualifiedTable, String alias) {
        String physical = sink.clean(qualifiedTable);
        if (physical.isBlank()) {
            return;
        }
        bindQueryRowset(sink.baseName(physical), physical);
        bindQueryRowset(alias, physical);
    }

    public void bindDerivedRowset(String alias) {
        bindQueryRowset(alias, "");
    }

    public Optional<String> physicalTableForAlias(String alias) {
        String key = normalize(alias);
        if (key.isBlank()) {
            return Optional.empty();
        }
        for (Map<String, String> scope : queryRowsets) {
            if (scope.containsKey(key)) {
                return Optional.ofNullable(scope.get(key)).filter(value -> !value.isBlank());
            }
        }
        return Optional.empty();
    }

    public void markNonColumnIdentifier(String identifier) {
        MySqlRoutineScopePolicy.markNonColumnIdentifier(sink, identifier);
    }

    private void bindQueryRowset(String alias, String physicalTable) {
        String key = normalize(alias);
        if (!key.isBlank()) {
            queryRowsets.peek().put(key, physicalTable == null ? "" : physicalTable);
        }
    }

    private String normalize(String value) {
        return sink.clean(value).toLowerCase(Locale.ROOT);
    }

    public String lastRowsetAlias() {
        return rowsetAliases.isEmpty() ? "" : rowsetAliases.get(rowsetAliases.size() - 1);
    }

    public String firstIdentifier(ParseTree tree) {
        List<String> identifiers = sink.identifiers(tree);
        return identifiers.isEmpty() ? "" : identifiers.get(0);
    }

    public String projectedColumnName(ParseTree expression) {
        return sink.directProjectedColumnName(expression);
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
