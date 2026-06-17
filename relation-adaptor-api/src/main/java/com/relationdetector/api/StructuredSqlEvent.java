package com.relationdetector.api;

import java.util.Map;

import com.relationdetector.api.Enums.StructuredParseEventType;

/**
 * One parser-level fact extracted before relationship scoring.
 *
 * <p>ANTLR migration note: this is intentionally not a relationship. It is the
 * intermediate event stream that lets dialect parsers describe table
 * references, predicates, DDL clauses, dynamic SQL markers, and comparison
 * diagnostics without coupling the grammar layer to confidence scoring.
 */
public record StructuredSqlEvent(
        StructuredParseEventType type,
        String sourceName,
        long line,
        Map<String, Object> attributes
) {
    public StructuredSqlEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
