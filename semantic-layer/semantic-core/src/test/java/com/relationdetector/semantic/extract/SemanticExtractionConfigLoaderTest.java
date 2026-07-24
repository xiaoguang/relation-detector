package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
                  model: gpt-5.6-sol
                  reasoningEffort: xhigh
                  maxOutputTokens: 9000
                  baseUrl: http://127.0.0.1:9999/v1
                  apiKeyEnv: TEST_OPENAI_API_KEY
                  maxRelationships: 10
                  maxLineage: 20
                  maxNamingEvidence: 30
                  requestOnly: true
                  artifactRetention: final-only
                  sharding:
                    mode: force
                    targetInputTokens: 220000
                    maxInputTokens: 700000
                    maxShardCount: 64
                    reconcile: false
                  shardMaxOutputTokens: 24000
                  reconciliationMaxOutputTokens: 16000
                  requestTimeoutSeconds: 900
                  maxTransportRetries: 2
                """);

        SemanticExtractionConfig loaded = new SemanticExtractionConfigLoader().load(config);

        assertEquals("openai-api", loaded.provider());
        assertEquals(List.of(tempDir.resolve("result-a.json"), tempDir.resolve("result-b.json")), loaded.inputs());
        assertEquals(tempDir.resolve("out"), loaded.output());
        assertEquals("ROUTINE:erp.sp", loaded.focus());
        assertEquals("gpt-5.6-sol", loaded.model());
        assertEquals("xhigh", loaded.reasoningEffort());
        assertEquals(9000, loaded.maxOutputTokens());
        assertEquals("http://127.0.0.1:9999/v1", loaded.baseUrl());
        assertEquals("TEST_OPENAI_API_KEY", loaded.apiKeyEnv());
        assertEquals(10, loaded.maxRelationships());
        assertEquals(20, loaded.maxLineage());
        assertEquals(30, loaded.maxNamingEvidence());
        assertTrue(loaded.requestOnly());
        assertEquals(ArtifactRetention.FINAL_ONLY, loaded.artifactRetention());
        assertEquals(SemanticShardMode.FORCE, loaded.sharding().mode());
        assertEquals(220000, loaded.sharding().targetInputTokens());
        assertEquals(700000, loaded.sharding().maxInputTokens());
        assertEquals(64, loaded.sharding().maxShardCount());
        assertEquals(false, loaded.sharding().reconcile());
        assertEquals(24000, loaded.shardMaxOutputTokens());
        assertEquals(16000, loaded.reconciliationMaxOutputTokens());
        assertEquals(900, loaded.requestTimeoutSeconds());
        assertEquals(2, loaded.maxTransportRetries());
    }

    @Test
    void defaultsUseUnlimitedEvidenceCandidateLimits() {
        SemanticExtractionConfig defaults = SemanticExtractionConfig.defaults();

        assertEquals(0, defaults.maxRelationships());
        assertEquals(0, defaults.maxLineage());
        assertEquals(0, defaults.maxNamingEvidence());
        assertEquals("gpt-5.6-sol", defaults.model());
        assertEquals("xhigh", defaults.reasoningEffort());
        assertEquals(SemanticShardingOptions.defaults(), defaults.sharding());
        assertEquals(ArtifactRetention.FULL, defaults.artifactRetention());
    }

    @Test
    void missingCandidateLimitsRemainUnlimited() throws Exception {
        Path config = tempDir.resolve("semantic-extraction-default-limits.yml");
        Files.writeString(config, """
                semanticExtraction:
                  input: result.json
                  output: out
                """);

        SemanticExtractionConfig loaded = new SemanticExtractionConfigLoader().load(config);

        assertEquals(0, loaded.maxRelationships());
        assertEquals(0, loaded.maxLineage());
        assertEquals(0, loaded.maxNamingEvidence());
    }

    @Test
    void rejectsNonObjectRootAndSemanticExtractionValue() throws Exception {
        Path scalarRoot = writeConfig("not-an-object\n");
        Path scalarExtraction = writeConfig("""
                semanticExtraction: not-an-object
                """);
        Path missingExtraction = writeConfig("""
                provider: openai-api
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(scalarRoot));
        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(scalarExtraction));
        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(missingExtraction));
    }

    @Test
    void rejectsUnknownFieldsAtRootExtractionAndShardingLevels() throws Exception {
        Path unknownRoot = writeConfig("""
                semanticExtraction: {}
                typo: true
                """);
        Path unknownExtraction = writeConfig("""
                semanticExtraction:
                  modle: gpt-5.6-sol
                """);
        Path unknownSharding = writeConfig("""
                semanticExtraction:
                  sharding:
                    mode: auto
                    targetInputToken: 100
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(unknownRoot));
        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(unknownExtraction));
        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(unknownSharding));
    }

    @Test
    void rejectsInvalidNumericValuesInsteadOfReplacingThemWithDefaults() throws Exception {
        List<Path> invalidConfigs = List.of(
                writeConfig("""
                        semanticExtraction:
                          maxOutputTokens: 0
                        """),
                writeConfig("""
                        semanticExtraction:
                          requestTimeoutSeconds: many
                        """),
                writeConfig("""
                        semanticExtraction:
                          maxTransportRetries: -1
                        """),
                writeConfig("""
                        semanticExtraction:
                          artifactRetention: forever
                        """),
                writeConfig("""
                        semanticExtraction:
                          sharding:
                            targetInputTokens: 100.5
                        """));

        for (Path invalidConfig : invalidConfigs) {
            assertThrows(IllegalArgumentException.class,
                    () -> new SemanticExtractionConfigLoader().load(invalidConfig),
                    invalidConfig.toString());
        }
    }

    @Test
    void rejectsTargetInputEstimateAboveMaximumInputEstimate() throws Exception {
        Path config = writeConfig("""
                semanticExtraction:
                  sharding:
                    targetInputTokens: 801
                    maxInputTokens: 800
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(config));
    }

    @Test
    void rejectsModelsOutsideTheApprovedExtractionProfile() throws Exception {
        Path wrongModel = writeConfig("""
                semanticExtraction:
                  model: another-model
                """);
        Path wrongReasoning = writeConfig("""
                semanticExtraction:
                  reasoningEffort: high
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(wrongModel));
        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionConfigLoader().load(wrongReasoning));
    }

    @Test
    void resolvesRelativeInputAndOutputPathsFromConfigDirectory() throws Exception {
        Path configDirectory = Files.createDirectories(tempDir.resolve("config"));
        Path config = configDirectory.resolve("semantic-extraction.yml");
        Files.writeString(config, """
                semanticExtraction:
                  input: ../input/result.json
                  inputs:
                    - nested/second.json
                  output: ../output
                """);

        SemanticExtractionConfig loaded = new SemanticExtractionConfigLoader().load(config);

        assertEquals(List.of(
                configDirectory.resolve("nested/second.json").normalize(),
                tempDir.resolve("input/result.json")), loaded.inputs());
        assertEquals(tempDir.resolve("output"), loaded.output());
    }

    @Test
    void repositoryExamplesResolveFromTheirConfigDirectory() {
        Path root = repositoryRoot();
        Path examples = root.resolve("semantic-layer/examples");
        SemanticExtractionConfig api = new SemanticExtractionConfigLoader()
                .load(examples.resolve("semantic-extraction-openai-api.yml"));
        SemanticExtractionConfig codex = new SemanticExtractionConfigLoader()
                .load(examples.resolve("semantic-extraction-codex-session.yml"));
        Path expectedInput = root.resolve(
                "relation-detector/target/sample-data-parser-cli/results/mysql-v8_0-full.json");

        assertEquals(List.of(expectedInput), api.inputs());
        assertEquals(root.resolve("semantic-layer/target/semantic-extraction/openai-api-full"), api.output());
        assertEquals(List.of(expectedInput), codex.inputs());
        assertEquals(root.resolve("semantic-layer/target/semantic-extraction/codex-session-full"), codex.output());
    }

    private Path writeConfig(String contents) throws Exception {
        Path directory = Files.createTempDirectory(tempDir, "config-");
        Path config = directory.resolve("semantic-extraction.yml");
        Files.writeString(config, contents);
        return config;
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null
                && !(Files.isRegularFile(current.resolve("pom.xml"))
                && Files.isDirectory(current.resolve("semantic-layer/examples")))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("repository root cannot be located");
        }
        return current;
    }
}
