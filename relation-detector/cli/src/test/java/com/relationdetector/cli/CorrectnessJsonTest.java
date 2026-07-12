package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorrectnessJsonTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripsEscapedGoldenValuesThroughJackson() throws Exception {
        Path file = tempDir.resolve("expected-relations.json");
        ExpectedRelations expected = new ExpectedRelations(
                List.of("line one\nline two", "quoted \"value\"", "back\\slash"),
                List.of("forbidden.table"));

        CorrectnessJson.writeRelations(file, expected);

        assertEquals(expected, CorrectnessJson.readRelations(file));
    }

    @Test
    void writesGoldenArraysOneValuePerLine() throws Exception {
        Path file = tempDir.resolve("expected-relations.json");

        CorrectnessJson.writeRelations(file, new ExpectedRelations(
                List.of("first", "second"),
                List.of("forbidden.table")));

        String json = Files.readString(file);
        assertTrue(json.contains("\n    \"first\",\n    \"second\"\n  ]"), json);
        assertTrue(json.contains("\n    \"forbidden.table\"\n  ]"), json);
    }

    @Test
    void malformedGoldenIncludesItsPathInTheFailure() throws Exception {
        Path file = tempDir.resolve("expected-relations.json");
        Files.writeString(file, "{\"fingerprints\":\"not-an-array\"}");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> CorrectnessJson.readRelations(file));

        assertTrue(error.getMessage().contains(file.toString()));
    }

    @Test
    void diagnosticsRequiresFixtureHash() throws Exception {
        Path file = tempDir.resolve("expected-diagnostics.json");
        Files.writeString(file, "{\"warningCodes\":{}}");

        assertThrows(IllegalArgumentException.class, () -> CorrectnessJson.readDiagnostics(file));
    }
}
