package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchManifestLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesCasePathsAndAppliesStableDefaults() throws Exception {
        Files.createDirectories(tempDir.resolve("configs"));
        Files.writeString(tempDir.resolve("configs/mysql.yml"), "database:\n  type: MYSQL\n");
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 2
                report: out/report.json
                cases:
                  - id: mysql-80
                    config: configs/mysql.yml
                    outputBundle: out/mysql
                """);

        BatchManifest loaded = new BatchManifestLoader().load(manifest);

        assertEquals(4, loaded.caseParallelism());
        assertEquals(8, loaded.maxWorkerThreads());
        assertEquals(BatchFailurePolicy.CONTINUE, loaded.failurePolicy());
        assertEquals(tempDir.resolve("out/report.json").toAbsolutePath().normalize(), loaded.report());
        assertEquals(tempDir.resolve("configs/mysql.yml").toAbsolutePath().normalize(),
                loaded.cases().get(0).config());
        assertEquals(tempDir.resolve("out/mysql").toAbsolutePath().normalize(),
                loaded.cases().get(0).outputBundle());
    }

    @Test
    void rejectsOutputCollisionsBeforeAnyCaseRuns() throws Exception {
        Files.writeString(tempDir.resolve("one.yml"), "database:\n  type: MYSQL\n");
        Files.writeString(tempDir.resolve("two.yml"), "database:\n  type: MYSQL\n");
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 2
                cases:
                  - id: one
                    config: one.yml
                    output: same.json
                  - id: two
                    config: two.yml
                    output: same.json
                """);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new BatchManifestLoader().load(manifest));

        assertTrue(error.getMessage().contains("artifact paths overlap"));
    }

    @Test
    void rejectsVersionOneAndLegacyDirectOutput() throws Exception {
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 1
                cases:
                  - id: one
                    config: one.yml
                    output: one.json
                    directOutput: one-direct.json
                """);

        assertThrows(IllegalArgumentException.class, () -> new BatchManifestLoader().load(manifest));
    }

    @Test
    void rejectsMissingMixedAndNestedArtifactPaths() throws Exception {
        Path missing = tempDir.resolve("missing.yml");
        Files.writeString(missing, """
                version: 2
                cases:
                  - id: one
                    config: one.yml
                """);
        assertThrows(IllegalArgumentException.class, () -> new BatchManifestLoader().load(missing));

        Path mixed = tempDir.resolve("mixed.yml");
        Files.writeString(mixed, """
                version: 2
                cases:
                  - id: one
                    config: one.yml
                    output: one.json
                    outputBundle: one
                """);
        assertThrows(IllegalArgumentException.class, () -> new BatchManifestLoader().load(mixed));

        Path nested = tempDir.resolve("nested.yml");
        Files.writeString(nested, """
                version: 2
                report: artifacts/report.json
                cases:
                  - id: one
                    config: one.yml
                    outputBundle: artifacts
                """);
        assertThrows(IllegalArgumentException.class, () -> new BatchManifestLoader().load(nested));
    }

    @Test
    void rejectsUnknownManifestKeys() throws Exception {
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 2
                surprise: true
                cases: []
                """);

        assertThrows(IllegalArgumentException.class, () -> new BatchManifestLoader().load(manifest));
    }
}
