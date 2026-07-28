package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
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

        ObjectNode slice = new SemanticEvidenceBundleSliceReader().read(bundle, raw);

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

        ObjectNode slice = new SemanticEvidenceBundleSliceReader().read(bundle, raw);
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
                () -> new SemanticEvidenceBundleSliceReader().read(bundle, raw));
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
