package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;
import com.relationdetector.semantic.extract.SemanticShardingException;

final class SemanticEvidenceStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void streamsCompleteDeduplicatedBundleWithoutTransportArtifacts() throws Exception {
        Path input = tempDir.resolve("scan.json");
        JSON.writeValue(input.toFile(), scanResult());
        Path inputWork = tempDir.resolve("input-work");
        Path evidenceWork = tempDir.resolve("evidence-work");
        Path bundlePath = tempDir.resolve("full-evidence-bundle.json");

        try (SemanticInputStore store = new ScanResultReader().open(List.of(input), inputWork);
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(store, evidenceWork, 1024 * 1024)) {
            assertEquals(2, evidence.count(SemanticEvidenceStore.Section.METADATA_TABLES));
            assertEquals(3, evidence.count(SemanticEvidenceStore.Section.METADATA_COLUMNS));
            assertEquals(1, evidence.count(SemanticEvidenceStore.Section.RELATIONSHIPS));
            assertFalse(Files.exists(evidenceWork.resolve("components")));
            assertEquals(64, evidence.writeBundleAndHash(bundlePath).length());
        }

        JsonNode bundle = JSON.readTree(bundlePath.toFile());
        assertEquals(2, bundle.path("tables").size());
        assertEquals(2, bundle.path("metadataTables").size());
        assertEquals(3, bundle.path("metadataColumns").size());
        assertEquals(1, bundle.path("relationships").size());
        assertFalse(Files.exists(inputWork));
        assertFalse(Files.exists(evidenceWork));
    }

    @Test
    void rawBufferSizeDoesNotSplitRoutineEventOrChangeGlobalBundle() throws Exception {
        ObjectNode root = (ObjectNode) scanResult();
        addRoutineLineage(root, "lineage:order-id", "shop.orders", "customer_id", "id", "INSERT_SELECT");
        addRoutineLineage(root, "lineage:customer-name", "shop.customers", "id", "name", "UPDATE_SET");
        Path input = tempDir.resolve("split-scan.json");
        JSON.writeValue(input.toFile(), root);

        JsonNode large = buildBundle(input, "large", 1024 * 1024);
        JsonNode tiny = buildBundle(input, "tiny", 1);

        assertEquals(
                StableSemanticId.canonicalJson(large),
                StableSemanticId.canonicalJson(tiny));
        assertEquals(1, tiny.path("eventCandidates").size());
        JsonNode event = tiny.path("eventCandidates").get(0);
        assertEquals(2, event.path("lineageRefs").size());
        assertEquals(2, event.path("operationKinds").size());
    }

    @Test
    void rawBufferSizeDoesNotChangeEvidenceGraphOrKnowledgeGraph() throws Exception {
        ObjectNode root = (ObjectNode) scanResult();
        addRoutineLineage(root, "lineage:order-id", "shop.orders", "customer_id", "id", "INSERT_SELECT");
        addRoutineLineage(root, "lineage:customer-name", "shop.customers", "id", "name", "UPDATE_SET");
        Path input = tempDir.resolve("graph-scan.json");
        JSON.writeValue(input.toFile(), root);

        List<JsonNode> large = buildArtifacts(input, "large-graph", 1024 * 1024);
        List<JsonNode> tiny = buildArtifacts(input, "tiny-graph", 1);

        assertEquals(
                StableSemanticId.canonicalJson(large.get(0)),
                StableSemanticId.canonicalJson(tiny.get(0)));
        assertEquals(
                StableSemanticId.canonicalJson(withoutBuildTimestamp(large.get(1))),
                StableSemanticId.canonicalJson(withoutBuildTimestamp(tiny.get(1))));
    }

    @Test
    void digestOnlyRendersTheExactFullArtifactsWithoutPersistingLargeJson() throws Exception {
        Path input = tempDir.resolve("digest-scan.json");
        JSON.writeValue(input.toFile(), scanResult());
        Path full = tempDir.resolve("full-artifacts");
        Path digestOnly = tempDir.resolve("digest-artifacts");
        Clock clock = Clock.fixed(Instant.parse("2026-07-31T12:00:00Z"), ZoneOffset.UTC);
        SemanticKgArtifactReport fullReport;

        try (SemanticInputStore store = new ScanResultReader().open(
                     List.of(input), tempDir.resolve("digest-input-work"));
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(
                     store, tempDir.resolve("digest-evidence-work"), 1024 * 1024)) {
            fullReport = new SemanticDiskBackedArtifactWriter(clock)
                    .writeArtifacts(evidence, full, SemanticKgArtifactMode.FULL);
            SemanticKgArtifactReport digestReport = new SemanticDiskBackedArtifactWriter(clock)
                    .writeArtifacts(evidence, digestOnly, SemanticKgArtifactMode.DIGEST_ONLY);
            assertEquals(fullReport.artifacts(), digestReport.artifacts());
            assertEquals(fullReport.summary(), digestReport.summary());
        }

        for (String file : List.of(
                "semantic-kg.json", "semantic-evidence-graph.json", "semantic-build-run.json")) {
            assertTrue(Files.isRegularFile(full.resolve(file)));
            assertFalse(Files.exists(digestOnly.resolve(file)));
        }
        JsonNode report = JSON.readTree(digestOnly.resolve("semantic-kg-digests.json").toFile());
        assertEquals("PASS", report.path("validation").path("referenceClosure").asText());
        assertEquals(3, report.path("artifacts").size());

        JsonNode kg = JSON.readTree(full.resolve("semantic-kg.json").toFile());
        assertEquals(2, kg.path("artifactSchemaVersion").asInt());
        assertFalse(kg.has("evidenceRefs"));
        assertFalse(kg.has("diagnostics"));
        assertEquals("semantic-evidence-graph.json", kg.path("evidenceGraph").path("path").asText());
        SemanticKgArtifactReport.ArtifactDigest evidenceDigest = fullReport.artifacts().stream()
                .filter(artifact -> "semantic-evidence-graph.json".equals(artifact.path()))
                .findFirst()
                .orElseThrow();
        assertEquals(
                evidenceDigest.sha256(),
                kg.path("evidenceGraph").path("sha256").asText());
        assertEquals(fullReport.summary().evidenceRefCount(),
                kg.path("evidenceGraph").path("evidenceRefCount").asLong());
        assertEquals(fullReport.summary().diagnosticCount(),
                kg.path("evidenceGraph").path("diagnosticCount").asLong());
    }

    @Test
    void rejectsOneEventAcrossManyTransportWindowsBeforeMemberMaterialization()
            throws Exception {
        ObjectNode root = (ObjectNode) scanResult();
        for (int index = 0; index < 200; index++) {
            addRoutineLineage(
                    root,
                    "lineage:budget:%04d".formatted(index),
                    "shop.orders",
                    "customer_id",
                    "id_%04d".formatted(index),
                    "INSERT_SELECT");
        }
        Path input = tempDir.resolve("event-budget-scan.json");
        JSON.writeValue(input.toFile(), root);
        Path evidenceWork = tempDir.resolve("event-budget-evidence-work");

        try (SemanticInputStore store = new ScanResultReader().open(
                List.of(input), tempDir.resolve("event-budget-input-work"))) {
            assertThrows(
                    SemanticShardingException.class,
                    () -> {
                        try (SemanticEvidenceStore ignored = new SemanticEvidenceStore(
                                store,
                                evidenceWork,
                                1,
                                2_000)) {
                            // Construction performs the full disk-backed build.
                        }
                    });
        }
        assertFalse(Files.exists(evidenceWork));
    }

    private JsonNode buildBundle(Path input, String prefix, long bufferBytes) throws Exception {
        Path bundle = tempDir.resolve(prefix + "-bundle.json");
        try (SemanticInputStore store = new ScanResultReader().open(
                     List.of(input), tempDir.resolve(prefix + "-input-work"));
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(
                     store, tempDir.resolve(prefix + "-evidence-work"), bufferBytes)) {
            evidence.writeBundle(bundle);
        }
        return JSON.readTree(bundle.toFile());
    }

    private List<JsonNode> buildArtifacts(Path input, String prefix, long bufferBytes) throws Exception {
        Path output = tempDir.resolve(prefix + "-artifacts");
        try (SemanticInputStore store = new ScanResultReader().open(
                     List.of(input), tempDir.resolve(prefix + "-input-work"));
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(
                     store, tempDir.resolve(prefix + "-evidence-work"), bufferBytes)) {
            new SemanticDiskBackedArtifactWriter().writeArtifacts(evidence, output);
        }
        return List.of(
                JSON.readTree(output.resolve("semantic-evidence-graph.json").toFile()),
                JSON.readTree(output.resolve("semantic-kg.json").toFile()));
    }

    private JsonNode withoutBuildTimestamp(JsonNode value) {
        ObjectNode copy = value.deepCopy();
        ((ObjectNode) copy.path("buildRun")).remove("builtAt");
        return copy;
    }

    private void addRoutineLineage(
            ObjectNode root,
            String id,
            String sourceTable,
            String sourceColumn,
            String targetColumn,
            String mappingKind
    ) {
        ObjectNode lineage = root.withArray("dataLineages").addObject();
        lineage.put("id", id);
        lineage.putArray("sources").addObject()
                .put("table", sourceTable)
                .put("column", sourceColumn);
        lineage.putObject("target")
                .put("table", "shop.audit_log")
                .put("column", targetColumn);
        lineage.put("flowKind", "VALUE");
        lineage.put("transformType", "DIRECT");
        lineage.put("confidence", 0.82);
        ObjectNode evidence = lineage.putArray("evidence").addObject();
        evidence.put("type", "DATA_LINEAGE");
        evidence.put("transformType", "DIRECT");
        evidence.put("sourceType", "PLAIN_SQL");
        evidence.put("score", 0.82);
        evidence.put("source", "procedures/audit.sql");
        evidence.put("detail", "typed routine write");
        evidence.putObject("attributes")
                .put("sourceObjectType", "PROCEDURE")
                .put("sourceObjectName", "sp_write_audit")
                .put("sourceObjectIdentity", "shop.sp_write_audit(bigint)")
                .put("sourceStatementId", "routine:sp_write_audit")
                .put("mappingKind", mappingKind);
        lineage.putArray("rawEvidence");
        lineage.putArray("warnings");
        lineage.putObject("attributes").put("mappingKind", mappingKind);
        ObjectNode summary = (ObjectNode) root.path("summary");
        int count = root.path("dataLineages").size();
        summary.put("directDataLineageCount", count);
        summary.put("totalDataLineageCount", count);
    }

    private JsonNode scanResult() throws Exception {
        return JSON.readTree("""
                {
                  "database": {"type": "mysql", "catalog": "shop", "schema": ""},
                  "generatedAt": "2026-07-28T12:00:00Z",
                  "summary": {
                    "directRelationshipCount": 1,
                    "derivedRelationshipCount": 0,
                    "totalRelationshipCount": 1,
                    "directDataLineageCount": 0,
                    "derivedDataLineageCount": 0,
                    "totalDataLineageCount": 0,
                    "directNamingEvidenceCount": 0,
                    "derivedNamingEvidenceCount": 0,
                    "totalNamingEvidenceCount": 0,
                    "warningCount": 0,
                    "sources": ["metadata"]
                  },
                  "metadataInventory": {
                    "status": "COMPLETE",
                    "basis": "LIVE_METADATA",
                    "scope": {
                      "catalog": "shop",
                      "schema": "",
                      "includeTables": [],
                      "excludeTables": []
                    },
                    "counts": {"tables": 2, "columns": 3, "constraints": 1, "indexes": 1},
                    "tables": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "tableType": "BASE TABLE", "engine": "InnoDB", "comment": ""},
                      {"catalog": "shop", "schema": null, "tableName": "customers",
                       "tableType": "BASE TABLE", "engine": "InnoDB", "comment": ""}
                    ],
                    "columns": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "columnName": "customer_id", "dataType": "bigint", "columnType": "bigint",
                       "nullable": false, "defaultValue": null, "extra": "",
                       "generationExpression": "", "ordinalPosition": 1},
                      {"catalog": "shop", "schema": null, "tableName": "customers",
                       "columnName": "id", "dataType": "bigint", "columnType": "bigint",
                       "nullable": false, "defaultValue": null, "extra": "",
                       "generationExpression": "", "ordinalPosition": 1},
                      {"catalog": "shop", "schema": null, "tableName": "customers",
                       "columnName": "name", "dataType": "varchar", "columnType": "varchar(255)",
                       "nullable": true, "defaultValue": null, "extra": "",
                       "generationExpression": "", "ordinalPosition": 2}
                    ],
                    "constraints": [
                      {"catalog": "shop", "schema": null, "tableName": "orders",
                       "constraintName": "fk_orders_customer", "constraintType": "FOREIGN_KEY",
                       "columns": ["customer_id"], "referencedCatalog": "shop",
                       "referencedSchema": null, "referencedTable": "customers",
                       "referencedColumns": ["id"], "updateRule": "NO ACTION", "deleteRule": "NO ACTION"}
                    ],
                    "indexes": [
                      {"catalog": "shop", "schema": null, "tableName": "customers",
                       "indexName": "pk_customers", "unique": true, "primary": true,
                       "indexType": "BTREE", "visible": true, "columns": ["id"],
                       "expressions": [], "subParts": [], "seqInIndex": [1]}
                    ]
                  },
                  "relationships": [{
                    "id": "relationship:orders-customers",
                    "source": {"table": "shop.orders", "column": "customer_id"},
                    "target": {"table": "shop.customers", "column": "id"},
                    "relationType": "FK_LIKE",
                    "relationSubType": "DECLARED_FK",
                    "confidence": 1.0,
                    "evidence": [{
                      "type": "METADATA_FOREIGN_KEY",
                      "sourceType": "METADATA",
                      "score": 1.0,
                      "source": "metadata",
                      "detail": "fk_orders_customer",
                      "attributes": {}
                    }],
                    "rawEvidence": [],
                    "warnings": [],
                    "attributes": {}
                  }],
                  "dataLineages": [],
                  "derivedRelationships": [],
                  "derivedDataLineages": [],
                  "namingEvidence": [],
                  "derivedNamingEvidence": [],
                  "warnings": []
                }
                """);
    }
}
