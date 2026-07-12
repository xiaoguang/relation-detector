package com.relationdetector.postgres.fullgrammar.common;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Events and recoverable routine diagnostics emitted by a version visitor. */
public record PostgresFullGrammarEventOutcome(
        List<StructuredSqlEvent> events,
        List<WarningMessage> warnings
) {
    public PostgresFullGrammarEventOutcome {
        events = events == null ? List.of() : List.copyOf(events);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
