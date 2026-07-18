package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 语义 graph 中一个 owner node 的稳定投影，保留 kind、label、type 和 evidence refs；assembler 保证 id 唯一。
 * EN: Stable projection of one semantic owner node with kind, label, type, and evidence references. The assembler guarantees global node-id uniqueness.
 */
public record SemanticGraphNode(String id, String kind, String label, String type, List<String> evidenceRefs) {
}
