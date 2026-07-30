package com.relationdetector.contracts.metadata;

import java.util.List;

/**
 * CN: metadata collector 采集的索引 catalog fact。{@code members} 是索引成员顺序和类型的权威表示，
 * 可完整表达物理列、前缀列与表达式的交错结构；旧字段仅作为 v6 调用方的确定性兼容投影，不能重建
 * 顺序不明确的 mixed shape。core 在生成唯一性或 lookup 证据前负责校验完整成员闭包。
 *
 * <p>EN: Structured index catalog fact collected from live metadata. {@code members} is the authoritative ordered
 * representation and preserves interleaved full columns, prefix columns, and expressions. Legacy fields remain
 * deterministic v6 compatibility projections and cannot reconstruct an ambiguously ordered mixed shape. Core
 * validates the complete member closure before deriving uniqueness or lookup evidence.
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
        List<Integer> seqInIndex,
        List<MetadataIndexMemberFact> members
) {
    public MetadataIndexFact {
        columns = columns == null ? List.of() : List.copyOf(columns);
        expressions = expressions == null ? List.of() : List.copyOf(expressions);
        subParts = subParts == null ? List.of() : List.copyOf(subParts);
        seqInIndex = seqInIndex == null ? List.of() : List.copyOf(seqInIndex);
        members = members == null ? legacyMembers(columns, expressions, subParts, seqInIndex) : List.copyOf(members);
        if (!members.isEmpty()) {
            columns = members.stream()
                    .filter(MetadataIndexFact::isPhysical)
                    .map(MetadataIndexMemberFact::columnName)
                    .toList();
            expressions = members.stream()
                    .filter(member -> member.kind() == MetadataIndexMemberKind.EXPRESSION)
                    .map(MetadataIndexMemberFact::expression)
                    .toList();
            boolean hasPrefix = members.stream()
                    .filter(MetadataIndexFact::isPhysical)
                    .anyMatch(member -> member.kind() == MetadataIndexMemberKind.PREFIX_COLUMN);
            subParts = hasPrefix
                    ? members.stream()
                            .filter(MetadataIndexFact::isPhysical)
                            .map(member -> member.prefixLength() == null ? "" : member.prefixLength().toString())
                            .toList()
                    : List.of();
            seqInIndex = !columns.isEmpty()
                    ? members.stream()
                            .filter(MetadataIndexFact::isPhysical)
                            .map(MetadataIndexMemberFact::ordinal)
                            .toList()
                    : members.stream().map(MetadataIndexMemberFact::ordinal).toList();
        }
    }

    public MetadataIndexFact(
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
        this(catalog, schema, tableName, indexName, unique, primary, indexType, visible,
                columns, expressions, subParts, seqInIndex, null);
    }

    public MetadataIndexFact(
            String catalog,
            String schema,
            String tableName,
            String indexName,
            boolean unique,
            boolean primary,
            String indexType,
            boolean visible,
            List<MetadataIndexMemberFact> members
    ) {
        this(catalog, schema, tableName, indexName, unique, primary, indexType, visible,
                List.of(), List.of(), List.of(), List.of(), members);
    }

    private static List<MetadataIndexMemberFact> legacyMembers(
            List<String> columns,
            List<String> expressions,
            List<String> subParts,
            List<Integer> positions
    ) {
        if (!columns.isEmpty() && !expressions.isEmpty()) {
            return List.of();
        }
        if (!columns.isEmpty()) {
            if (positions.size() != columns.size()
                    || (!subParts.isEmpty() && subParts.size() != columns.size())) {
                return List.of();
            }
            java.util.ArrayList<MetadataIndexMemberFact> result = new java.util.ArrayList<>(columns.size());
            for (int index = 0; index < columns.size(); index++) {
                String subPart = subParts.isEmpty() ? null : subParts.get(index);
                if (subPart == null || subPart.isBlank()) {
                    result.add(MetadataIndexMemberFact.fullColumn(positions.get(index), columns.get(index)));
                    continue;
                }
                try {
                    result.add(MetadataIndexMemberFact.prefixColumn(
                            positions.get(index), columns.get(index), Integer.parseInt(subPart.strip())));
                } catch (NumberFormatException failure) {
                    return List.of();
                }
            }
            return List.copyOf(result);
        }
        if (!expressions.isEmpty() && positions.size() == expressions.size() && subParts.isEmpty()) {
            java.util.ArrayList<MetadataIndexMemberFact> result = new java.util.ArrayList<>(expressions.size());
            for (int index = 0; index < expressions.size(); index++) {
                result.add(MetadataIndexMemberFact.expression(positions.get(index), expressions.get(index)));
            }
            return List.copyOf(result);
        }
        return List.of();
    }

    private static boolean isPhysical(MetadataIndexMemberFact member) {
        return member != null && member.kind() != MetadataIndexMemberKind.EXPRESSION;
    }
}
