package com.relationdetector.semantic.kg;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.graph.EvidenceReference;

/**
 * CN: 最终 evidence-backed KG artifact，聚合 deterministic build metadata、summary、nodes、edges、evidence 和 diagnostics；所有集合在构造时冻结。
 * EN: Final evidence-backed KG artifact containing deterministic build metadata, summary, nodes, edges, evidence, and diagnostics, with collections frozen at construction.
 */
public record SemanticKnowledgeGraph(
        Map<String, Object> buildRun,
        Map<String, Integer> summary,
        List<SemanticNode> nodes,
        List<SemanticEdge> edges,
        List<EvidenceReference> evidenceRefs,
        List<JsonNode> diagnostics
) {
    public SemanticKnowledgeGraph {
        buildRun = Map.copyOf(buildRun == null ? Map.of("builtAt", "") : buildRun);
        summary = Map.copyOf(summary == null ? Map.of() : summary);
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        evidenceRefs = List.copyOf(evidenceRefs == null ? List.of() : evidenceRefs);
        diagnostics = List.copyOf(diagnostics == null ? List.of() : diagnostics);
    }
}
