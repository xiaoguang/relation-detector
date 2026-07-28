package com.relationdetector.semantic.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticDiskBackedMemoryGateTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int PADDING_RECORD_BYTES = 64 * 1024;

    @TempDir
    Path tempDir;

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void completeBuildAndRequestOnlyRunWithinConfiguredChildHeap() throws Exception {
        int mebibytes = Integer.getInteger("semanticMemoryGateMiB", 1);
        String heap = System.getProperty("semanticMemoryGateHeap", "96m");
        Path input = tempDir.resolve("scan-%04d-mib.json".formatted(mebibytes));
        Path output = tempDir.resolve("semantic-output");
        writeCompleteScan(input, (long) mebibytes * 1024L * 1024L);

        Path stdout = tempDir.resolve("child.stdout");
        Path stderr = tempDir.resolve("child.stderr");
        Process process = new ProcessBuilder(
                javaExecutable(),
                "-Xmx" + heap,
                "-cp", testClasspath(),
                Main.class.getName(),
                "e2e",
                "--input", input.toString(),
                "--output", output.toString(),
                "--name", "memory-gate",
                "--target-input-tokens", "10000",
                "--max-input-tokens", "50000",
                "--max-shards", "16")
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        assertTrue(process.waitFor(Duration.ofMinutes(25).toMillis(), TimeUnit.MILLISECONDS),
                "semantic memory-gate child JVM timed out");
        assertEquals(0, process.exitValue(), () -> {
            try {
                return "child stderr: " + Files.readString(stderr);
            } catch (Exception failure) {
                return "child stderr could not be read";
            }
        });
        assertTrue(Files.isRegularFile(output.resolve(
                "semantic-kg/memory-gate/semantic-kg.json")));
        assertTrue(Files.isDirectory(output.resolve(
                "semantic-extraction/memory-gate")));
        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains("-work-")),
                    "semantic disk-backed workspace was not cleaned");
        }
    }

    private void writeCompleteScan(Path target, long minimumBytes) throws Exception {
        String padding = "x".repeat(PADDING_RECORD_BYTES);
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.writeStartObject();
            generator.writeObjectFieldStart("database");
            generator.writeStringField("type", "mysql");
            generator.writeStringField("catalog", "shop");
            generator.writeStringField("schema", "");
            generator.writeEndObject();
            generator.writeStringField("generatedAt", "2026-07-28T00:00:00Z");
            writeSummary(generator);
            writeInventory(generator);
            for (String section : List.of(
                    "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
                    "namingEvidence", "derivedNamingEvidence", "warnings")) {
                generator.writeArrayFieldStart(section);
                generator.writeEndArray();
            }
            generator.writeArrayFieldStart("syntheticPadding");
            long written = 0;
            while (written < minimumBytes) {
                generator.writeString(padding);
                written += PADDING_RECORD_BYTES;
            }
            generator.writeEndArray();
            generator.writeEndObject();
        }
        assertTrue(Files.size(target) >= minimumBytes);
    }

    private void writeSummary(JsonGenerator generator) throws Exception {
        generator.writeObjectFieldStart("summary");
        for (String field : List.of(
                "directRelationshipCount", "derivedRelationshipCount", "totalRelationshipCount",
                "directDataLineageCount", "derivedDataLineageCount", "totalDataLineageCount",
                "directNamingEvidenceCount", "derivedNamingEvidenceCount", "totalNamingEvidenceCount",
                "warningCount")) {
            generator.writeNumberField(field, 0);
        }
        generator.writeArrayFieldStart("sources");
        generator.writeString("metadata");
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeInventory(JsonGenerator generator) throws Exception {
        generator.writeObjectFieldStart("metadataInventory");
        generator.writeStringField("status", "COMPLETE");
        generator.writeObjectFieldStart("scope");
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeArrayFieldStart("includeTables");
        generator.writeEndArray();
        generator.writeArrayFieldStart("excludeTables");
        generator.writeEndArray();
        generator.writeEndObject();
        generator.writeObjectFieldStart("counts");
        generator.writeNumberField("tables", 1);
        generator.writeNumberField("columns", 1);
        generator.writeNumberField("constraints", 0);
        generator.writeNumberField("indexes", 0);
        generator.writeEndObject();
        generator.writeArrayFieldStart("tables");
        generator.writeStartObject();
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeStringField("tableName", "orders");
        generator.writeStringField("tableType", "BASE TABLE");
        generator.writeEndObject();
        generator.writeEndArray();
        generator.writeArrayFieldStart("columns");
        generator.writeStartObject();
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeStringField("tableName", "orders");
        generator.writeStringField("columnName", "id");
        generator.writeStringField("dataType", "bigint");
        generator.writeStringField("columnType", "bigint");
        generator.writeBooleanField("nullable", false);
        generator.writeNumberField("ordinalPosition", 1);
        generator.writeEndObject();
        generator.writeEndArray();
        generator.writeArrayFieldStart("constraints");
        generator.writeEndArray();
        generator.writeArrayFieldStart("indexes");
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private String javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private String testClasspath() {
        String surefire = System.getProperty("surefire.test.class.path");
        return surefire == null || surefire.isBlank()
                ? System.getProperty("java.class.path")
                : surefire;
    }
}
