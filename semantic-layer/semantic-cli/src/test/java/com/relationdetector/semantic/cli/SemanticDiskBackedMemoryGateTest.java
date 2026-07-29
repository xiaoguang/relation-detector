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
    private static final int GENERATED_EXPRESSION_BYTES = 128 * 1024;
    private static final int GENERATED_COLUMNS_PER_TABLE = 512;
    private static final long DEFAULT_CHILD_TIMEOUT_MINUTES = 25;
    private static final long MAX_GATE_TEST_MINUTES = 120;

    @TempDir
    Path tempDir;

    @Test
    @Timeout(value = MAX_GATE_TEST_MINUTES, unit = TimeUnit.MINUTES)
    void completeBuildAndRequestOnlyRunWithinConfiguredChildHeap() throws Exception {
        int mebibytes = Integer.getInteger("semanticMemoryGateMiB", 1);
        String heap = System.getProperty("semanticMemoryGateHeap", "96m");
        long childTimeoutMinutes = Long.getLong(
                "semanticMemoryGateTimeoutMinutes", DEFAULT_CHILD_TIMEOUT_MINUTES);
        Path input = tempDir.resolve("scan-%04d-mib.json".formatted(mebibytes));
        Path output = tempDir.resolve("semantic-output");
        int maximumShards = writeCompleteScan(input, (long) mebibytes * 1024L * 1024L);

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
                "--target-input-tokens", "50000",
                "--max-input-tokens", "100000",
                "--max-shards", Integer.toString(maximumShards))
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        assertTrue(process.waitFor(
                        Duration.ofMinutes(childTimeoutMinutes).toMillis(), TimeUnit.MILLISECONDS),
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

    private int writeCompleteScan(Path target, long minimumBytes) throws Exception {
        int generatedColumnCount = Math.max(
                1,
                Math.toIntExact((minimumBytes + GENERATED_EXPRESSION_BYTES - 1)
                        / GENERATED_EXPRESSION_BYTES));
        int tableCount = (generatedColumnCount + GENERATED_COLUMNS_PER_TABLE - 1)
                / GENERATED_COLUMNS_PER_TABLE;
        String generatedExpression = generatedExpression();
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
            writeInventory(generator, tableCount, generatedColumnCount, generatedExpression);
            for (String section : List.of(
                    "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
                    "namingEvidence", "derivedNamingEvidence", "warnings")) {
                generator.writeArrayFieldStart(section);
                generator.writeEndArray();
            }
            generator.writeEndObject();
        }
        assertTrue(Files.size(target) >= minimumBytes);
        return Math.max(16, (generatedColumnCount + tableCount * 3) * 2);
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

    private void writeInventory(
            JsonGenerator generator,
            int tableCount,
            int generatedColumnCount,
            String generatedExpression
    ) throws Exception {
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
        generator.writeNumberField("tables", tableCount);
        generator.writeNumberField("columns", generatedColumnCount + tableCount);
        generator.writeNumberField("constraints", tableCount);
        generator.writeNumberField("indexes", tableCount);
        generator.writeEndObject();
        generator.writeArrayFieldStart("tables");
        for (int table = 0; table < tableCount; table++) {
            generator.writeStartObject();
            generator.writeStringField("catalog", "shop");
            generator.writeNullField("schema");
            generator.writeStringField("tableName", tableName(table));
            generator.writeStringField("tableType", "BASE TABLE");
            generator.writeStringField("engine", "InnoDB");
            generator.writeStringField("comment", "Disk-backed semantic memory-gate inventory table " + table);
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("columns");
        int generated = 0;
        for (int table = 0; table < tableCount; table++) {
            writeColumn(generator, tableName(table), "id", "bigint", "bigint",
                    false, "", "", 1);
            int remaining = generatedColumnCount - generated;
            int tableColumns = Math.min(GENERATED_COLUMNS_PER_TABLE, remaining);
            for (int column = 0; column < tableColumns; column++) {
                writeColumn(
                        generator,
                        tableName(table),
                        "generated_%04d".formatted(column + 1),
                        "varchar",
                        "varchar(255)",
                        true,
                        "STORED GENERATED",
                        generatedExpression,
                        column + 2);
                generated++;
            }
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("constraints");
        for (int table = 0; table < tableCount; table++) {
            generator.writeStartObject();
            generator.writeStringField("catalog", "shop");
            generator.writeNullField("schema");
            generator.writeStringField("tableName", tableName(table));
            generator.writeStringField("constraintName", "pk_" + tableName(table));
            generator.writeStringField("constraintType", "PRIMARY_KEY");
            generator.writeArrayFieldStart("columns");
            generator.writeString("id");
            generator.writeEndArray();
            generator.writeNullField("referencedCatalog");
            generator.writeNullField("referencedSchema");
            generator.writeNullField("referencedTable");
            generator.writeArrayFieldStart("referencedColumns");
            generator.writeEndArray();
            generator.writeNullField("updateRule");
            generator.writeNullField("deleteRule");
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeArrayFieldStart("indexes");
        for (int table = 0; table < tableCount; table++) {
            generator.writeStartObject();
            generator.writeStringField("catalog", "shop");
            generator.writeNullField("schema");
            generator.writeStringField("tableName", tableName(table));
            generator.writeStringField("indexName", "pk_" + tableName(table));
            generator.writeBooleanField("unique", true);
            generator.writeBooleanField("primary", true);
            generator.writeStringField("indexType", "BTREE");
            generator.writeBooleanField("visible", true);
            generator.writeArrayFieldStart("columns");
            generator.writeString("id");
            generator.writeEndArray();
            generator.writeArrayFieldStart("expressions");
            generator.writeEndArray();
            generator.writeArrayFieldStart("subParts");
            generator.writeEndArray();
            generator.writeArrayFieldStart("seqInIndex");
            generator.writeNumber(1);
            generator.writeEndArray();
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeColumn(
            JsonGenerator generator,
            String table,
            String column,
            String dataType,
            String columnType,
            boolean nullable,
            String extra,
            String generationExpression,
            int ordinal
    ) throws Exception {
        generator.writeStartObject();
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeStringField("tableName", table);
        generator.writeStringField("columnName", column);
        generator.writeStringField("dataType", dataType);
        generator.writeStringField("columnType", columnType);
        generator.writeBooleanField("nullable", nullable);
        generator.writeNullField("defaultValue");
        generator.writeStringField("extra", extra);
        generator.writeStringField("generationExpression", generationExpression);
        generator.writeNumberField("ordinalPosition", ordinal);
        generator.writeEndObject();
    }

    private String generatedExpression() {
        String prefix = "CONCAT(CAST(id AS CHAR),";
        String segment = "'semantic_memory_gate_segment',";
        StringBuilder expression = new StringBuilder(GENERATED_EXPRESSION_BYTES + segment.length());
        expression.append(prefix);
        while (expression.length() + segment.length() + 3 < GENERATED_EXPRESSION_BYTES) {
            expression.append(segment);
        }
        expression.append("'end')");
        return expression.toString();
    }

    private String tableName(int index) {
        return "memory_gate_%05d".formatted(index + 1);
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
