package com.relationdetector.oracle.fullgrammer.common;

import java.util.List;

import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared Oracle DDL event post-processing.
 */
public final class OracleDdlEventVisitorCore {
    private OracleDdlEventVisitorCore() {
    }

    public static List<StructuredSqlEvent> ddlEvents(List<StructuredSqlEvent> events) {
        return events.stream()
                .filter(event -> event.type().name().startsWith("DDL_"))
                .toList();
    }
}
