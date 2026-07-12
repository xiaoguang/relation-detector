package com.relationdetector.mysql.fullgrammar.common;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import java.util.List;

/**
 * Shared state for MySQL DDL typed visitors.
 */
public final class MySqlDdlEventVisitorCore {
    private final MySqlDdlEventSink out;
    private String currentTable = "";

    public MySqlDdlEventVisitorCore(String sourceName) {
        this.out = new MySqlDdlEventSink(sourceName);
    }

    public MySqlDdlEventSink out() {
        return out;
    }

    public List<StructuredSqlEvent> events() {
        return out.events();
    }

    public String currentTable() {
        return currentTable;
    }

    public void currentTable(String currentTable) {
        this.currentTable = currentTable == null ? "" : currentTable;
    }

    public void withCurrentTable(String table, Runnable body) {
        String previous = currentTable;
        currentTable = table == null ? "" : table;
        try {
            body.run();
        } finally {
            currentTable = previous;
        }
    }
}
