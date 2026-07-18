package com.relationdetector.oracle.fullgrammar.common;

import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;

import com.relationdetector.contracts.parse.StructuredSqlEvent;

/**
 * CN: 将 Oracle DDL collector 的 typed table/column/key/index observations 规范化并写入 DdlEventBuilder；不读取 generated contexts，也不把组合键成员当单列唯一。
 * EN: Normalizes typed Oracle table, column, key, and index observations into DdlEventBuilder. It does not read generated contexts or treat members of composite keys as individually unique.
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
            core.ddlForeignKey(ctx, sourceTable, sourceColumns.get(index), targetTable,
                    targetColumns.get(index), index + 1, count);
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
        core.ddlIndex(ctx, table, core.baseName(column), role, kind);
    }

    public static void addIndexEvents(
            OracleSqlEventVisitorCore core,
            ParserRuleContext ctx,
            String table,
            List<String> columns,
            String role,
            String kind
    ) {
        if (table.isBlank() || columns == null) {
            return;
        }
        core.ddlIndex(ctx, table, columns.stream()
                .map(core::baseName)
                .filter(column -> !column.isBlank())
                .toList(), role, kind);
    }

    public static void addColumnEvent(
            OracleSqlEventVisitorCore core,
            ParserRuleContext ctx,
            String table,
            String column
    ) {
        if (table.isBlank() || column.isBlank()) {
            return;
        }
        core.ddlColumn(ctx, table, core.baseName(column));
    }

    public static List<StructuredSqlEvent> ddlEvents(List<StructuredSqlEvent> events) {
        return events.stream()
                .filter(event -> event.type().name().startsWith("DDL_"))
                .toList();
    }
}
