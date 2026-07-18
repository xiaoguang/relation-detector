package com.relationdetector.semantic.reader;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: 保存 reader 边界已验证的血缘事实和有序物理端点；上游是 scan JSON reader，下游是事件、证据图和抽取 bundle，禁止依赖点号猜测表名。
 * EN: Holds reader-validated lineage with ordered physical endpoints for event, evidence-graph, and extraction-bundle consumers; table identity must not be guessed from dotted strings.
 */
public record ScanLineageFact(
        String id,
        List<PhysicalEndpointRef> sources,
        PhysicalEndpointRef target,
        String flowKind,
        String transformType,
        double confidence,
        boolean derived,
        JsonNode document
) implements ScanFact {
    public ScanLineageFact {
        sources = List.copyOf(sources == null ? List.of() : sources);
        if (target == null) {
            throw new IllegalArgumentException("lineage target is required");
        }
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("lineage fact must be a JSON object");
        }
        document = document.deepCopy();
    }
}
