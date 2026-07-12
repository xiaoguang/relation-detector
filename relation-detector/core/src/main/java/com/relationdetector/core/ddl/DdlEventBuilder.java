package com.relationdetector.core.ddl;

import java.util.ArrayList;
import java.util.List;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.DdlEvent;
import com.relationdetector.contracts.parse.SourceProvenance;
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
            events.add(new DdlEvent(StructuredParseEventType.DDL_FOREIGN_KEY,
                    SourceProvenance.source(sourceName, line), sourceTable, sourceColumns.get(i),
                    targetTable, targetColumns.get(i), "", "", "", "", i + 1, count));
        }
    }

    public void addIndex(String table, String column, String role, String kind, long line) {
        addIndex(table, List.of(column), role, kind, line);
    }

    public void addIndex(String table, List<String> columns, String role, String kind, long line) {
        List<String> safeColumns = columns == null ? List.of() : columns.stream()
                .filter(column -> column != null && !column.isBlank())
                .toList();
        int count = safeColumns.size();
        for (int index = 0; index < count; index++) {
            events.add(new DdlEvent(StructuredParseEventType.DDL_INDEX,
                    SourceProvenance.source(sourceName, line), "", "", "", "",
                    table, safeColumns.get(index), role, kind, index + 1, count));
        }
    }

    public void addColumn(String table, String column, long line) {
        if (table == null || table.isBlank() || column == null || column.isBlank()) {
            return;
        }
        events.add(new DdlEvent(StructuredParseEventType.DDL_COLUMN,
                SourceProvenance.source(sourceName, line), "", "", "", "",
                table, column, "", "", 1, 1));
    }
}
