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

    public long line(ParserRuleContext ctx) {
        if (ctx == null || ctx.getStart() == null) {
            return statement.startLine();
        }
        return statement.startLine() + Math.max(0, ctx.getStart().getLine() - 1);
    }
}
