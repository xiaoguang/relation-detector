package com.relationdetector.semantic.enrich;

import com.relationdetector.semantic.graph.EvidenceGraph;

/** Extension point for future LLM semantic candidate generation. */
public interface LlmSemanticEnricher {
    EvidenceGraph enrich(EvidenceGraph graph);
}
