package com.relationdetector.oracle.fullgrammer.common;

import java.util.Map;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * Shared Oracle DDL event post-processing.
 */
public final class OracleDdlEventVisitorCore {
    private OracleDdlEventVisitorCore() {
    }

    public static void addForeignKeyEvents(
            OracleSqlEventVisitorCore core,
            ParserRuleContext ctx,
            String sourceTable,
            List<String> sourceColumns,
            String targetTable,
            List<String> targetColumns
    ) {
        int count = Math.min(sourceColumns.size(), targetColumns.size());
        for (int index = 0; index < count; index++) {
            Map<String, Object> attrs = core.attrs();
            attrs.put("sourceTable", sourceTable);
            attrs.put("sourceColumn", sourceColumns.get(index));
            attrs.put("targetTable", targetTable);
            attrs.put("targetColumn", targetColumns.get(index));
            attrs.put("compositePosition", index + 1);
            attrs.put("compositeSize", count);
            core.add(StructuredParseEventType.DDL_FOREIGN_KEY, ctx, attrs);
        }
    }

    public static void addIndexEvent(
            OracleSqlEventVisitorCore core,
            ParserRuleContext ctx,
            String table,
            String column,
            String role,
            String kind
    ) {
        if (table.isBlank() || column.isBlank()) {
            return;
        }
        Map<String, Object> attrs = core.attrs();
        attrs.put("table", table);
        attrs.put("column", core.baseName(column));
        attrs.put("role", role);
        attrs.put("kind", kind);
        core.add(StructuredParseEventType.DDL_INDEX, ctx, attrs);
    }

    public static List<StructuredSqlEvent> ddlEvents(List<StructuredSqlEvent> events) {
        return events.stream()
                .filter(event -> event.type().name().startsWith("DDL_"))
                .toList();
    }
}
