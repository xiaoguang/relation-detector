package com.relationdetector.contracts.parse;

import java.util.Map;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * One parser-level fact extracted before relationship scoring.
 *
 * <p>This is intentionally not a relationship. It is the intermediate
 * Token/Event stream that lets dialect parsers describe table references,
 * predicates, DDL clauses, dynamic SQL markers, and write expressions without
 * coupling the grammar layer to confidence scoring.
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
