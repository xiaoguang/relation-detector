package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.relationdetector.contracts.Enums.ErrorCode;
import com.relationdetector.core.common.CommonDatabaseAdaptor;

class RuntimeConfigurationCliTest {
    @TempDir
    Path tempDir;

    @Test
    void missingJdbcForLiveMetadataIsConfigurationError() throws Exception {
        Path config = writeConfig("""
                database:
                  type: common
                sources:
                  metadata:
                    enabled: true
                """);

        assertEquals(ErrorCode.CONFIG_FORMAT_ERROR.code(), run("scan", "--config", config.toString()));
    }

    @Test
    void missingInputFileRemainsInputError() throws Exception {
        Path config = writeConfig("""
                database:
                  type: common
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - missing.sql
                """);

        assertEquals(ErrorCode.INPUT_FILE_ERROR.code(), run("scan", "--config", config.toString()));
    }

    @Test
    void invalidMinConfidenceOverrideIsArgumentError() throws Exception {
        Path query = tempDir.resolve("query.sql");
        Files.writeString(query, "SELECT 1;\n");
        Path config = writeConfig("""
                database:
                  type: common
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - query.sql
                """);

        assertEquals(ErrorCode.ARGUMENT_ERROR.code(), run("scan", "--config", config.toString(),
                "--min-confidence", "1.01"));
    }

    @Test
    void invalidYamlMinConfidenceIsConfigurationError() throws Exception {
        Path query = tempDir.resolve("query.sql");
        Files.writeString(query, "SELECT 1;\n");
        Path config = writeConfig("""
                database:
                  type: common
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - query.sql
                output:
                  minConfidence: -0.01
                """);

        assertEquals(ErrorCode.CONFIG_FORMAT_ERROR.code(), run("scan", "--config", config.toString()));
    }

    @Test
    void invalidYamlParserModeIsConfigurationError() throws Exception {
        Path query = tempDir.resolve("query.sql");
        Files.writeString(query, "SELECT 1;\n");
        Path config = writeConfig("""
                database:
                  type: common
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - query.sql
                parser:
                  mode: unsupported
                """);

        assertEquals(ErrorCode.CONFIG_FORMAT_ERROR.code(), run("scan", "--config", config.toString()));
    }

    @Test
    void invalidParserModeOverrideIsArgumentError() throws Exception {
        Path query = tempDir.resolve("query.sql");
        Files.writeString(query, "SELECT 1;\n");
        Path config = writeConfig("""
                database:
                  type: common
                sources:
                  metadata:
                    enabled: false
                  logs:
                    enabled: true
                    files:
                      - query.sql
                """);

        assertEquals(ErrorCode.ARGUMENT_ERROR.code(), run("scan", "--config", config.toString(),
                "--parser-mode", "unsupported"));
    }

    private Path writeConfig(String yaml) throws Exception {
        Path file = tempDir.resolve("scan-" + System.nanoTime() + ".yml");
        Files.writeString(file, yaml);
        return file;
    }

    private int run(String... args) {
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try {
            System.setErr(new PrintStream(error, true, StandardCharsets.UTF_8));
            return new Main.MainCommand(new SingleScanRunner(),
                    ignored -> new AdaptorRegistry(java.util.List.of(new CommonDatabaseAdaptor()))).run(args);
        } finally {
            System.setErr(previous);
        }
    }
}
