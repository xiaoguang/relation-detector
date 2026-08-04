package com.relationdetector.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.Enums.ErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutputBundleCliTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void jsonBundlePublishesExactlyResultAndDirectFromOneScan() throws Exception {
        Path input = tempDir.resolve("input.sql");
        Path config = tempDir.resolve("config.yml");
        Path bundle = tempDir.resolve("bundle");
        Files.writeString(input,
                "SELECT o.customer_id FROM orders o JOIN customers c ON o.customer_id = c.id;\n");
        Files.writeString(config, config(input, "json"));

        int code = new Main.MainCommand().run(new String[] {
                "scan", "--config", config.toString(), "--output-bundle", bundle.toString()
        });

        assertEquals(ErrorCode.OK.code(), code);
        try (Stream<Path> entries = Files.list(bundle)) {
            assertEquals(Set.of("result.json", "direct.json"), entries
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet()));
        }
        JsonNode result = JSON.readTree(bundle.resolve("result.json").toFile());
        JsonNode direct = JSON.readTree(bundle.resolve("direct.json").toFile());
        assertTrue(result.isObject());
        assertTrue(direct.isObject());
        assertFalse(direct.has("derivedRelationships") && direct.path("derivedRelationships").size() > 0);
    }

    @Test
    void existingBundleIsPreservedAndReportedAsOutputFailure() throws Exception {
        Path input = tempDir.resolve("input.sql");
        Path config = tempDir.resolve("config.yml");
        Path bundle = tempDir.resolve("bundle");
        Files.writeString(input, "SELECT 1;\n");
        Files.writeString(config, config(input, "json"));
        Files.createDirectories(bundle);
        Files.writeString(bundle.resolve("keep.txt"), "keep");

        int code = new Main.MainCommand().run(new String[] {
                "scan", "--config", config.toString(), "--output-bundle", bundle.toString()
        });

        assertEquals(ErrorCode.OUTPUT_WRITE_ERROR.code(), code);
        assertEquals("keep", Files.readString(bundle.resolve("keep.txt")));
        assertFalse(Files.exists(bundle.resolve("result.json")));
        assertFalse(Files.exists(bundle.resolve("direct.json")));
    }

    @Test
    void bundleRejectsTableOutputAndMutualExclusionBeforeScanning() throws Exception {
        Path input = tempDir.resolve("input.sql");
        Path tableConfig = tempDir.resolve("table.yml");
        Path jsonConfig = tempDir.resolve("json.yml");
        Files.writeString(input, "SELECT 1;\n");
        Files.writeString(tableConfig, config(input, "table"));
        Files.writeString(jsonConfig, config(input, "json"));

        assertEquals(ErrorCode.ARGUMENT_ERROR.code(), new Main.MainCommand().run(new String[] {
                "scan", "--config", tableConfig.toString(), "--output-bundle", tempDir.resolve("table-bundle").toString()
        }));
        assertEquals(ErrorCode.ARGUMENT_ERROR.code(), new Main.MainCommand().run(new String[] {
                "scan", "--config", jsonConfig.toString(),
                "--output", tempDir.resolve("result.json").toString(),
                "--output-bundle", tempDir.resolve("json-bundle").toString()
        }));
    }

    @Test
    void removedDirectOutputIsAnUnknownArgument() throws Exception {
        Path input = tempDir.resolve("input.sql");
        Path config = tempDir.resolve("config.yml");
        Files.writeString(input, "SELECT 1;\n");
        Files.writeString(config, config(input, "json"));

        int code = new Main.MainCommand().run(new String[] {
                "scan", "--config", config.toString(),
                "--output", tempDir.resolve("result.json").toString(),
                "--direct-output", tempDir.resolve("direct.json").toString()
        });

        assertEquals(ErrorCode.ARGUMENT_ERROR.code(), code);
        assertFalse(Files.exists(tempDir.resolve("result.json")));
        assertFalse(Files.exists(tempDir.resolve("direct.json")));
    }

    private String config(Path input, String format) {
        return """
                database:
                  type: common
                  schema: portable
                sources:
                  metadata:
                    enabled: false
                  ddl:
                    enabled: false
                  logs:
                    enabled: true
                    format: PLAIN_SQL
                    files:
                      - %s
                output:
                  format: %s
                  minConfidence: 0.0
                  includeEvidence: true
                  includeWarnings: true
                  includeObservationCounts: true
                parser:
                  mode: token-event
                derivedPaths:
                  enabled: true
                """.formatted(input.toAbsolutePath(), format);
    }
}
