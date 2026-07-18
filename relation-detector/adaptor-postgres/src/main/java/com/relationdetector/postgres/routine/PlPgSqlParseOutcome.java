package com.relationdetector.postgres.routine;

import java.util.List;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * CN: 汇总一次 PL/pgSQL body parse 已恢复的 events、warnings 和 statement counts；调用方必须同时消费事件和诊断，不能因局部失败丢弃整个 routine。
 * EN: Summarizes recovered events, warnings, and statement counts from one PL/pgSQL body parse. Callers must consume both events and diagnostics rather than dropping the routine after a local failure.
 */
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
