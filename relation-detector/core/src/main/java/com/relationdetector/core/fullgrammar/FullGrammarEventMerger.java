package com.relationdetector.core.fullgrammar;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Deduplicates full-grammar native events before semantic extraction.
 *
 * <p>CN: 这个类只用于 full-grammar 事件去重和来源统计，不承担 SQL 结构识别，
 * 也不参与 relationship 或 Data Lineage 语义判断。</p>
 *
 * <p>EN: This helper only deduplicates and reports event sources for
 * full-grammar diagnostics. It does not recognize SQL structure and does not
 * make relationship or lineage decisions.</p>
 */
public final class FullGrammarEventMerger {
    private FullGrammarEventMerger() {
    }

    /**
     * Native events are deduplicated by a stable event key.
     */
    public static List<StructuredSqlEvent> merge(
            List<StructuredSqlEvent> nativeEvents,
            Set<StructuredParseEventType> nativeEventTypes
    ) {
        Map<String, StructuredSqlEvent> merged = new LinkedHashMap<>();
        for (StructuredSqlEvent event : nativeEvents) {
            merged.putIfAbsent(key(event), event);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingLong(StructuredSqlEvent::line)
                        .thenComparing(event -> event.type().name())
                        .thenComparing(FullGrammarEventMerger::key))
                .toList();
    }

    public static List<String> eventTypeNames(Set<StructuredParseEventType> eventTypes) {
        return eventTypes.stream().map(Enum::name).sorted().toList();
    }

    private static String key(StructuredSqlEvent event) {
        return event.semanticKey();
    }
}
