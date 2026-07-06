package com.relationdetector.semantic.enrich;

import com.relationdetector.semantic.graph.EvidenceGraph;

/** Default enricher for Phase 1 KG generation: it never creates facts. */
public final class NoopSemanticEnricher implements LlmSemanticEnricher {
    @Override
    public EvidenceGraph enrich(EvidenceGraph graph) {
        return graph;
    }
}
