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
                version: 1
                report: out/report.json
                cases:
                  - id: mysql-80
                    config: configs/mysql.yml
                    output: out/mysql.json
                    directOutput: out/mysql-direct.json
                """);

        BatchManifest loaded = new BatchManifestLoader().load(manifest);

        assertEquals(4, loaded.caseParallelism());
        assertEquals(8, loaded.maxWorkerThreads());
        assertEquals(BatchFailurePolicy.CONTINUE, loaded.failurePolicy());
        assertEquals(tempDir.resolve("out/report.json").toAbsolutePath().normalize(), loaded.report());
        assertEquals(tempDir.resolve("configs/mysql.yml").toAbsolutePath().normalize(),
                loaded.cases().get(0).config());
    }

    @Test
    void rejectsOutputCollisionsBeforeAnyCaseRuns() throws Exception {
        Files.writeString(tempDir.resolve("one.yml"), "database:\n  type: MYSQL\n");
        Files.writeString(tempDir.resolve("two.yml"), "database:\n  type: MYSQL\n");
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 1
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

        assertTrue(error.getMessage().contains("output path is used more than once"));
    }

    @Test
    void rejectsUnknownManifestKeys() throws Exception {
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 1
                surprise: true
                cases: []
                """);

        assertThrows(IllegalArgumentException.class, () -> new BatchManifestLoader().load(manifest));
    }
}
