package com.relationdetector.mysql.fullgrammer.common;

import java.util.List;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.ddl.DdlEventBuilder;

/**
 * Shared DDL event sink for MySQL full-grammer visitors.
 */
public final class MySqlDdlEventSink {
    private final DdlEventBuilder builder;

    public MySqlDdlEventSink(String sourceName) {
        this.builder = new DdlEventBuilder(sourceName);
    }

    public List<StructuredSqlEvent> events() {
        return builder.events();
    }

    public void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            long line
    ) {
        builder.addForeignKey(sourceTable, sourceColumns, targetTable, targetColumns, line);
    }

    public void addIndex(String table, String column, String role, String kind, long line) {
        builder.addIndex(table, column, role, kind, line);
    }

    public String clean(String value) {
        if (value == null) {
            return "";
        }
        String text = value.strip();
        while ((text.startsWith("`") && text.endsWith("`"))
                || (text.startsWith("\"") && text.endsWith("\""))
                || (text.startsWith("[") && text.endsWith("]"))) {
            text = text.substring(1, text.length() - 1);
        }
        return text.replace("`.", ".")
                .replace(".`", ".")
                .replace("\".", ".")
                .replace(".\"", ".")
                .replace(" ", "");
    }

    public List<String> nonBlank(List<String> values) {
        return values.stream()
                .map(this::clean)
                .filter(value -> !value.isBlank())
                .toList();
    }
}
