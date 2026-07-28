package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticEvidenceStoreTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void streamsCompleteDeduplicatedBundleAndBoundedComponents() throws Exception {
        Path input = tempDir.resolve("scan.json");
        JSON.writeValue(input.toFile(), scanResult());
        Path inputWork = tempDir.resolve("input-work");
        Path evidenceWork = tempDir.resolve("evidence-work");
        Path bundlePath = tempDir.resolve("full-evidence-bundle.json");

        List<SemanticEvidenceStore.ComponentBundle> components = new ArrayList<>();
        try (SemanticInputStore store = new ScanResultReader().open(List.of(input), inputWork);
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(store, evidenceWork, 1024 * 1024)) {
            assertEquals(2, evidence.count(SemanticEvidenceStore.Section.METADATA_TABLES));
            assertEquals(3, evidence.count(SemanticEvidenceStore.Section.METADATA_COLUMNS));
            assertEquals(1, evidence.count(SemanticEvidenceStore.Section.RELATIONSHIPS));
            evidence.forEachComponent(components::add);
            assertEquals(1, components.size());
            assertTrue(Files.size(components.get(0).path()) > 0);
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
    void everyByteSplitMetadataChunkRetainsItsPhysicalTableOwner() throws Exception {
        Path input = tempDir.resolve("split-scan.json");
        JSON.writeValue(input.toFile(), scanResult());
        Path inputWork = tempDir.resolve("split-input-work");
        Path evidenceWork = tempDir.resolve("split-evidence-work");
        List<SemanticEvidenceStore.ComponentBundle> components = new ArrayList<>();

        try (SemanticInputStore store = new ScanResultReader().open(List.of(input), inputWork);
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(store, evidenceWork, 1)) {
            evidence.forEachComponent(components::add);
            assertTrue(components.size() > 1);
            for (SemanticEvidenceStore.ComponentBundle component : components) {
                JsonNode bundle = JSON.readTree(component.path().toFile());
                boolean ownsMetadata = !bundle.path("metadataTables").isEmpty()
                        || !bundle.path("metadataColumns").isEmpty()
                        || !bundle.path("metadataConstraints").isEmpty()
                        || !bundle.path("metadataIndexes").isEmpty();
                if (ownsMetadata) {
                    assertFalse(bundle.path("tables").isEmpty(),
                            "metadata chunk lost its physical table owner: " + component.id());
                }
            }
        }
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
