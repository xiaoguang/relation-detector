package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SemanticExtractionConfigLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsSemanticExtractionYaml() throws Exception {
        Path config = tempDir.resolve("semantic-extraction.yml");
        Files.writeString(config, """
                semanticExtraction:
                  provider: openai-api
                  inputs:
                    - result-a.json
                    - result-b.json
                  output: out
                  focus: ROUTINE:erp.sp
                  model: gpt-5.5
                  reasoningEffort: high
                  maxOutputTokens: 9000
                  baseUrl: http://127.0.0.1:9999/v1
                  apiKeyEnv: TEST_OPENAI_API_KEY
                  maxRelationships: 10
                  maxLineage: 20
                  maxNamingEvidence: 30
                  requestOnly: true
                """);

        SemanticExtractionConfig loaded = new SemanticExtractionConfigLoader().load(config);

        assertEquals("openai-api", loaded.provider());
        assertEquals(2, loaded.inputs().size());
        assertEquals(Path.of("out"), loaded.output());
        assertEquals("ROUTINE:erp.sp", loaded.focus());
        assertEquals("gpt-5.5", loaded.model());
        assertEquals("high", loaded.reasoningEffort());
        assertEquals(9000, loaded.maxOutputTokens());
        assertEquals("http://127.0.0.1:9999/v1", loaded.baseUrl());
        assertEquals("TEST_OPENAI_API_KEY", loaded.apiKeyEnv());
        assertEquals(10, loaded.maxRelationships());
        assertEquals(20, loaded.maxLineage());
        assertEquals(30, loaded.maxNamingEvidence());
        assertTrue(loaded.requestOnly());
    }
}
