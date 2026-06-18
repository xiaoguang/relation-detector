package com.relationdetector.api;

/** Structured table catalog fact collected from database metadata. */
public record MetadataTableFact(
        String schema,
        String tableName,
        String tableType,
        String engine,
        String comment
) {
}
