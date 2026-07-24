package com.relationdetector.semantic.kg;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.graph.EvidenceGraph;

/**
 * CN: 将已构建的 KG、build-run 和 evidence graph 稳定序列化为 pretty JSON；文件 artifact 直接流式写入固定
 * filenames，避免物化无界大字符串，字符串方法只服务有界的内存调用。I/O 失败明确抛出，不改变 graph。
 * EN: Serializes built KG, build-run, and evidence graph artifacts as stable pretty JSON. File artifacts stream
 * directly to fixed filenames to avoid materializing an unbounded String; String methods serve bounded in-memory
 * callers only. I/O failures propagate and graph content is never mutated.
 */
public final class JsonSemanticKgWriter {
    private static final ObjectMapper DEFAULT_JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final ObjectMapper json;

    public JsonSemanticKgWriter() {
        this(DEFAULT_JSON);
    }

    JsonSemanticKgWriter(ObjectMapper json) {
        this.json = json;
    }

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
            writeArtifact(outputDirectory.resolve("semantic-kg.json"), graph);
            writeArtifact(outputDirectory.resolve("semantic-build-run.json"), graph.buildRun());
            writeArtifact(outputDirectory.resolve("semantic-evidence-graph.json"), evidenceGraph);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to write semantic KG artifacts to " + outputDirectory, e);
        }
    }

    private void writeArtifact(Path path, Object value) throws IOException {
        try (OutputStream output = Files.newOutputStream(path);
             JsonGenerator generator = json.getFactory().createGenerator(output)) {
            json.writeValue(generator, value);
            generator.writeRaw('\n');
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to render semantic JSON", e);
        }
    }
}
