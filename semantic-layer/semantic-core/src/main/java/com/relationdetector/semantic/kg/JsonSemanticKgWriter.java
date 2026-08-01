package com.relationdetector.semantic.kg;

import java.io.IOException;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.internal.io.SemanticDigestingOutputStream;

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

    public String writeKg(SemanticKnowledgeGraph graph, EvidenceGraph evidenceGraph) {
        new SemanticKgCrossFileClosureValidator().validate(graph, evidenceGraph);
        byte[] evidenceBytes = writeBytes(evidenceGraph);
        SemanticKgEvidenceGraphReference reference = reference(evidenceBytes, evidenceGraph);
        return new String(writeKgBytes(graph, reference), StandardCharsets.UTF_8);
    }

    public String writeEvidenceGraph(EvidenceGraph graph) {
        return write(graph);
    }

    public String writeBuildRun(SemanticKnowledgeGraph graph) {
        return write(graph.buildRun());
    }

    public void writeArtifacts(SemanticKnowledgeGraph graph, EvidenceGraph evidenceGraph, Path outputDirectory) {
        new SemanticKgCrossFileClosureValidator().validate(graph, evidenceGraph);
        try {
            Files.createDirectories(outputDirectory);
            Path evidencePath = outputDirectory.resolve("semantic-evidence-graph.json");
            SemanticKgEvidenceGraphReference evidenceReference = writeEvidenceGraphArtifact(
                    evidencePath, evidenceGraph);
            writeKgArtifact(outputDirectory.resolve("semantic-kg.json"), graph, evidenceReference);
            writeArtifact(outputDirectory.resolve("semantic-build-run.json"), graph.buildRun());
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to write semantic KG artifacts to " + outputDirectory, e);
        }
    }

    private byte[] writeKgBytes(
            SemanticKnowledgeGraph graph,
            SemanticKgEvidenceGraphReference evidenceGraph
    ) {
        return writeBytes(generator -> writeKgDocument(generator, graph, evidenceGraph));
    }

    private SemanticKgEvidenceGraphReference reference(byte[] bytes, EvidenceGraph graph) {
        return new SemanticKgEvidenceGraphReference(
                "semantic-evidence-graph.json",
                sha256(bytes),
                graph.evidenceRefs().size(),
                graph.diagnostics().size());
    }

    private SemanticKgEvidenceGraphReference writeEvidenceGraphArtifact(
            Path path,
            EvidenceGraph graph
    ) throws IOException {
        SemanticDigestingOutputStream digest = new SemanticDigestingOutputStream(Files.newOutputStream(path));
        try (JsonGenerator generator = json.getFactory().createGenerator(digest)) {
            json.writeValue(generator, graph);
            generator.writeRaw('\n');
        }
        return new SemanticKgEvidenceGraphReference(
                "semantic-evidence-graph.json",
                digest.sha256(),
                graph.evidenceRefs().size(),
                graph.diagnostics().size());
    }

    private void writeKgArtifact(
            Path path,
            SemanticKnowledgeGraph graph,
            SemanticKgEvidenceGraphReference evidenceGraph
    ) throws IOException {
        try (OutputStream output = Files.newOutputStream(path);
             JsonGenerator generator = json.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            writeKgDocument(generator, graph, evidenceGraph);
            generator.writeRaw('\n');
        }
    }

    private void writeKgDocument(
            JsonGenerator generator,
            SemanticKnowledgeGraph graph,
            SemanticKgEvidenceGraphReference evidenceGraph
    ) throws IOException {
        generator.writeStartObject();
        generator.writeNumberField("artifactSchemaVersion", 2);
        generator.writeObjectField("buildRun", graph.buildRun());
        generator.writeObjectField("summary", graph.summary());
        generator.writeObjectField("nodes", graph.nodes());
        generator.writeObjectField("edges", graph.edges());
        generator.writeObjectField("evidenceGraph", evidenceGraph);
        generator.writeEndObject();
    }

    private void writeArtifact(Path path, Object value) throws IOException {
        try (OutputStream output = Files.newOutputStream(path);
             JsonGenerator generator = json.getFactory().createGenerator(output)) {
            json.writeValue(generator, value);
            generator.writeRaw('\n');
        }
    }

    private byte[] writeBytes(Object value) {
        return writeBytes(generator -> json.writeValue(generator, value));
    }

    private byte[] writeBytes(JsonRenderer renderer) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (JsonGenerator generator = json.getFactory().createGenerator(output)) {
            generator.useDefaultPrettyPrinter();
            renderer.write(generator);
            generator.writeRaw('\n');
        } catch (IOException failure) {
            throw new IllegalStateException("failed to render semantic JSON", failure);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to render semantic JSON", e);
        }
    }

    @FunctionalInterface
    private interface JsonRenderer {
        void write(JsonGenerator generator) throws IOException;
    }
}
