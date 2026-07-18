package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.model.PhysicalEndpointRef;

/**
 * CN: relation-detector record 在 evidence graph 中的规范化 fact，保留 typed endpoints、confidence、payload 和 evidence refs；构造器只冻结数据，不推断业务语义。
 * EN: Normalized evidence-graph fact derived directly from a relation-detector record, retaining typed endpoints, confidence, payload, and evidence references without business inference.
 */
public record EvidenceGraphFact(
        String id,
        String type,
        String label,
        List<PhysicalEndpointRef> endpoints,
        List<String> evidenceRefs,
        BigDecimal confidence,
        JsonNode payload,
        Map<String, Object> attributes
) {
    public EvidenceGraphFact {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("fact id is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("fact type is required");
        }
        label = label == null ? id : label;
        endpoints = List.copyOf(endpoints == null ? List.of() : endpoints);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }
}
