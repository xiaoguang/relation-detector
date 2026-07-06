package com.relationdetector.contracts.metadata;

/**
 * metadata collector 采集的列 catalog fact。
 *
 * <p>CN: 描述列类型、空值、默认值、生成表达式和序号，用于证据增强与审计输出。
 *
 * <p>EN: Structured column catalog fact collected from database metadata,
 * including type, nullability, default, generation expression, and ordinal position.
 */
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
