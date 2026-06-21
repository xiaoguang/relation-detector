package com.relationdetector.core.fullgrammer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

public final class FullGrammerEventMerger {
    private FullGrammerEventMerger() {
    }

    public static List<StructuredSqlEvent> merge(
            List<StructuredSqlEvent> nativeEvents,
            List<StructuredSqlEvent> delegatedEvents,
            Set<StructuredParseEventType> nativeEventTypes
    ) {
        Map<String, StructuredSqlEvent> merged = new LinkedHashMap<>();
        for (StructuredSqlEvent event : nativeEvents) {
            merged.putIfAbsent(key(event), event);
        }
        for (StructuredSqlEvent event : delegatedEvents) {
            if (nativeEventTypes.contains(event.type())) {
                continue;
            }
            merged.putIfAbsent(key(event), event);
        }
        return merged.values().stream()
                .sorted(Comparator.comparingLong(StructuredSqlEvent::line)
                        .thenComparing(event -> event.type().name())
                        .thenComparing(FullGrammerEventMerger::key))
                .toList();
    }

    public static List<String> eventTypeNames(Set<StructuredParseEventType> eventTypes) {
        return eventTypes.stream().map(Enum::name).sorted().toList();
    }

    public static List<String> delegatedEventTypeNames(List<StructuredSqlEvent> delegatedEvents, Set<StructuredParseEventType> nativeEventTypes) {
        return delegatedEvents.stream()
                .map(StructuredSqlEvent::type)
                .filter(type -> !nativeEventTypes.contains(type))
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
    }

    public static List<String> bridgedEventTypeNames(
            List<StructuredSqlEvent> nativeEvents,
            Set<StructuredParseEventType> bridgedEventTypes
    ) {
        return nativeEvents.stream()
                .map(StructuredSqlEvent::type)
                .filter(bridgedEventTypes::contains)
                .map(Enum::name)
                .distinct()
                .sorted()
                .toList();
    }

    private static String key(StructuredSqlEvent event) {
        List<String> attributes = new ArrayList<>();
        event.attributes().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> attributes.add(entry.getKey() + "=" + String.valueOf(entry.getValue())));
        return event.type().name() + "|" + event.line() + "|" + String.join("|", attributes);
    }
}
