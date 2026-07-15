package com.relationdetector.contracts.metadata;

import java.util.List;

/**
 * metadata collector 采集的索引 catalog fact。
 *
 * <p>CN: 记录唯一性、主键、索引类型、可见性、列、表达式和前缀长度，用于增强候选关系。
 *
 * <p>EN: Structured index catalog fact recording uniqueness, primary status,
 * index type, visibility, columns, expressions, and prefix parts for evidence enhancement.
 */
public record MetadataIndexFact(
        String catalog,
        String schema,
        String tableName,
        String indexName,
        boolean unique,
        boolean primary,
        String indexType,
        boolean visible,
        List<String> columns,
        List<String> expressions,
        List<String> subParts,
        List<Integer> seqInIndex
) {
    public MetadataIndexFact {
        columns = columns == null ? List.of() : List.copyOf(columns);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
        subParts = subParts == null ? List.of() : List.copyOf(subParts);
        seqInIndex = seqInIndex == null ? List.of() : List.copyOf(seqInIndex);
    }
}
