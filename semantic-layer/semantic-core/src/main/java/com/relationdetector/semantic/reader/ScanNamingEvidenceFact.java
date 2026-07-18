package com.relationdetector.semantic.reader;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 保存 reader 边界已验证的命名证据和有向物理端点；供语义候选与证据图消费，不执行命名规则或字符串端点拆分。
 * EN: Holds reader-validated naming evidence with directional physical endpoints; consumers build semantic candidates and graphs, while this type performs neither naming inference nor string splitting.
 */
public record ScanNamingEvidenceFact(
        String id,
        PhysicalEndpointRef source,
        PhysicalEndpointRef target,
        String rule,
        boolean directionHint,
        double confidence,
        JsonNode document
) implements ScanFact {
    public ScanNamingEvidenceFact {
        if (source == null || target == null) {
            throw new IllegalArgumentException("naming endpoints are required");
        }
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("naming evidence fact must be a JSON object");
        }
        document = document.deepCopy();
    }
}
