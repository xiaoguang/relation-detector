package com.relationdetector.contracts.metadata;

import java.util.List;

/** Structured constraint catalog fact collected from database metadata. */
public record MetadataConstraintFact(
        String schema,
        String tableName,
        String constraintName,
        String constraintType,
        List<String> columns,
        String referencedSchema,
        String referencedTable,
        List<String> referencedColumns,
        String updateRule,
        String deleteRule
) {
    public MetadataConstraintFact {
        columns = columns == null ? List.of() : List.copyOf(columns);
        referencedColumns = referencedColumns == null ? List.of() : List.copyOf(referencedColumns);
    }
}
