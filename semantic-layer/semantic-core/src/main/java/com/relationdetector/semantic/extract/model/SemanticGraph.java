package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 正式语义抽取 artifact 内的节点、边和计数摘要；内容来自已验证 semantic owners，不是 relation-detector 物理 evidence graph。
 * EN: Carries normalized semantic nodes, edges, and counts in the extraction artifact. It is built from validated semantic owners and is distinct from the physical evidence graph.
 */
public record SemanticGraph(
        List<SemanticGraphNode> nodes,
        List<SemanticGraphEdge> edges,
        SemanticGraphSummary summary
) {
}
