package com.relationdetector.semantic.kg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.semantic.graph.EvidenceGraph;
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

        assertEquals("2026-07-24T00:00:00Z",
                new ObjectMapper().readTree(tempDir.resolve("semantic-kg.json").toFile())
                        .path("buildRun").path("builtAt").asText());
        for (String file : List.of(
                "semantic-kg.json", "semantic-build-run.json", "semantic-evidence-graph.json")) {
            byte[] contents = Files.readAllBytes(tempDir.resolve(file));
            assertTrue(contents.length > 1);
            assertEquals('\n', contents[contents.length - 1]);
        }
    }
}
