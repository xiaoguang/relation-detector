package com.relationdetector.contracts.metadata;

/**
 * metadata collector 采集的表 catalog fact。
 *
 * <p>CN: 记录 schema、表名、表类型、引擎和注释，用于 known physical table 与审计上下文。
 *
 * <p>EN: Structured table catalog fact recording schema, table name, table type,
 * engine, and comment for known-physical-table and audit context.
 */
public record MetadataTableFact(
        String catalog,
        String schema,
        String tableName,
        String tableType,
        String engine,
        String comment
) {
}
