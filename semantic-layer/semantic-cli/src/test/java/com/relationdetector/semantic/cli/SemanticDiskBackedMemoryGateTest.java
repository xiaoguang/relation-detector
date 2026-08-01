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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticDiskBackedMemoryGateTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int GENERATED_EXPRESSION_BYTES = 128 * 1024;
    private static final int GENERATED_COLUMNS_PER_TABLE = 512;
    // CN: 1,000个关联产生约百万条KG引用复制，覆盖低堆高扇出而不退化为磁盘吞吐基准。
    // EN: 1,000 associations yield about one million KG reference copies without becoming a disk benchmark.
    private static final int HIGH_FANOUT_RELATIONSHIPS = 1_000;
    private static final long MAX_GATE_TEST_MINUTES = 120;

    @TempDir
    Path tempDir;

    @Test
    @Timeout(value = MAX_GATE_TEST_MINUTES, unit = TimeUnit.MINUTES)
    void completeBuildAndRequestOnlyRunWithinConfiguredChildHeap() throws Exception {
        int mebibytes = requiredIntegerProperty("semanticMemoryGateMiB");
        String heap = requiredProperty("semanticMemoryGateHeap");
        long childTimeoutMinutes = requiredLongProperty("semanticMemoryGateTimeoutMinutes");
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

    @Test
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void oversizedStandaloneRawResultFailsByBudgetInsteadOfHeapExhaustion() throws Exception {
        int mebibytes = requiredIntegerProperty("semanticStandaloneRawMiB");
        Path input = tempDir.resolve("oversized-standalone.json");
        Path output = tempDir.resolve("standalone-output.json");
        Path stdout = tempDir.resolve("standalone.stdout");
        Path stderr = tempDir.resolve("standalone.stderr");
        writeOversizedRaw(input, (long) mebibytes * 1024L * 1024L);
        Files.writeString(output, "existing-output");

        Process process = new ProcessBuilder(
                javaExecutable(),
                "-Xmx32m",
                "-cp", testClasspath(),
                Main.class.getName(),
                "normalize-extraction",
                "--input", input.toString(),
                "--evidence-bundle", tempDir.resolve("unused-bundle.json").toString(),
                "--output", output.toString(),
                "--max-output-tokens", "75")
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        assertTrue(process.waitFor(5, TimeUnit.MINUTES),
                "oversized standalone child JVM timed out");
        assertEquals(1, process.exitValue());
        assertEquals("existing-output", Files.readString(output));
        assertFalse(Files.readString(stderr).contains("OutOfMemoryError"));
        try (var paths = Files.list(tempDir)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    @Tag("semantic-adversarial-memory")
    @Timeout(value = 15, unit = TimeUnit.MINUTES)
    void highFanoutEventCompletesBuildAndKgWithinLowHeap() throws Exception {
        Path input = tempDir.resolve("high-fanout-scan.json");
        Path output = tempDir.resolve("high-fanout-output");
        Path stdout = tempDir.resolve("high-fanout.stdout");
        Path stderr = tempDir.resolve("high-fanout.stderr");
        writeHighFanoutScan(input);

        Process process = new ProcessBuilder(
                javaExecutable(),
                "-Xmx96m",
                "-cp", testClasspath(),
                Main.class.getName(),
                "e2e",
                "--input", input.toString(),
                "--output", output.toString(),
                "--name", "high-fanout",
                "--target-input-tokens", "5000000",
                "--max-input-tokens", "10000000",
                "--max-shards", "64")
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        assertTrue(process.waitFor(10, TimeUnit.MINUTES),
                "high-fanout semantic child JVM timed out");
        assertEquals(0, process.exitValue(), () -> {
            try {
                return "child stderr: " + Files.readString(stderr);
            } catch (Exception failure) {
                return "child stderr could not be read";
            }
        });
        assertFalse(Files.readString(stderr).contains("OutOfMemoryError"));
        assertTrue(Files.isRegularFile(output.resolve(
                "semantic-kg/high-fanout/semantic-kg.json")));
        try (var paths = Files.walk(tempDir)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().contains("-work-")),
                    "high-fanout semantic workspace was not cleaned");
        }
    }

    private void writeOversizedRaw(Path target, long minimumBytes) throws Exception {
        byte[] chunk = new byte[64 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        try (OutputStream output = Files.newOutputStream(target)) {
            output.write("{\"entities\":[],\"events\":[],\"relations\":[],\"lineage\":[],"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write("\"metrics\":[],\"dimensions\":[],\"triplets\":[],\"reviewItems\":[],"
                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            output.write("\"padding\":\"".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            long written = 127;
            while (written < minimumBytes) {
                int length = (int) Math.min(chunk.length, minimumBytes - written);
                output.write(chunk, 0, length);
                written += length;
            }
            output.write("\"}".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        }
    }

    private static int requiredIntegerProperty(String name) {
        return Integer.parseInt(requiredProperty(name));
    }

    private static long requiredLongProperty(String name) {
        return Long.parseLong(requiredProperty(name));
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required memory-gate property was not injected: " + name);
        }
        return value;
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

    private void writeHighFanoutScan(Path target) throws Exception {
        try (OutputStream output = Files.newOutputStream(target);
             JsonGenerator generator = JSON.getFactory().createGenerator(output)) {
            generator.writeStartObject();
            generator.writeObjectFieldStart("database");
            generator.writeStringField("type", "mysql");
            generator.writeStringField("catalog", "shop");
            generator.writeStringField("schema", "");
            generator.writeEndObject();
            generator.writeStringField("generatedAt", "2026-07-28T00:00:00Z");
            writeHighFanoutSummary(generator);
            writeHighFanoutInventory(generator);
            writeHighFanoutRelationships(generator);
            writeHighFanoutLineage(generator);
            for (String section : List.of(
                    "derivedRelationships", "derivedDataLineages",
                    "namingEvidence", "derivedNamingEvidence", "warnings")) {
                generator.writeArrayFieldStart(section);
                generator.writeEndArray();
            }
            generator.writeEndObject();
        }
    }

    private void writeHighFanoutSummary(JsonGenerator generator) throws Exception {
        generator.writeObjectFieldStart("summary");
        generator.writeNumberField("directRelationshipCount", HIGH_FANOUT_RELATIONSHIPS);
        generator.writeNumberField("derivedRelationshipCount", 0);
        generator.writeNumberField("totalRelationshipCount", HIGH_FANOUT_RELATIONSHIPS);
        generator.writeNumberField("directDataLineageCount", 1);
        generator.writeNumberField("derivedDataLineageCount", 0);
        generator.writeNumberField("totalDataLineageCount", 1);
        generator.writeNumberField("directNamingEvidenceCount", 0);
        generator.writeNumberField("derivedNamingEvidenceCount", 0);
        generator.writeNumberField("totalNamingEvidenceCount", 0);
        generator.writeNumberField("warningCount", 0);
        generator.writeArrayFieldStart("sources");
        generator.writeString("metadata");
        generator.writeString("ddl");
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeHighFanoutInventory(JsonGenerator generator) throws Exception {
        generator.writeObjectFieldStart("metadataInventory");
        generator.writeStringField("status", "COMPLETE");
        generator.writeStringField("basis", "LIVE_METADATA");
        generator.writeObjectFieldStart("scope");
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeArrayFieldStart("includeTables");
        generator.writeEndArray();
        generator.writeArrayFieldStart("excludeTables");
        generator.writeEndArray();
        generator.writeEndObject();
        generator.writeObjectFieldStart("counts");
        generator.writeNumberField("tables", 2);
        generator.writeNumberField("columns", HIGH_FANOUT_RELATIONSHIPS + 1);
        generator.writeNumberField("constraints", 1);
        generator.writeNumberField("indexes", 1);
        generator.writeEndObject();
        generator.writeArrayFieldStart("tables");
        writeTable(generator, "source_rows");
        writeTable(generator, "audit_log");
        generator.writeEndArray();
        generator.writeArrayFieldStart("columns");
        for (int index = 0; index < HIGH_FANOUT_RELATIONSHIPS; index++) {
            writeColumn(generator, "source_rows", sourceColumn(index), "bigint", "bigint",
                    false, "", "", index + 1);
        }
        writeColumn(generator, "audit_log", "id", "bigint", "bigint", false, "", "", 1);
        generator.writeEndArray();
        generator.writeArrayFieldStart("constraints");
        generator.writeStartObject();
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeStringField("tableName", "audit_log");
        generator.writeStringField("constraintName", "pk_audit_log");
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
        generator.writeEndArray();
        generator.writeArrayFieldStart("indexes");
        writePhysicalIndex(generator, "audit_log", "pk_audit_log", "id");
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeHighFanoutRelationships(JsonGenerator generator) throws Exception {
        generator.writeArrayFieldStart("relationships");
        for (int index = 0; index < HIGH_FANOUT_RELATIONSHIPS; index++) {
            generator.writeStartObject();
            generator.writeStringField("id", "relationship:high-fanout:%05d".formatted(index));
            writeEndpoint(generator, "source", "shop.source_rows", sourceColumn(index));
            writeEndpoint(generator, "target", "shop.audit_log", "id");
            generator.writeStringField("relationType", "FK_LIKE");
            generator.writeStringField("relationSubType", "INFERRED_JOIN_FK");
            generator.writeNumberField("confidence", 0.82);
            generator.writeArrayFieldStart("evidence");
            generator.writeStartObject();
            generator.writeStringField("type", "PROCEDURE_JOIN");
            generator.writeStringField("sourceType", "PLAIN_SQL");
            generator.writeNumberField("score", 0.82);
            generator.writeStringField("source", "procedures/high_fanout.sql");
            generator.writeStringField("detail", "typed high-fanout predicate");
            generator.writeObjectFieldStart("attributes");
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeEndArray();
            generator.writeArrayFieldStart("rawEvidence");
            generator.writeEndArray();
            generator.writeArrayFieldStart("warnings");
            generator.writeEndArray();
            generator.writeObjectFieldStart("attributes");
            generator.writeEndObject();
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    private void writeHighFanoutLineage(JsonGenerator generator) throws Exception {
        generator.writeArrayFieldStart("dataLineages");
        generator.writeStartObject();
        generator.writeStringField("id", "lineage:high-fanout-event");
        generator.writeArrayFieldStart("sources");
        generator.writeStartObject();
        generator.writeStringField("table", "shop.source_rows");
        generator.writeStringField("column", sourceColumn(0));
        generator.writeEndObject();
        generator.writeEndArray();
        writeEndpoint(generator, "target", "shop.audit_log", "id");
        generator.writeStringField("flowKind", "VALUE");
        generator.writeStringField("transformType", "DIRECT");
        generator.writeNumberField("confidence", 0.9);
        generator.writeArrayFieldStart("evidence");
        generator.writeStartObject();
        generator.writeStringField("type", "DATA_LINEAGE");
        generator.writeStringField("transformType", "DIRECT");
        generator.writeStringField("sourceType", "PLAIN_SQL");
        generator.writeNumberField("score", 0.9);
        generator.writeStringField("source", "procedures/high_fanout.sql");
        generator.writeStringField("detail", "typed procedure write");
        generator.writeObjectFieldStart("attributes");
        generator.writeStringField("sourceObjectType", "PROCEDURE");
        generator.writeStringField("sourceObjectName", "sp_high_fanout");
        generator.writeStringField("sourceObjectIdentity", "shop.sp_high_fanout()");
        generator.writeStringField("sourceStatementId", "routine:sp_high_fanout");
        generator.writeStringField("mappingKind", "INSERT_SELECT");
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndArray();
        generator.writeArrayFieldStart("rawEvidence");
        generator.writeEndArray();
        generator.writeArrayFieldStart("warnings");
        generator.writeEndArray();
        generator.writeObjectFieldStart("attributes");
        generator.writeStringField("mappingKind", "INSERT_SELECT");
        generator.writeEndObject();
        generator.writeEndObject();
        generator.writeEndArray();
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
        generator.writeStringField("basis", "LIVE_METADATA");
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
            writeIndexMembers(generator, "id");
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private void writeTable(JsonGenerator generator, String table) throws Exception {
        generator.writeStartObject();
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeStringField("tableName", table);
        generator.writeStringField("tableType", "BASE TABLE");
        generator.writeStringField("engine", "InnoDB");
        generator.writeStringField("comment", "");
        generator.writeEndObject();
    }

    private void writePhysicalIndex(
            JsonGenerator generator,
            String table,
            String indexName,
            String column
    ) throws Exception {
        generator.writeStartObject();
        generator.writeStringField("catalog", "shop");
        generator.writeNullField("schema");
        generator.writeStringField("tableName", table);
        generator.writeStringField("indexName", indexName);
        generator.writeBooleanField("unique", true);
        generator.writeBooleanField("primary", true);
        generator.writeStringField("indexType", "BTREE");
        generator.writeBooleanField("visible", true);
        generator.writeArrayFieldStart("columns");
        generator.writeString(column);
        generator.writeEndArray();
        generator.writeArrayFieldStart("expressions");
        generator.writeEndArray();
        generator.writeArrayFieldStart("subParts");
        generator.writeEndArray();
        generator.writeArrayFieldStart("seqInIndex");
        generator.writeNumber(1);
        generator.writeEndArray();
        writeIndexMembers(generator, column);
        generator.writeEndObject();
    }

    private void writeIndexMembers(JsonGenerator generator, String column) throws Exception {
        generator.writeArrayFieldStart("members");
        generator.writeStartObject();
        generator.writeNumberField("ordinal", 1);
        generator.writeStringField("kind", "FULL_COLUMN");
        generator.writeStringField("columnName", column);
        generator.writeNullField("expression");
        generator.writeNullField("prefixLength");
        generator.writeEndObject();
        generator.writeEndArray();
    }

    private void writeEndpoint(
            JsonGenerator generator,
            String field,
            String table,
            String column
    ) throws Exception {
        generator.writeObjectFieldStart(field);
        generator.writeStringField("table", table);
        generator.writeStringField("column", column);
        generator.writeEndObject();
    }

    private String sourceColumn(int index) {
        return "source_%05d".formatted(index + 1);
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
