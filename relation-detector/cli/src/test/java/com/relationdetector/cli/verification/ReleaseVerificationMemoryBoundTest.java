package com.relationdetector.cli.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseVerificationMemoryBoundTest {
    private static final int FACT_COUNT = 1_024;
    private static final int PADDING_BYTES = 128 * 1_024;

    @TempDir
    Path tempDir;

    @Test
    void validatesAndFingerprintsOneHundredTwentyEightMibUnderSixtyFourMibHeap() throws Exception {
        Path results = tempDir.resolve("results");
        Files.createDirectories(results);
        Path bundle = results.resolve("large");
        Files.createDirectories(bundle);
        Path direct = bundle.resolve("direct.json");
        writeLargeResult(direct);
        Files.writeString(bundle.resolve("result.json"), emptyResult());
        assertTrue(Files.size(direct) >= 128L * 1024 * 1024);

        Path validation = tempDir.resolve("validation.json");
        runChild("validate-results",
                "--result-dir", results.toString(),
                "--expected-categories", "1",
                "--output", validation.toString());
        assertEquals("PASS",
                ReleaseVerificationJson.MAPPER.readTree(validation.toFile()).path("status").asText());

        Path workspace = tempDir.resolve("fingerprint-work");
        Path fingerprints = tempDir.resolve("fingerprints.tsv");
        runChild("fingerprint",
                "--workspace", workspace.toString(),
                "--output", fingerprints.toString(),
                direct.toString());
        assertEquals(64, Files.readString(fingerprints).split("\\t", 2)[0].length());
        assertFalse(Files.exists(workspace));
    }

    private void runChild(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx64m");
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(ReleaseVerificationMain.class.getName());
        command.addAll(List.of(arguments));
        Path log = tempDir.resolve(arguments[0] + ".log");
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
        assertEquals(0, process.waitFor(), () -> {
            try {
                return Files.readString(log);
            } catch (Exception ignored) {
                return "child verification JVM failed";
            }
        });
    }

    private void writeLargeResult(Path path) throws Exception {
        String padding = "x".repeat(PADDING_BYTES);
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("""
                    {
                      "summary": {
                        "directRelationshipCount": %d,
                        "derivedRelationshipCount": 0,
                        "totalRelationshipCount": %d,
                        "directDataLineageCount": 0,
                        "derivedDataLineageCount": 0,
                        "totalDataLineageCount": 0,
                        "directNamingEvidenceCount": 0,
                        "derivedNamingEvidenceCount": 0,
                        "totalNamingEvidenceCount": 0,
                        "warningCount": 0
                      },
                      %s,
                      "relationships": [
                    """.formatted(FACT_COUNT, FACT_COUNT, inventory()));
            for (int index = 0; index < FACT_COUNT; index++) {
                if (index > 0) {
                    writer.write(',');
                }
                writer.write("{\"padding\":\"");
                writer.write(padding);
                writer.write("\"}");
            }
            writer.write("""
                      ],
                      "derivedRelationships": [],
                      "dataLineages": [],
                      "derivedDataLineages": [],
                      "namingEvidence": [],
                      "derivedNamingEvidence": [],
                      "warnings": []
                    }
                    """);
        }
    }

    private String emptyResult() {
        return """
                {
                  "summary": {
                    "directRelationshipCount": 0,
                    "derivedRelationshipCount": 0,
                    "totalRelationshipCount": 0,
                    "directDataLineageCount": 0,
                    "derivedDataLineageCount": 0,
                    "totalDataLineageCount": 0,
                    "directNamingEvidenceCount": 0,
                    "derivedNamingEvidenceCount": 0,
                    "totalNamingEvidenceCount": 0,
                    "warningCount": 0
                  },
                  %s,
                  "relationships": [],
                  "derivedRelationships": [],
                  "dataLineages": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """.formatted(inventory());
    }

    private String inventory() {
        return """
                "metadataInventory": {
                  "status": "COMPLETE",
                  "basis": "DDL_DECLARATIONS",
                  "scope": {
                    "catalog": "",
                    "schema": "sample",
                    "includeTables": [],
                    "excludeTables": []
                  },
                  "counts": {
                    "tables": 1,
                    "columns": 1,
                    "constraints": 0,
                    "indexes": 0
                  },
                  "tables": [{
                    "catalog": null,
                    "schema": "sample",
                    "tableName": "events",
                    "tableType": "TABLE"
                  }],
                  "columns": [{
                    "catalog": null,
                    "schema": "sample",
                    "tableName": "events",
                    "columnName": "id",
                    "dataType": "bigint",
                    "columnType": "bigint",
                    "nullable": false,
                    "ordinalPosition": 1
                  }],
                  "constraints": [],
                  "indexes": []
                }
                """;
    }
}
