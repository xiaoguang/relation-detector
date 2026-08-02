package com.relationdetector.semantic.extraction.shard;

import com.relationdetector.semantic.extraction.shard.SemanticEvidenceBundleSliceReader;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionDocumentNormalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SemanticEvidenceBundleSliceReaderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void readsOnlyTheOwnedReferenceClosureFromAPathBackedBundle() throws Exception {
        Path bundle = tempDir.resolve("evidence-bundle.json");
        Files.writeString(bundle, """
                {
                  "database": {"type": "MYSQL", "catalog": "shop", "schema": ""},
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "LIVE_METADATA",
                    "scope": {"catalog": "shop", "schema": "", "includeTables": [], "excludeTables": []},
                    "counts": {"tables": 3, "columns": 2, "constraints": 0, "indexes": 0},
                    "fingerprint": "inventory-test"
                  },
                  "inputFiles": ["input.sql"],
                  "sources": ["input.sql"],
                  "tables": ["shop.orders", "shop.customers", "shop.unused"],
                  "evidence": [
                    {"id": "ev-owned", "type": "SQL_PREDICATE"},
                    {"id": "ev-unused", "type": "SQL_PREDICATE"}
                  ],
                  "metadataTables": [
                    {"id": "meta-table-orders", "table": "shop.orders", "evidenceRefs": ["meta-ev-orders"]},
                    {"id": "meta-table-unused", "table": "shop.unused", "evidenceRefs": ["meta-ev-unused"]}
                  ],
                  "metadataColumns": [
                    {"id": "meta-column-order-id", "column": "shop.orders.id",
                     "evidenceRefs": ["meta-ev-order-id"]},
                    {"id": "meta-column-unused-id", "column": "shop.unused.id",
                     "evidenceRefs": ["meta-ev-unused-id"]}
                  ],
                  "metadataConstraints": [],
                  "metadataIndexes": [],
                  "relationships": [
                    {"id": "rel-owned", "source": "shop.orders.customer_id",
                     "target": "shop.customers.id", "evidenceRefs": ["ev-owned"]},
                    {"id": "rel-unused", "source": "shop.unused.id",
                     "target": "shop.unused.parent_id", "evidenceRefs": ["ev-unused"]}
                  ],
                  "lineage": [],
                  "derivedRelationships": [],
                  "derivedLineage": [],
                  "namingEvidence": [],
                  "diagnostics": [],
                  "eventCandidates": [],
                  "tripletCandidates": [],
                  "reviewItemCandidates": [],
                  "instructions": {"allOutputsMustUseEvidenceRefs": true},
                  "shardContext": {
                    "shardId": "shard-0001",
                    "ownerKey": "shop.orders",
                    "outputOwnedReferencesOnly": true,
                    "ownedFactRefs": ["rel-owned"],
                    "ownedCandidateRefs": [],
                    "overlapRefs": []
                  }
                }
                """);
        ObjectNode raw = (ObjectNode) JSON.readTree("""
                {
                  "entities": [
                    {"name": "订单", "physicalName": "shop.orders", "type": "BUSINESS_ENTITY",
                     "ownedGroundingRefs": ["rel-owned"], "evidenceRefs": ["ev-owned"]}
                  ],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        ObjectNode slice = new SemanticEvidenceBundleSliceReader().read(bundle, raw, 20_000);

        assertEquals(1, slice.path("relationships").size());
        assertEquals("rel-owned", slice.path("relationships").get(0).path("id").asText());
        assertEquals(1, slice.path("evidence").size());
        assertEquals("ev-owned", slice.path("evidence").get(0).path("id").asText());
        assertTrue(contains(slice.path("tables"), "shop.orders"));
        assertTrue(contains(slice.path("tables"), "shop.customers"));
        assertFalse(contains(slice.path("tables"), "shop.unused"));
        assertEquals("shard-0001", slice.path("shardContext").path("shardId").asText());
    }

    @Test
    void metadataOnlyPhysicalReferencesRemainValidAfterSlicing() throws Exception {
        Path bundle = tempDir.resolve("metadata-only-bundle.json");
        Files.writeString(bundle, """
                {
                  "database": {"type": "MYSQL", "catalog": "shop", "schema": ""},
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "LIVE_METADATA",
                    "scope": {"catalog": "shop", "schema": "", "includeTables": [], "excludeTables": []},
                    "counts": {"tables": 1, "columns": 1, "constraints": 0, "indexes": 0},
                    "fingerprint": "inventory-test"
                  },
                  "inputFiles": [], "sources": [],
                  "tables": ["shop.orders"],
                  "evidence": [{"id": "meta-ev-orders", "type": "METADATA"}],
                  "metadataTables": [
                    {"id": "meta-table-orders", "table": "shop.orders",
                     "evidenceRefs": ["meta-ev-orders"]}
                  ],
                  "metadataColumns": [
                    {"id": "meta-column-total", "column": "shop.orders.total",
                     "evidenceRefs": ["meta-ev-orders"]}
                  ],
                  "metadataConstraints": [], "metadataIndexes": [],
                  "relationships": [], "lineage": [], "derivedRelationships": [],
                  "derivedLineage": [], "namingEvidence": [], "diagnostics": [],
                  "eventCandidates": [], "tripletCandidates": [], "reviewItemCandidates": [],
                  "instructions": {"allOutputsMustUseEvidenceRefs": true},
                  "shardContext": {
                    "shardId": "shard-0001",
                    "ownerKey": "shop.orders",
                    "outputOwnedReferencesOnly": true,
                    "ownedFactRefs": ["meta-table-orders", "meta-column-total"],
                    "ownedCandidateRefs": [],
                    "overlapRefs": []
                  }
                }
                """);
        ObjectNode raw = (ObjectNode) JSON.readTree("""
                {
                  "entities": [
                    {"name": "订单", "physicalName": "shop.orders", "type": "BUSINESS_ENTITY",
                     "ownedGroundingRefs": ["meta-table-orders"], "evidenceRefs": ["meta-ev-orders"]}
                  ],
                  "events": [], "relations": [], "lineage": [],
                  "metrics": [
                    {"name": "订单金额", "type": "MEASURE", "physicalField": "shop.orders.total",
                     "ownedGroundingRefs": ["meta-column-total"], "evidenceRefs": ["meta-ev-orders"]}
                  ],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        ObjectNode slice = new SemanticEvidenceBundleSliceReader().read(bundle, raw, 20_000);
        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalizeOwnedShard(raw, slice);

        assertEquals("shop.orders", slice.path("metadataTables").get(0).path("table").asText());
        assertEquals("shop.orders.total", slice.path("metadataColumns").get(0).path("column").asText());
        assertEquals(1, normalized.path("metrics").size());
    }

    @Test
    void rejectsEvidenceBundleWithoutCompleteInventoryDescriptor() throws Exception {
        Path bundle = tempDir.resolve("partial-inventory-bundle.json");
        Files.writeString(bundle, """
                {
                  "database": {"type": "MYSQL", "catalog": "shop", "schema": ""},
                  "metadataInventory": {
                    "status": "PARTIAL",
                    "scope": {"catalog": "shop", "schema": "", "includeTables": [], "excludeTables": []},
                    "counts": {"tables": 0, "columns": 0, "constraints": 0, "indexes": 0},
                    "fingerprint": "inventory-test"
                  },
                  "inputFiles": [], "sources": [], "tables": [], "evidence": [],
                  "metadataTables": [], "metadataColumns": [], "metadataConstraints": [], "metadataIndexes": [],
                  "relationships": [], "lineage": [], "derivedRelationships": [], "derivedLineage": [],
                  "namingEvidence": [], "diagnostics": [], "eventCandidates": [], "tripletCandidates": [],
                  "reviewItemCandidates": [], "instructions": {"allOutputsMustUseEvidenceRefs": true},
                  "shardContext": {
                    "shardId": "shard-0001", "ownerKey": "shop",
                    "outputOwnedReferencesOnly": true,
                    "ownedFactRefs": [], "ownedCandidateRefs": [], "overlapRefs": []
                  }
                }
                """);
        ObjectNode raw = (ObjectNode) JSON.readTree("""
                {
                  "entities": [], "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticEvidenceBundleSliceReader().read(bundle, raw, 20_000));
    }

    @Test
    void rejectsSelectedEvidenceClosureBeyondTheConfiguredInputBudget() throws Exception {
        Path bundle = tempDir.resolve("oversized-evidence-bundle.json");
        Files.writeString(bundle, """
                {
                  "database": {"type": "MYSQL", "catalog": "shop", "schema": ""},
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "scope": {"catalog": "shop", "schema": "", "includeTables": [], "excludeTables": []},
                    "counts": {"tables": 1, "columns": 0, "constraints": 0, "indexes": 0},
                    "fingerprint": "inventory-test"
                  },
                  "inputFiles": [], "sources": [], "tables": ["shop.orders"],
                  "evidence": [
                    {"id": "ev-owned", "type": "SQL_PREDICATE",
                     "detail": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"}
                  ],
                  "metadataTables": [], "metadataColumns": [], "metadataConstraints": [],
                  "metadataIndexes": [], "relationships": [
                    {"id": "rel-owned", "source": "shop.orders.customer_id",
                     "target": "shop.orders.id", "evidenceRefs": ["ev-owned"]}
                  ],
                  "lineage": [], "derivedRelationships": [], "derivedLineage": [],
                  "namingEvidence": [], "diagnostics": [], "eventCandidates": [],
                  "tripletCandidates": [], "reviewItemCandidates": [],
                  "instructions": {"allOutputsMustUseEvidenceRefs": true},
                  "shardContext": {
                    "shardId": "shard-0001", "ownerKey": "shop.orders",
                    "outputOwnedReferencesOnly": true,
                    "ownedFactRefs": ["rel-owned"], "ownedCandidateRefs": [], "overlapRefs": []
                  }
                }
                """);
        ObjectNode raw = (ObjectNode) JSON.readTree("""
                {
                  "entities": [
                    {"name": "订单", "physicalName": "shop.orders", "type": "BUSINESS_ENTITY",
                     "ownedGroundingRefs": ["rel-owned"], "evidenceRefs": ["ev-owned"]}
                  ],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticEvidenceBundleSliceReader().read(bundle, raw, 80));
    }

    @Test
    void rejectsEachOversizedEnvelopeFieldWithinTheSharedSliceBudget() throws Exception {
        ObjectNode raw = emptyRawDocument();
        for (String field : java.util.List.of(
                "inputFiles", "sources", "instructions", "shardContext")) {
            Path bundle = writeBundleWithOversizedEnvelope(field);

            assertThrows(
                    SemanticExtractionValidationException.class,
                    () -> new SemanticEvidenceBundleSliceReader().read(bundle, raw, 256),
                    field);
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void rejectsHugeEnvelopeWithinLowHeapBeforeTreeMaterialization() throws Exception {
        Path bundle = tempDir.resolve("huge-envelope-bundle.json");
        Path raw = tempDir.resolve("empty-raw.json");
        Path stdout = tempDir.resolve("envelope-child.stdout");
        Path stderr = tempDir.resolve("envelope-child.stderr");
        writeBundleWithHugeInputFiles(bundle, 64L * 1024L * 1024L);
        JSON.writeValue(raw.toFile(), emptyRawDocument());

        Process process = new ProcessBuilder(
                javaExecutable(),
                "-Xmx32m",
                "-cp", testClasspath(),
                LowHeapOversizedEnvelopeProbe.class.getName(),
                bundle.toString(),
                raw.toString())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        assertTrue(process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(0, process.exitValue(), () -> {
            try {
                return Files.readString(stderr);
            } catch (Exception failure) {
                return "could not read child stderr";
            }
        });
        assertFalse(Files.readString(stderr).contains("OutOfMemoryError"));
    }

    private ObjectNode emptyRawDocument() throws Exception {
        return (ObjectNode) JSON.readTree("""
                {
                  "entities": [], "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
    }

    private Path writeBundleWithOversizedEnvelope(String field) throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("database")
                .put("type", "MYSQL")
                .put("catalog", "shop")
                .put("schema", "");
        ObjectNode inventory = root.putObject("metadataInventory");
        inventory.put("status", "COMPLETE");
        inventory.put("basis", "LIVE_METADATA");
        inventory.putObject("scope")
                .put("catalog", "shop")
                .put("schema", "")
                .putArray("includeTables");
        inventory.withObject("/scope").putArray("excludeTables");
        inventory.putObject("counts")
                .put("tables", 0)
                .put("columns", 0)
                .put("constraints", 0)
                .put("indexes", 0);
        inventory.put("fingerprint", "inventory-test");
        root.putArray("inputFiles");
        root.putArray("sources");
        root.putObject("instructions").put("allOutputsMustUseEvidenceRefs", true);
        ObjectNode context = root.putObject("shardContext");
        context.put("shardId", "shard-0001");
        context.put("ownerKey", "global");
        context.put("outputOwnedReferencesOnly", true);
        context.putArray("ownedFactRefs");
        context.putArray("ownedCandidateRefs");
        context.putArray("overlapRefs");
        for (String section : java.util.List.of(
                "tables", "evidence", "metadataTables", "metadataColumns",
                "metadataConstraints", "metadataIndexes", "relationships", "lineage",
                "derivedRelationships", "derivedLineage", "namingEvidence", "diagnostics",
                "eventCandidates", "tripletCandidates", "reviewItemCandidates")) {
            root.putArray(section);
        }
        if ("instructions".equals(field)) {
            root.withObject("/instructions").put("large", "x".repeat(100_000));
        } else if ("shardContext".equals(field)) {
            root.withObject("/shardContext").put("large", "x".repeat(100_000));
        } else {
            root.withArray("/" + field).add("x".repeat(100_000));
        }
        Path bundle = tempDir.resolve("oversized-" + field + ".json");
        JSON.writeValue(bundle.toFile(), root);
        return bundle;
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.MINUTES)
    void skipsHugeUnselectedEvidenceWithoutMaterializingIt() throws Exception {
        Path bundle = tempDir.resolve("huge-unselected-evidence-bundle.json");
        Path raw = tempDir.resolve("raw.json");
        writeBundleWithHugeUnselectedEvidence(bundle, 16L * 1024L * 1024L);
        Files.writeString(raw, """
                {
                  "entities": [
                    {"name": "订单", "physicalName": "shop.orders", "type": "BUSINESS_ENTITY",
                     "ownedGroundingRefs": ["rel-owned"], "evidenceRefs": ["ev-owned"]}
                  ],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        Path stdout = tempDir.resolve("slice-child.stdout");
        Path stderr = tempDir.resolve("slice-child.stderr");

        Process process = new ProcessBuilder(
                javaExecutable(),
                "-Xmx32m",
                "-cp", testClasspath(),
                LowHeapSliceProbe.class.getName(),
                bundle.toString(),
                raw.toString())
                .redirectOutput(stdout.toFile())
                .redirectError(stderr.toFile())
                .start();

        assertTrue(process.waitFor(Duration.ofMinutes(3).toMillis(), TimeUnit.MILLISECONDS));
        assertEquals(0, process.exitValue(), () -> {
            try {
                return Files.readString(stderr);
            } catch (Exception failure) {
                return "could not read child stderr";
            }
        });
        assertFalse(Files.readString(stderr).contains("OutOfMemoryError"));
    }

    private void writeBundleWithHugeUnselectedEvidence(Path bundle, long detailBytes) throws Exception {
        String prefix = """
                {
                  "database": {"type": "MYSQL", "catalog": "shop", "schema": ""},
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "LIVE_METADATA",
                    "scope": {"catalog": "shop", "schema": "", "includeTables": [], "excludeTables": []},
                    "counts": {"tables": 1, "columns": 0, "constraints": 0, "indexes": 0},
                    "fingerprint": "inventory-test"
                  },
                  "inputFiles": [], "sources": [], "tables": ["shop.orders"],
                  "evidence": [
                    {"id": "ev-unused", "type": "SQL_PREDICATE", "detail":
                """;
        String suffix = """
                },
                    {"id": "ev-owned", "type": "SQL_PREDICATE"}
                  ],
                  "metadataTables": [], "metadataColumns": [], "metadataConstraints": [],
                  "metadataIndexes": [], "relationships": [
                    {"id": "rel-owned", "source": "shop.orders.customer_id",
                     "target": "shop.orders.id", "evidenceRefs": ["ev-owned"]}
                  ],
                  "lineage": [], "derivedRelationships": [], "derivedLineage": [],
                  "namingEvidence": [], "diagnostics": [], "eventCandidates": [],
                  "tripletCandidates": [], "reviewItemCandidates": [],
                  "instructions": {"allOutputsMustUseEvidenceRefs": true},
                  "shardContext": {
                    "shardId": "shard-0001", "ownerKey": "shop.orders",
                    "outputOwnedReferencesOnly": true,
                    "ownedFactRefs": ["rel-owned"], "ownedCandidateRefs": [], "overlapRefs": []
                  }
                }
                """;
        byte[] chunk = new byte[64 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        try (OutputStream output = Files.newOutputStream(bundle)) {
            output.write(prefix.getBytes(StandardCharsets.UTF_8));
            output.write('"');
            long written = 0;
            while (written < detailBytes) {
                int length = (int) Math.min(chunk.length, detailBytes - written);
                output.write(chunk, 0, length);
                written += length;
            }
            output.write('"');
            output.write(suffix.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void writeBundleWithHugeInputFiles(Path bundle, long valueBytes) throws Exception {
        String prefix = """
                {
                  "database": {"type": "MYSQL", "catalog": "shop", "schema": ""},
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "LIVE_METADATA",
                    "scope": {"catalog": "shop", "schema": "", "includeTables": [], "excludeTables": []},
                    "counts": {"tables": 0, "columns": 0, "constraints": 0, "indexes": 0},
                    "fingerprint": "inventory-test"
                  },
                  "inputFiles": [
                """;
        String suffix = """
                ],
                  "sources": [], "tables": [], "evidence": [],
                  "metadataTables": [], "metadataColumns": [], "metadataConstraints": [],
                  "metadataIndexes": [], "relationships": [], "lineage": [],
                  "derivedRelationships": [], "derivedLineage": [], "namingEvidence": [],
                  "diagnostics": [], "eventCandidates": [], "tripletCandidates": [],
                  "reviewItemCandidates": [],
                  "instructions": {"allOutputsMustUseEvidenceRefs": true},
                  "shardContext": {
                    "shardId": "shard-0001", "ownerKey": "global",
                    "outputOwnedReferencesOnly": true,
                    "ownedFactRefs": [], "ownedCandidateRefs": [], "overlapRefs": []
                  }
                }
                """;
        byte[] chunk = new byte[64 * 1024];
        java.util.Arrays.fill(chunk, (byte) 'x');
        try (OutputStream output = Files.newOutputStream(bundle)) {
            output.write(prefix.getBytes(StandardCharsets.UTF_8));
            output.write('"');
            long written = 0;
            while (written < valueBytes) {
                int length = (int) Math.min(chunk.length, valueBytes - written);
                output.write(chunk, 0, length);
                written += length;
            }
            output.write('"');
            output.write(suffix.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win")
                        ? "java.exe"
                        : "java").toString();
    }

    private String testClasspath() {
        String surefire = System.getProperty("surefire.test.class.path");
        return surefire == null || surefire.isBlank()
                ? System.getProperty("java.class.path")
                : surefire;
    }

    public static final class LowHeapSliceProbe {
        private LowHeapSliceProbe() {
        }

        public static void main(String[] args) throws Exception {
            ObjectNode raw = (ObjectNode) JSON.readTree(Path.of(args[1]).toFile());
            ObjectNode slice = new SemanticEvidenceBundleSliceReader().read(
                    Path.of(args[0]), raw, 20_000);
            if (slice.path("evidence").size() != 1
                    || !"ev-owned".equals(slice.path("evidence").get(0).path("id").asText())) {
                throw new IllegalStateException("unexpected evidence slice");
            }
        }
    }

    public static final class LowHeapOversizedEnvelopeProbe {
        private LowHeapOversizedEnvelopeProbe() {
        }

        public static void main(String[] args) throws Exception {
            ObjectNode raw = (ObjectNode) JSON.readTree(Path.of(args[1]).toFile());
            try {
                new SemanticEvidenceBundleSliceReader().read(
                        Path.of(args[0]), raw, 256);
                throw new IllegalStateException("oversized envelope was accepted");
            } catch (SemanticExtractionValidationException expected) {
                // Expected budget rejection before the complete field is materialized.
            }
        }
    }

    private boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.asText())) {
                return true;
            }
        }
        return false;
    }
}
