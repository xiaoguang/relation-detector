package com.relationdetector.contracts.parse;

import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 *
 * Foreign-key, index, unique-key, or column-inventory event.
 */
public record DdlEvent(
        StructuredParseEventType type,
        SourceProvenance provenance,
        String sourceTable,
        String sourceColumn,
        String targetTable,
        String targetColumn,
        String table,
        String column,
        String role,
        String kind,
        int compositePosition,
        int compositeSize
) implements StructuredSqlEvent {
    public DdlEvent {
        sourceTable = clean(sourceTable);
        sourceColumn = clean(sourceColumn);
        targetTable = clean(targetTable);
        targetColumn = clean(targetColumn);
        table = clean(table);
        column = clean(column);
        role = clean(role);
        kind = clean(kind);
        compositePosition = Math.max(1, compositePosition);
        compositeSize = Math.max(1, compositeSize);
    }

    @Override
    public StructuredSqlEvent withProvenance(SourceProvenance value) {
        return new DdlEvent(type, value, sourceTable, sourceColumn, targetTable,
                targetColumn, table, column, role, kind, compositePosition, compositeSize);
    }

    private static String clean(String value) { return value == null ? "" : value; }
}
