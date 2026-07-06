package com.relationdetector.contracts.metadata;

import java.util.List;

/**
 * metadata collector 采集的约束 catalog fact。
 *
 * <p>CN: 覆盖 PK/UK/FK 等约束及引用端信息，是 metadata FK evidence 的原始事实。
 *
 * <p>EN: Structured constraint catalog fact for PK/UK/FK-style constraints and
 * referenced endpoints. This is the raw fact behind metadata FK evidence.
 */
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
