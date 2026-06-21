package com.relationdetector.core.fullgrammer;

import java.util.LinkedHashMap;
import java.util.Map;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

final class FullGrammerEventSink {
    private final SqlStatementRecord statement;
    private final String contextSource;
    private final java.util.List<StructuredSqlEvent> events = new java.util.ArrayList<>();

    FullGrammerEventSink(SqlStatementRecord statement, String contextSource) {
        this.statement = statement;
        this.contextSource = contextSource;
    }

    void add(StructuredSqlEvent event) {
        Map<String, Object> attributes = new LinkedHashMap<>(event.attributes());
        attributes.put("tokenEventNative", true);
        attributes.put("fullGrammerNative", true);
        attributes.put("fullGrammerContextSource", contextSource);
        events.add(new StructuredSqlEvent(event.type(), statement.sourceName(), event.line(), attributes));
    }

    boolean hasType(StructuredParseEventType type) {
        return events.stream().anyMatch(event -> event.type() == type);
    }

    java.util.List<StructuredSqlEvent> events() {
        return java.util.List.copyOf(events);
    }
}
