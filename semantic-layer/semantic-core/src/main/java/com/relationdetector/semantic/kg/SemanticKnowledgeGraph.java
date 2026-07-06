package com.relationdetector.semantic.kg;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.graph.EvidenceReference;

/** Evidence-backed semantic knowledge graph artifact. */
public record SemanticKnowledgeGraph(
        Map<String, Object> buildRun,
        Map<String, Integer> summary,
        List<SemanticNode> nodes,
        List<SemanticEdge> edges,
        List<EvidenceReference> evidenceRefs,
        List<JsonNode> diagnostics
) {
    public SemanticKnowledgeGraph {
        buildRun = Map.copyOf(buildRun == null ? Map.of("builtAt", Instant.now().toString()) : buildRun);
        summary = Map.copyOf(summary == null ? Map.of() : summary);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
