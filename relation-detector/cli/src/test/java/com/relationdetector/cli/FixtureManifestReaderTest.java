package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FixtureManifestReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsQuotedYamlValuesContainingColons() throws Exception {
        Path manifest = writeManifest("description: \"routine: refresh\"\n");

        FixtureManifest loaded = FixtureManifestReader.read(manifest);

        assertEquals("routine: refresh", loaded.description());
    }

    @Test
    void rejectsUnknownManifestFields() throws Exception {
        Path manifest = writeManifest("surprise: true\n");

        assertThrows(IllegalArgumentException.class, () -> FixtureManifestReader.read(manifest));
    }

    private Path writeManifest(String extra) throws Exception {
        Path manifest = tempDir.resolve("manifest.yml");
        Files.writeString(manifest, """
                id: example
                databaseType: MYSQL
                parserTarget: SQL
                input: input.sql
                expectedRelations: expected-relations.json
                expectedDiagnostics: expected-diagnostics.json
                """ + extra);
        return manifest;
    }
}
