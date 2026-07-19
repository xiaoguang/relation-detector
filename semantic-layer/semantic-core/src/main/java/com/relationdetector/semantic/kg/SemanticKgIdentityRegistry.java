package com.relationdetector.semantic.kg;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CN: 在一次 KG build 内按 stable ID 原子登记 node 与 edge；builder 是上游，最终不可变集合是下游，完全相同
 * 内容可幂等复用，ID 冲突或无证据 edge 明确失败，本类不生成 ID 或补造 evidence。
 * EN: Atomically registers nodes and edges by stable ID within one KG build. The builder supplies values and immutable
 * output consumes them; identical content is idempotent, while conflicting IDs and evidence-free edges fail. This
 * registry neither creates IDs nor invents evidence.
 */
final class SemanticKgIdentityRegistry {
    private final Map<String, SemanticNode> nodes = new LinkedHashMap<>();
    private final Map<String, SemanticEdge> edges = new LinkedHashMap<>();

    void addNode(SemanticNode node) {
        SemanticNode existing = nodes.putIfAbsent(node.id(), node);
        if (existing != null && !existing.equals(node)) {
            throw new IllegalArgumentException("conflicting semantic node id: " + node.id());
        }
    }

    void addEdge(SemanticEdge edge) {
        if (edge.evidenceRefs().isEmpty()) {
            throw new IllegalArgumentException("semantic edge requires evidence: " + edge.id());
        }
        SemanticEdge existing = edges.putIfAbsent(edge.id(), edge);
        if (existing != null && !existing.equals(edge)) {
            throw new IllegalArgumentException("conflicting semantic edge id: " + edge.id());
        }
    }

    List<SemanticNode> nodes() {
        return List.copyOf(nodes.values());
    }

    List<SemanticEdge> edges() {
        return List.copyOf(edges.values());
    }

    int nodeCount() {
        return nodes.size();
    }

    int edgeCount() {
        return edges.size();
    }
}
