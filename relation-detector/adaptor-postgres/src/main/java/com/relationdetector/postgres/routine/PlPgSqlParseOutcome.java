package com.relationdetector.postgres.routine;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

public record PlPgSqlParseOutcome(
        List<StructuredSqlEvent> events,
        List<WarningMessage> warnings,
        int parsedStatementCount,
        int unsupportedStatementCount
) {
    public PlPgSqlParseOutcome {
        events = events == null ? List.of() : List.copyOf(events);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        parsedStatementCount = Math.max(0, parsedStatementCount);
        unsupportedStatementCount = Math.max(0, unsupportedStatementCount);
    }

    public static PlPgSqlParseOutcome empty() {
        return new PlPgSqlParseOutcome(List.of(), List.of(), 0, 0);
    }
}
