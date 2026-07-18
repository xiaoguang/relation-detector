package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * CN: 表示物理表、CTE、临时表、trigger pseudo-rowset 或其他 typed rowset 引用。
 * EN: Represents a physical table, CTE, temporary table, trigger pseudo-rowset, or other typed rowset reference.
 */
public record RowsetEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        String keyword,
        String qualifiedTable,
        String table,
        String alias,
        String name,
        String targetTable,
        String reason
) implements StructuredSqlEvent {
    public RowsetEvent {
        keyword = clean(keyword);
        qualifiedTable = clean(qualifiedTable);
        table = clean(table);
        alias = clean(alias);
        name = clean(name);
        targetTable = clean(targetTable);
        reason = clean(reason);
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new RowsetEvent(type, value, keyword, qualifiedTable, table, alias, name, targetTable, reason);
    }

    private static String clean(String value) { return value == null ? "" : value; }
}
