package com.relationdetector.semantic.kg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.graph.EvidenceGraph;

/**
 * CN: 将已构建的 KG、build-run 和 evidence graph 稳定序列化为 pretty JSON，并写入固定 filenames；I/O 失败明确抛出，不改变 graph。
 * EN: Serializes built KG, build-run, and evidence graph artifacts as stable pretty JSON under fixed filenames. I/O failures propagate and graph content is never mutated.
 */
public final class JsonSemanticKgWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String writeKg(SemanticKnowledgeGraph graph) {
        return write(graph);
    }

    public String writeEvidenceGraph(EvidenceGraph graph) {
        return write(graph);
    }

    public String writeBuildRun(SemanticKnowledgeGraph graph) {
        return write(graph.buildRun());
    }

    public void writeArtifacts(SemanticKnowledgeGraph graph, EvidenceGraph evidenceGraph, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(outputDirectory.resolve("semantic-kg.json"), writeKg(graph));
            Files.writeString(outputDirectory.resolve("semantic-build-run.json"), writeBuildRun(graph));
            Files.writeString(outputDirectory.resolve("semantic-evidence-graph.json"), writeEvidenceGraph(evidenceGraph));
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to write semantic KG artifacts to " + outputDirectory, e);
        }
    }

    private String write(Object value) {
        try {
            return JSON.writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to render semantic JSON", e);
        }
    }
}
