package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.semantic.extract.ArtifactRetention;

final class SemanticCommandArgumentsTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesCliOverridesBeforeValidatingTheMergedTokenEstimateLimits() throws Exception {
        Path config = writeConfig(300, 600);

        SemanticCommandArguments arguments = SemanticCommandArguments.parse(new String[] {
                "extract",
                "--config", config.toString(),
                "--target-input-tokens", "700",
                "--max-input-tokens", "800"
        });

        assertEquals(700, arguments.sharding().targetInputTokens());
        assertEquals(800, arguments.sharding().maxInputTokens());
        assertEquals(List.of(config.getParent().resolve("input.json")), arguments.inputs());
        assertEquals(config.getParent().resolve("output"), arguments.output());
    }

    @Test
    void rejectsMergedCliOverrideWhenTargetEstimateExceedsMaximumEstimate() throws Exception {
        Path config = writeConfig(300, 600);

        assertThrows(IllegalArgumentException.class,
                () -> SemanticCommandArguments.parse(new String[] {
                        "extract",
                        "--config", config.toString(),
                        "--target-input-tokens", "700"
                }));
    }

    @Test
    void cliArtifactRetentionOverridesConfig() throws Exception {
        Path config = writeConfig(300, 600, "final-only");

        SemanticCommandArguments arguments = SemanticCommandArguments.parse(new String[] {
                "extract",
                "--config", config.toString(),
                "--artifact-retention", "full"
        });

        assertEquals(ArtifactRetention.FULL, arguments.artifactRetention());
    }

    private Path writeConfig(int targetInputTokens, int maxInputTokens) throws Exception {
        return writeConfig(targetInputTokens, maxInputTokens, "full");
    }

    private Path writeConfig(int targetInputTokens, int maxInputTokens, String artifactRetention) throws Exception {
        Path directory = Files.createDirectories(tempDir.resolve("config"));
        Path config = directory.resolve("semantic-extraction.yml");
        Files.writeString(config, """
                semanticExtraction:
                  input: input.json
                  output: output
                  artifactRetention: %s
                  sharding:
                    targetInputTokens: %d
                    maxInputTokens: %d
                """.formatted(artifactRetention, targetInputTokens, maxInputTokens));
        return config;
    }
}
