package com.relationdetector.semantic.enrich;

import com.relationdetector.semantic.graph.EvidenceGraph;

/**
 * CN: 默认 enrichment 实现，原样返回 EvidenceGraph，保证未显式配置 LLM 时不产生任何新事实或副作用。
 * EN: Default enrichment implementation that returns EvidenceGraph unchanged, ensuring no facts or side effects appear when LLM enrichment is not explicitly configured.
 */
public final class NoopSemanticEnricher implements LlmSemanticEnricher {
    @Override
    public EvidenceGraph enrich(EvidenceGraph graph) {
        return graph;
    }
}
