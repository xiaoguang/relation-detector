package com.relationdetector.postgres.fullgrammer.common;

import java.util.List;

import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared state for PostgreSQL DDL typed visitors.
 *
 * <p>CN: 集中 DDL event sink 和当前 CREATE/ALTER table scope。版本 collector 只负责
 * 从 generated typed context 取字段。
 *
 * <p>EN: Holds the DDL event sink and current CREATE/ALTER table scope. Version
 * collectors only read fields from generated typed contexts.
 */
public final class PostgresDdlEventVisitorCore {
    private final PostgresDdlEventSink out;
    private String currentTable = "";

    public PostgresDdlEventVisitorCore(String sourceName) {
        this.out = new PostgresDdlEventSink(sourceName);
    }

    public PostgresDdlEventSink out() {
        return out;
    }

    public List<StructuredSqlEvent> events() {
        return out.events();
    }

    public String currentTable() {
        return currentTable;
    }

    public void withCurrentTable(String table, Runnable body) {
        String previous = currentTable;
        currentTable = table;
        try {
            body.run();
        } finally {
            currentTable = previous;
        }
    }
}
