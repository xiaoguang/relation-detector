package com.relationdetector.semantic.graph;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.reader.EndpointRef;
import com.relationdetector.semantic.reader.ScanBundle;

/** Evidence-backed semantic graph before KG materialization. */
public record EvidenceGraph(
        ScanBundle scanBundle,
        List<EndpointRef> endpoints,
        List<EvidenceGraphFact> facts,
        List<EvidenceReference> evidenceRefs,
        List<JsonNode> diagnostics,
        Map<String, Integer> summary
) {
    public EvidenceGraph {
        if (scanBundle == null) {
            throw new IllegalArgumentException("scan bundle is required");
        }
        endpoints = List.copyOf(endpoints == null ? List.of() : endpoints);
        facts = List.copyOf(facts == null ? List.of() : facts);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
        summary = Map.copyOf(summary == null ? Map.of() : summary);
    }
}
