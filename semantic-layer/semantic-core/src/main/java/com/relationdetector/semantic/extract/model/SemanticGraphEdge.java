package com.relationdetector.semantic.extract.model;

import java.util.List;

/**
 * CN: 语义 graph 中一个有向、evidence-backed edge；相同 id 只有内容完全一致时可幂等去重，冲突由 assembler 拒绝。
 * EN: Represents one directional evidence-backed edge in the semantic graph. Equal ids may deduplicate only when content matches exactly; conflicts are rejected by the assembler.
 */
public record SemanticGraphEdge(String id, String source, String target, String type, List<String> evidenceRefs) {
}
