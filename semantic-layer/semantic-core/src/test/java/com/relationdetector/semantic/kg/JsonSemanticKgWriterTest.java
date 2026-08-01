package com.relationdetector.semantic.kg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.graph.EvidenceGraph;
import com.relationdetector.semantic.graph.EvidenceReference;
import com.relationdetector.semantic.reader.ScanBundle;

final class JsonSemanticKgWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void artifactFilesUseStreamingSerializationInsteadOfOneIntermediateString() throws Exception {
        ObjectMapper streamingOnly = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) throws JsonProcessingException {
                throw new AssertionError("artifact serialization must not allocate one complete JSON string");
            }
        };
        ScanBundle bundle = new ScanBundle(
                "mysql", "shop", "", "2026-07-24T00:00:00Z",
                List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        EvidenceGraph evidenceGraph = new EvidenceGraph(
                bundle, List.of(), List.of(), List.of(), List.of(), Map.of());
        SemanticKnowledgeGraph knowledgeGraph = new SemanticKnowledgeGraph(
                Map.of("builtAt", "2026-07-24T00:00:00Z"),
                Map.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        new JsonSemanticKgWriter(streamingOnly).writeArtifacts(knowledgeGraph, evidenceGraph, tempDir);

        ObjectMapper json = new ObjectMapper();
        JsonNode kg = json.readTree(tempDir.resolve("semantic-kg.json").toFile());
        assertEquals("2026-07-24T00:00:00Z", kg.path("buildRun").path("builtAt").asText());
        assertEquals(2, kg.path("artifactSchemaVersion").asInt());
        assertFalse(kg.has("evidenceRefs"));
        assertFalse(kg.has("diagnostics"));
        assertEquals("semantic-evidence-graph.json", kg.path("evidenceGraph").path("path").asText());
        assertEquals(sha256(tempDir.resolve("semantic-evidence-graph.json")),
                kg.path("evidenceGraph").path("sha256").asText());
        assertEquals(0, kg.path("evidenceGraph").path("evidenceRefCount").asInt());
        assertEquals(0, kg.path("evidenceGraph").path("diagnosticCount").asInt());
        for (String file : List.of(
                "semantic-kg.json", "semantic-build-run.json", "semantic-evidence-graph.json")) {
            byte[] contents = Files.readAllBytes(tempDir.resolve(file));
            assertTrue(contents.length > 1);
            assertEquals('\n', contents[contents.length - 1]);
        }
    }

    @Test
    void boundedStringWriterUsesTheSameV2WireAndRejectsCrossFileReferenceGaps() throws Exception {
        ScanBundle bundle = new ScanBundle(
                "mysql", "shop", "", "2026-07-24T00:00:00Z",
                List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        EvidenceReference evidence = new EvidenceReference(
                "evidence:known", "SQL", "logs", BigDecimal.ONE,
                "known.sql", "known", Map.of());
        EvidenceGraph evidenceGraph = new EvidenceGraph(
                bundle, List.of(), List.of(), List.of(evidence), List.of(), Map.of());
        SemanticNode validNode = new SemanticNode(
                "node:known", "Fact", "Known", BigDecimal.ONE,
                "EVIDENCE_SUPPORTED", List.of("evidence:known"), Map.of());
        SemanticKnowledgeGraph valid = new SemanticKnowledgeGraph(
                Map.of("builtAt", "2026-07-24T00:00:00Z"),
                Map.of("nodeCount", 1, "edgeCount", 0, "evidenceRefCount", 1, "diagnosticCount", 0),
                List.of(validNode), List.of(), List.of(evidence), List.of());

        JsonNode wire = new ObjectMapper().readTree(new JsonSemanticKgWriter().writeKg(valid, evidenceGraph));
        assertEquals(2, wire.path("artifactSchemaVersion").asInt());
        assertFalse(wire.has("evidenceRefs"));
        assertFalse(wire.has("diagnostics"));
        assertEquals(1, wire.path("evidenceGraph").path("evidenceRefCount").asInt());

        SemanticNode invalidNode = new SemanticNode(
                "node:missing", "Fact", "Missing", BigDecimal.ONE,
                "EVIDENCE_SUPPORTED", List.of("evidence:missing"), Map.of());
        SemanticKnowledgeGraph invalid = new SemanticKnowledgeGraph(
                valid.buildRun(), valid.summary(), List.of(invalidNode), List.of(), List.of(evidence), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> new JsonSemanticKgWriter().writeKg(invalid, evidenceGraph));
    }

    private String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
