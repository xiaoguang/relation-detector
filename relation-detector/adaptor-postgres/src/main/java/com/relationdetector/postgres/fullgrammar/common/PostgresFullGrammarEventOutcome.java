package com.relationdetector.postgres.fullgrammar.common;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.model.WarningMessage;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * CN: 封装一个 versioned PostgreSQL visitor 产生的 structured events 与可恢复 routine warnings，供 parser 生命周期原子装配结果；它不修改事件或吞掉诊断。
 * EN: Carries structured events and recoverable routine warnings from one versioned PostgreSQL visitor into parser result assembly without mutating events or suppressing diagnostics.
 */
public record PostgresFullGrammarEventOutcome(
        List<StructuredSqlEvent> events,
        List<WarningMessage> warnings,
        Map<String, Object> attributes
) {
    public PostgresFullGrammarEventOutcome(
            List<StructuredSqlEvent> events,
            List<WarningMessage> warnings
    ) {
        this(events, warnings, Map.of());
    }

    public PostgresFullGrammarEventOutcome {
        events = events == null ? List.of() : List.copyOf(events);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
