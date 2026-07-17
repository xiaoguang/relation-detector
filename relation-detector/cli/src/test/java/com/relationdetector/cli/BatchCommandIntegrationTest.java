package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchCommandIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void batchCommandRunsCasesAndWritesStableReportOrder() throws Exception {
        Path sql = tempDir.resolve("input.sql");
        Files.writeString(sql, "SELECT o.customer_id FROM orders o JOIN customers c ON o.customer_id = c.id;");
        Path firstConfig = writeConfig("first.yml", sql);
        Path secondConfig = writeConfig("second.yml", sql);
        Path manifest = tempDir.resolve("batch.yml");
        Files.writeString(manifest, """
                version: 1
                execution:
                  caseParallelism: 2
                  maxWorkerThreads: 2
                report: report.json
                cases:
                  - id: first
                    config: %s
                    output: first.json
                    directOutput: first-direct.json
                  - id: second
                    config: %s
                    output: second.json
                """.formatted(firstConfig.getFileName(), secondConfig.getFileName()));

        int code = new Main.MainCommand().run(new String[]{"batch", "--manifest", manifest.toString()});

        assertEquals(0, code);
        assertTrue(Files.isRegularFile(tempDir.resolve("first.json")));
        assertTrue(Files.isRegularFile(tempDir.resolve("first-direct.json")));
        JsonNode report = JSON.readTree(tempDir.resolve("report.json").toFile());
        assertEquals("first", report.path("cases").get(0).path("id").asText());
        assertEquals("second", report.path("cases").get(1).path("id").asText());
        assertEquals("SUCCESS", report.path("cases").get(0).path("status").asText());
    }

    @Test
    void batchPreflightPreservesConfigFileAndFormatErrorCodes() throws Exception {
        Path malformed = tempDir.resolve("malformed.yml");
        Files.writeString(malformed, "database: [\n");
        Path malformedManifestFile = tempDir.resolve("malformed-manifest.yml");
        Files.writeString(malformedManifestFile, "cases: [\n");

        Path malformedManifest = writeSingleCaseManifest("malformed-batch.yml", malformed.getFileName());
        Path missingManifest = writeSingleCaseManifest("missing-batch.yml", Path.of("missing.yml"));

        assertEquals(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR.code(),
                new Main.MainCommand().run(new String[]{"batch", "--manifest", malformedManifest.toString()}));
        assertEquals(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FILE_ERROR.code(),
                new Main.MainCommand().run(new String[]{"batch", "--manifest", missingManifest.toString()}));
        assertEquals(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR.code(),
                new Main.MainCommand().run(new String[]{"batch", "--manifest", malformedManifestFile.toString()}));
        assertEquals(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FILE_ERROR.code(),
                new Main.MainCommand().run(new String[]{"batch", "--manifest",
                        tempDir.resolve("missing-manifest.yml").toString()}));
    }

    @Test
    void batchUsesTheSameRuntimeConfigurationValidation() throws Exception {
        Path invalid = tempDir.resolve("live-without-jdbc.yml");
        Files.writeString(invalid, """
                database:
                  type: mysql
                sources:
                  metadata:
                    enabled: true
                """);
        Path manifest = writeSingleCaseManifest("runtime-invalid-batch.yml", invalid.getFileName());

        assertEquals(com.relationdetector.contracts.Enums.ErrorCode.CONFIG_FORMAT_ERROR.code(),
                new Main.MainCommand().run(new String[]{"batch", "--manifest", manifest.toString()}));
    }

    private Path writeSingleCaseManifest(String name, Path config) throws Exception {
        Path manifest = tempDir.resolve(name);
        Files.writeString(manifest, """
                version: 1
                report: report.json
                cases:
                  - id: case
                    config: %s
                    output: output.json
                """.formatted(config));
        return manifest;
    }

    private Path writeConfig(String name, Path sql) throws Exception {
        Path config = tempDir.resolve(name);
        Files.writeString(config, """
                database:
                  type: MYSQL
                  schema: sample_data
                parser:
                  mode: token-event
                execution:
                  parallelism: 1
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: false
                  objects:
                    enabled: false
                  logs:
                    enabled: true
                    format: PLAIN_SQL
                    files:
                      - %s
                  dataProfile:
                    enabled: false
                output:
                  format: json
                  minConfidence: 0.0
                """.formatted(sql));
        return config;
    }
}
