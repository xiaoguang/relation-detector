package com.relationdetector.semantic.extract.model;

/**
 * CN: 保存 semantic graph 装配后的 node/edge 数量，供 artifact 审计；计数不用于推断或覆盖 graph 内容。
 * EN: Carries post-assembly semantic graph node and edge counts for artifact auditing. Counts do not infer or replace graph content.
 */
public record SemanticGraphSummary(int nodeCount, int edgeCount) {
}
