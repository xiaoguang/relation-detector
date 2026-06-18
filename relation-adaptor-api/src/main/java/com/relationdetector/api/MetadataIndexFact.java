package com.relationdetector.api;

import java.util.List;

/** Structured index catalog fact collected from database metadata. */
public record MetadataIndexFact(
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
