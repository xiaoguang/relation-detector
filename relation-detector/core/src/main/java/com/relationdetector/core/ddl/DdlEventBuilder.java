package com.relationdetector.core.ddl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared builder for normalized DDL parser events.
 *
 * <p>CN: 方言 typed DDL visitor 后续可以只提交 table/column/constraint 结构，
 * 由这个 builder 统一生成 DDL_FOREIGN_KEY 和 DDL_INDEX 事件。
 */
public final class DdlEventBuilder {
    private final String sourceName;
    private final List<StructuredSqlEvent> events = new ArrayList<>();

    public DdlEventBuilder(String sourceName) {
        this.sourceName = sourceName;
    }

    public List<StructuredSqlEvent> events() {
        return List.copyOf(events);
    }

    public void addForeignKey(
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

    public void addColumn(String table, String column, long line) {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return;
        }
        events.add(new StructuredSqlEvent(StructuredParseEventType.DDL_COLUMN, sourceName, line,
                Map.of("table", table, "column", column)));
    }
}
