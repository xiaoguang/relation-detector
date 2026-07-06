package com.relationdetector.core.tokenevent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared event emission helper for token-event parse-tree visitors.
 */
public final class TokenEventEventEmitter {
    private final SqlStatementRecord statement;
    private final Predicate<StructuredParseEventType> typeFilter;

    public TokenEventEventEmitter(SqlStatementRecord statement) {
        this(statement, ignored -> true);
    }

    public TokenEventEventEmitter(
            SqlStatementRecord statement,
            Predicate<StructuredParseEventType> typeFilter
    ) {
        this.statement = statement;
        this.typeFilter = typeFilter == null ? ignored -> true : typeFilter;
    }

    public Map<String, Object> attrs() {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("tokenEventNative", true);
        return attrs;
    }

    public void add(
            List<StructuredSqlEvent> events,
            StructuredParseEventType type,
            ParserRuleContext ctx,
            Map<String, Object> attrs
    ) {
        if (!typeFilter.test(type)) {
            return;
        }
        events.add(new StructuredSqlEvent(type, statement.sourceName(), line(ctx), attrs));
    }

    public void addForeignKeyEvents(
            List<StructuredSqlEvent> events,
            ParserRuleContext ctx,
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int index = 0; index < count; index++) {
            Map<String, Object> attrs = attrs();
            attrs.put("sourceTable", sourceTable);
            attrs.put("sourceColumn", sourceColumns.get(index));
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("compositePosition", index + 1);
            attrs.put("compositeSize", count);
            add(events, StructuredParseEventType.DDL_FOREIGN_KEY, ctx, attrs);
        }
    }

    public void addIndexEvent(
            List<StructuredSqlEvent> events,
            ParserRuleContext ctx,
            String table,
            String column,
            String role,
            String kind
    ) {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("table", table);
        attrs.put("column", column);
        attrs.put("role", role);
        attrs.put("kind", kind);
        add(events, StructuredParseEventType.DDL_INDEX, ctx, attrs);
    }

    public void addDdlColumnEvent(List<StructuredSqlEvent> events, ParserRuleContext ctx, String table, String column) {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return;
        }
        Map<String, Object> attrs = attrs();
        attrs.put("table", table);
        attrs.put("column", column);
        add(events, StructuredParseEventType.DDL_COLUMN, ctx, attrs);
    }

    public long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }
}
