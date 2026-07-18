package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 将 relation-detector JSON endpoint 转换为中性的物理 endpoint 模型。它只解释已结构化的
 * table/column 字段，不解析显示字符串，也不执行命名或命名空间推断。
 *
 * EN: Converts a structured relation-detector JSON endpoint into the neutral physical endpoint
 * model. It reads only table and column fields and performs no display-string parsing, naming, or
 * namespace inference.
 */
public final class PhysicalEndpointJsonReader {
    private PhysicalEndpointJsonReader() {
    }

    public static PhysicalEndpointRef read(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new IllegalArgumentException("endpoint is required");
        }
        String table = node.path("table").asText("");
        JsonNode columnNode = node.path("column");
        String column = columnNode.isMissingNode() || columnNode.isNull() ? null : columnNode.asText();
        return new PhysicalEndpointRef(table, column);
    }
}
