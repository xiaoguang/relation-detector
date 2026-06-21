package com.relationdetector.postgres.fullgrammer.common;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final String sourceName;
    private final List<StructuredSqlEvent> events = new ArrayList<>();

    public PostgresDdlEventSink(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<StructuredSqlEvent> events() {
        return List.copyOf(events);
    }

    public void addForeignKeyEvents(
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns,
            long line
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int i = 0; i < count; i++) {
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("sourceTable", sourceTable);
            attributes.put("sourceColumn", sourceColumns.get(i));
            attributes.put("targetTable", targetTable);
            attributes.put("targetColumn", targetColumns.get(i));
            attributes.put("compositePosition", i + 1);
            attributes.put("compositeSize", count);
            events.add(new StructuredSqlEvent(StructuredParseEventType.DDL_FOREIGN_KEY, sourceName, line, attributes));
        }
    }

    public void addIndex(String table, String column, String role, String kind, long line) {
        events.add(new StructuredSqlEvent(StructuredParseEventType.DDL_INDEX, sourceName, line,
                Map.of("table", table, "column", column, "role", role, "kind", kind)));
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
