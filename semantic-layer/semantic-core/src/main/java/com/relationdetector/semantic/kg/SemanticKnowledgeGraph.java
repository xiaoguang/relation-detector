package com.relationdetector.semantic.kg;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.relationdetector.semantic.graph.EvidenceReference;

/**
 * CN: 保存KG构建阶段的build metadata、nodes、edges及用于闭包校验的evidence/diagnostic inventory；wire v2
 * 只序列化nodes/edges并引用独立Evidence Graph，避免重复payload。所有集合在构造时冻结。
 * EN: Holds KG build metadata, nodes, edges, and the evidence/diagnostic inventory used for closure validation.
 * Wire v2 serializes nodes and edges while referencing a separate Evidence Graph to avoid duplicate payloads.
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
