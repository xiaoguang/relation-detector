package com.relationdetector.postgres.fullgrammer.common;

import java.util.List;

import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.core.ddl.DdlEventBuilder;

/**
 * Shared sink for PostgreSQL full-grammer DDL events.
 *
 * <p>CN: 封装 PostgreSQL 各版本 DDL collector 共同的 FK/index 事件创建和 identifier
 * 清理逻辑。版本 collector 仍负责从各自 typed grammar context 读取列和约束。
 *
 * <p>EN: Shared sink for PostgreSQL full-grammer DDL collectors. It owns common
 * FK/index event creation and identifier cleanup while version collectors read
 * columns and constraints from their typed grammar contexts.
 */
public final class PostgresDdlEventSink {
    private final DdlEventBuilder builder;

    public PostgresDdlEventSink(String sourceName) {
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
        return values.stream().map(this::clean).filter(value -> !value.isBlank()).toList();
    }
}
