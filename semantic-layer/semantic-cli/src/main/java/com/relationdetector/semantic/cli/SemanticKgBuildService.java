package com.relationdetector.semantic.cli;

import java.nio.file.Path;

import com.relationdetector.semantic.enrich.NoopSemanticEnricher;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.SemanticEvidenceBuilder;
import com.relationdetector.semantic.kg.JsonSemanticKgWriter;
import com.relationdetector.semantic.kg.SemanticKgBuilder;
import com.relationdetector.semantic.kg.SemanticKnowledgeGraph;
import com.relationdetector.semantic.reader.ScanBundle;

/**
 * CN: 复用唯一的 scan bundle 到 evidence graph、KG 和 artifact 写入链路；上游是 build/e2e handler，下游是 semantic-core builder/writer，禁止解析 CLI 或调用模型。
 * EN: Owns the single scan-bundle to evidence-graph, KG, and artifact-writing path shared by build and e2e handlers; it neither parses CLI arguments nor calls a model.
 */
final class SemanticKgBuildService {
    void build(ScanBundle bundle, Path output) {
        EvidenceGraph evidenceGraph = new SemanticEvidenceBuilder().build(bundle);
        evidenceGraph = new NoopSemanticEnricher().enrich(evidenceGraph);
        SemanticKnowledgeGraph kg = new SemanticKgBuilder().build(evidenceGraph);
        new JsonSemanticKgWriter().writeArtifacts(kg, evidenceGraph, output);
    }
}
