package com.relationdetector.semantic.kg;

import java.util.LinkedHashSet;
import java.util.Set;

import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.ReferenceIndex;

/**
 * CN: 在有界内存 KG writer 写出 wire v2 前验证 node/edge 引用均可由配套 Evidence Graph 解析，并确认
 * builder 内部携带的 evidence/diagnostic inventory 未与该 graph 分叉；本类不生成或修补引用。
 * EN: Before the bounded in-memory writer emits KG wire v2, validates that every node and edge reference resolves
 * through the paired Evidence Graph and that the builder's internal evidence/diagnostic inventory has not diverged.
 * It never creates or repairs references.
 */
final class SemanticKgCrossFileClosureValidator {
    void validate(SemanticKnowledgeGraph graph, EvidenceGraph evidenceGraph) {
        if (graph == null || evidenceGraph == null) {
            throw new IllegalArgumentException("semantic KG and evidence graph are required");
        }
        Set<String> graphEvidence = new LinkedHashSet<>();
        graph.evidenceRefs().forEach(reference -> graphEvidence.add(reference.id()));
        Set<String> availableEvidence = new LinkedHashSet<>();
        evidenceGraph.evidenceRefs().forEach(reference -> availableEvidence.add(reference.id()));
        if (!graphEvidence.equals(availableEvidence)
                || !graph.diagnostics().equals(evidenceGraph.diagnostics())) {
            throw new IllegalArgumentException("semantic KG evidence inventory does not match its Evidence Graph");
        }

        ReferenceIndex references = ReferenceIndex.from(evidenceGraph);
        graph.nodes().forEach(node -> references.requireResolvable(node.id(), node.evidenceRefs()));
        graph.edges().forEach(edge -> references.requireEvidence(edge.id(), edge.evidenceRefs()));
        requireSummaryCount(graph, "evidenceRefCount", evidenceGraph.evidenceRefs().size());
        requireSummaryCount(graph, "diagnosticCount", evidenceGraph.diagnostics().size());
    }

    private void requireSummaryCount(SemanticKnowledgeGraph graph, String key, int expected) {
        Integer actual = graph.summary().get(key);
        if (actual != null && actual != expected) {
            throw new IllegalArgumentException("semantic KG summary does not match its Evidence Graph");
        }
    }
}
