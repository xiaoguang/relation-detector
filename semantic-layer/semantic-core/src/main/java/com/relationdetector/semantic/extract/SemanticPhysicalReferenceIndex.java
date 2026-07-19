package com.relationdetector.semantic.extract;

import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 从本次提示所使用的 typed evidence bundle 建立精确物理表和列集合，供 semantic section normalizer
 * 校验模型输出中的 physicalName、metric 和 lineage endpoint。输入保持 bundle 中的完整标识符，输出仅提供
 * 精确 membership 查询；本索引不降级 catalog/schema、不按名称补全，也不把 evidenceRef 当作物理身份依据。
 *
 * EN: Builds exact physical table and column sets from the typed evidence bundle used for the model prompt so
 * semantic section normalizers can validate physical names, metrics, and lineage endpoints. It preserves complete
 * bundle identifiers and exposes exact membership only; it neither drops catalog/schema components, completes names,
 * nor treats an evidence reference as proof of physical identity.
 */
final class SemanticPhysicalReferenceIndex {
    private static final Set<String> ENDPOINT_SECTIONS = Set.of(
            "relationships", "derivedRelationships", "namingEvidence");
    private static final Set<String> LINEAGE_SECTIONS = Set.of("lineage", "derivedLineage");

    private final Set<String> tables;
    private final Set<String> columns;

    private SemanticPhysicalReferenceIndex(Set<String> tables, Set<String> columns) {
        this.tables = Set.copyOf(tables);
        this.columns = Set.copyOf(columns);
    }

    /**
     * CN: 读取 bundle 的物理事实 section并生成不可变索引；缺少必需数组或非法列 endpoint时原子失败，
     * 不返回部分 registry。
     * EN: Reads physical fact sections from the bundle into an immutable index; missing required arrays or invalid
     * column endpoints fail atomically without returning a partial registry.
     */
    static SemanticPhysicalReferenceIndex from(JsonNode bundle) {
        Set<String> tables = new LinkedHashSet<>();
        JsonNode tableValues = bundle.path("tables");
        if (!tableValues.isArray()) {
            throw new IllegalArgumentException("semantic evidence bundle tables must be an array");
        }
        tableValues.forEach(value -> addText(tables, value));

        Set<String> endpointValues = new LinkedHashSet<>();
        for (String section : ENDPOINT_SECTIONS) {
            for (JsonNode item : bundle.path(section)) {
                addText(endpointValues, item.path("source"));
                addText(endpointValues, item.path("target"));
            }
        }
        for (String section : LINEAGE_SECTIONS) {
            for (JsonNode item : bundle.path(section)) {
                item.path("sources").forEach(value -> addText(endpointValues, value));
                addText(endpointValues, item.path("source"));
                addText(endpointValues, item.path("target"));
            }
        }
        for (JsonNode event : bundle.path("eventCandidates")) {
            event.path("inputEndpoints").forEach(value -> addText(endpointValues, value));
            event.path("outputEndpoints").forEach(value -> addText(endpointValues, value));
        }

        Set<String> columns = new LinkedHashSet<>();
        for (String endpoint : endpointValues) {
            if (tables.contains(endpoint)) {
                continue;
            }
            String table = PhysicalEndpointRef.column(endpoint).table();
            if (tables.contains(table)) {
                columns.add(endpoint);
            }
        }
        return new SemanticPhysicalReferenceIndex(tables, columns);
    }

    boolean containsTable(String table) {
        return table != null && tables.contains(table.trim());
    }

    boolean containsColumn(String column) {
        return column != null && columns.contains(column.trim());
    }

    private static void addText(Set<String> values, JsonNode value) {
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            values.add(value.asText().trim());
        }
    }

}
