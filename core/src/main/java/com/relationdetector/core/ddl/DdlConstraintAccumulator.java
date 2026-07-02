package com.relationdetector.core.ddl;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Small mutable holder for DDL events collected while visiting one statement.
 */
public final class DdlConstraintAccumulator {
    private final DdlColumnListExtractor columns = new DdlColumnListExtractor();
    private final DdlEventBuilder events;

    public DdlConstraintAccumulator(String sourceName) {
        this.events = new DdlEventBuilder(sourceName);
    }

    public DdlColumnListExtractor columns() {
        return columns;
    }

    public void addForeignKey(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            long line
    ) {
        events.addForeignKey(
                columns.clean(sourceTable),
                columns.nonBlank(sourceColumns),
                columns.clean(targetTable),
                columns.nonBlank(targetColumns),
                line);
    }

    public void addIndex(String table, String column, String role, String kind, long line) {
        events.addIndex(columns.clean(table), columns.clean(column), role, kind, line);
    }

    public List<StructuredSqlEvent> events() {
        return new ArrayList<>(events.events());
    }
}
