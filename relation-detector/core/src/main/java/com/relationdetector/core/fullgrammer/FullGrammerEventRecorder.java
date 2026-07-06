package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

final class FullGrammerEventRecorder {
    private final SqlStatementRecord statement;
    private final SourceLocationSupport source;
    private final List<StructuredSqlEvent> events = new ArrayList<>();
    private final Set<String> eventKeys = new LinkedHashSet<>();

    FullGrammerEventRecorder(SqlStatementRecord statement, SourceLocationSupport source) {
        this.statement = statement;
        this.source = source;
    }

    List<StructuredSqlEvent> events() {
        return events;
    }

    void add(ParserRuleContext ctx, StructuredParseEventType type, Map<String, Object> attributes) {
        StructuredSqlEvent event = new StructuredSqlEvent(type, statement.sourceName(), source.line(ctx), attributes);
        String key = type.name() + "|" + event.line() + "|" + attributes;
        if (eventKeys.add(key)) {
            events.add(event);
        }
    }
}
