package com.relationdetector.api;

/** Structured column catalog fact collected from database metadata. */
public record MetadataColumnFact(
        String schema,
        String tableName,
        String columnName,
        String dataType,
        String columnType,
        boolean nullable,
        String defaultValue,
        String extra,
        String generationExpression,
        int ordinalPosition
) {
}
