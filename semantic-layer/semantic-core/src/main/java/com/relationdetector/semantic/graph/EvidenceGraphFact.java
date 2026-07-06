package com.relationdetector.semantic.graph;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.reader.EndpointRef;

/** Semantic evidence graph fact derived directly from relation-detector records. */
public record EvidenceGraphFact(
        String id,
        String type,
        String label,
        List<EndpointRef> endpoints,
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
