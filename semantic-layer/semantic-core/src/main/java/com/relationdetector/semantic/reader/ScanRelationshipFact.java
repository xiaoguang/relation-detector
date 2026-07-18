package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 保存 reader 边界已验证的关系事实及其结构化物理端点；输入来自 relation-detector JSON，输出供语义事件、证据图和 KG 构建使用，不负责重新解析端点字符串。
 * EN: Holds a reader-validated relationship fact with structured physical endpoints; it is consumed by semantic event, evidence-graph, and KG builders and never reparses endpoint strings.
 */
public record ScanRelationshipFact(
        String id,
        PhysicalEndpointRef source,
        PhysicalEndpointRef target,
        String relationType,
        String relationSubType,
        double confidence,
        boolean derived,
        JsonNode document
) implements ScanFact {
    public ScanRelationshipFact {
        if (source == null || target == null) {
            throw new IllegalArgumentException("relationship endpoints are required");
        }
        document = immutableDocument(document);
    }

    private static JsonNode immutableDocument(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("relationship fact must be a JSON object");
        }
        return value.deepCopy();
    }
}
