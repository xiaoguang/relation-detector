package com.relationdetector.api;

import java.util.List;
import java.util.Map;

/**
 * Structured parse output from the ANTLR-backed Token/Event parser frontend.
 *
 * <p>The final JSON relationship model remains based on
 * {@link RelationshipCandidate}. This result is a diagnostic and extraction
 * bridge: visitors consume {@link #events()}, operators can inspect
 * {@link #warnings()}, and parser diagnostics can keep backend/dialect
 * provenance without changing the public relationship model.
 */
public record StructuredParseResult(
        String backend,
        String dialect,
        String sourceName,
        List<StructuredSqlEvent> events,
        List<WarningMessage> warnings,
        Map<String, Object> attributes
) {
    public StructuredParseResult {
        events = events == null ? List.of() : List.copyOf(events);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
