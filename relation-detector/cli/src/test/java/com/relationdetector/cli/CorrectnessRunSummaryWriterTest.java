package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class CorrectnessRunSummaryWriterTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesTotalsAndDialectVersionBreakdown() throws Exception {
        Path root = Files.createTempDirectory("correctness-summary").resolve("correctness");
        Path mysql = root.resolve("mysql/v8_0/example/manifest.yml");
        Path oracle = root.resolve("oracle/example/manifest.yml");
        Files.createDirectories(mysql.getParent());
        Files.createDirectories(oracle.getParent());
        Path output = root.getParent().resolve("target/correctness-run-summary.json");

        CorrectnessRunSummaryWriter.write(output, root, "full", 3, 2, List.of(
                new CorrectnessFixtureRunnerTest.FixtureExecution(mysql, null, 12),
                new CorrectnessFixtureRunnerTest.FixtureExecution(oracle, new IllegalStateException("boom"), 8)));

        var result = JSON.readTree(output.toFile());
        assertEquals(3, result.path("discovered").asInt());
        assertEquals(2, result.path("selected").asInt());
        assertEquals(2, result.path("executed").asInt());
        assertEquals(1, result.path("passed").asInt());
        assertEquals(1, result.path("failed").asInt());
        assertEquals("mysql/8_0", result.path("dialectVersions").get(0).path("id").asText());
        assertEquals("oracle/root", result.path("dialectVersions").get(1).path("id").asText());
    }
}
