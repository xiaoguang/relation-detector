package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticCliIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void semanticBuildWritesKgBuildRunAndEvidenceGraph() throws Exception {
        Path input = tempDir.resolve("scan-result.json");
        Path output = tempDir.resolve("semantic-output");
        Files.writeString(input, """
                {
                  "database": {"type": "mysql", "schema": "shop"},
                  "generatedAt": "2026-07-05T00:00:00Z",
                  "summary": {"directRelationshipCount": 0, "derivedRelationshipCount": 0, "totalRelationshipCount": 0, "directDataLineageCount": 0, "derivedDataLineageCount": 0, "totalDataLineageCount": 0, "directNamingEvidenceCount": 0, "derivedNamingEvidenceCount": 0, "totalNamingEvidenceCount": 0, "warningCount": 0, "sources": ["logs"]},
                  "relationships": [],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);

        int exit = Main.run(new String[] {"build", "--input", input.toString(), "--output", output.toString()});

        assertEquals(0, exit);
        assertTrue(Files.exists(output.resolve("semantic-kg.json")));
        assertTrue(Files.exists(output.resolve("semantic-build-run.json")));
        assertTrue(Files.exists(output.resolve("semantic-evidence-graph.json")));
        JsonNode kg = JSON.readTree(output.resolve("semantic-kg.json").toFile());
        assertEquals("mysql", kg.path("buildRun").path("database").path("type").asText());
        assertTrue(kg.path("nodes").isArray());
        assertTrue(kg.path("edges").isArray());
    }
}
