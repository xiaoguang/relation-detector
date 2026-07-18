package com.relationdetector.mysql.fullgrammar.common;

import java.util.List;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.ddl.DdlEventBuilder;

/**
 * CN: 接收 version visitor 已 typed 提取的 table、column、constraint 和 index 信息，委托 DdlEventBuilder 形成稳定 events；它不访问 generated context 或推断约束。
 * EN: Accepts typed table, column, constraint, and index data from version visitors and delegates stable event construction to DdlEventBuilder. It neither reads generated contexts nor infers constraints.
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

    public void addIndex(String table, List<String> columns, String role, String kind, long line) {
        builder.addIndex(table, columns, role, kind, line);
    }

    public void addColumn(String table, String column, long line) {
        builder.addColumn(clean(table), clean(column), line);
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
