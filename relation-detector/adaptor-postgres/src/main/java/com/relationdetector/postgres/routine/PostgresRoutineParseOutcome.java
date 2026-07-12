package com.relationdetector.postgres.routine;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/** Recoverable typed result for one PostgreSQL routine body. */
public record PostgresRoutineParseOutcome(
        List<StructuredSqlEvent> events,
        List<WarningMessage> warnings,
        int parsedStatementCount,
        int unsupportedStatementCount
) {
    public PostgresRoutineParseOutcome {
        events = events == null ? List.of() : List.copyOf(events);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        parsedStatementCount = Math.max(0, parsedStatementCount);
        unsupportedStatementCount = Math.max(0, unsupportedStatementCount);
    }
}
