package com.relationdetector.semantic.enrich;

import com.relationdetector.semantic.graph.EvidenceGraph;

/**
 * CN: EvidenceGraph enrichment 的受控扩展点，输入和输出都必须保留原物理事实与 evidence；实现不得发明或重写 relation-detector facts。
 * EN: Controlled extension point for EvidenceGraph enrichment. Implementations must preserve physical facts and evidence and may never invent or rewrite relation-detector output.
 */
public interface LlmSemanticEnricher {
    EvidenceGraph enrich(EvidenceGraph graph);
}
