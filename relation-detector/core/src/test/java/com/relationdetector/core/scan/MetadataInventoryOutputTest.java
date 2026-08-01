package com.relationdetector.core.scan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relationdetector.contracts.Enums.MetadataInventoryBasis;
import com.relationdetector.contracts.Enums.MetadataInventoryStatus;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataSnapshot;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.core.output.JsonResultWriter;

final class MetadataInventoryOutputTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesCompleteInventoryWithCanonicalScopeAndCounts() throws Exception {
        MetadataSnapshot snapshot = new MetadataSnapshot();
        snapshot.tableFacts().add(new MetadataTableFact(
                "shop", null, "orders", "TABLE", "InnoDB", "Orders"));
        snapshot.columnFacts().add(new MetadataColumnFact(
                "shop", null, "orders", "id", "bigint", "bigint", false,
                null, "", "", 1));
        snapshot.constraintFacts().add(new MetadataConstraintFact(
                "shop", null, "orders", "PRIMARY", "PRIMARY_KEY", List.of("id"),
                null, null, null, List.of(), null, null));
        snapshot.indexFacts().add(new MetadataIndexFact(
                "shop", null, "orders", "PRIMARY", true, true, "BTREE", true,
                List.of("id"), List.of(), List.of(), List.of(1)));
        MetadataInventory inventory = MetadataInventory.from(
                MetadataInventoryStatus.COMPLETE,
                MetadataInventoryBasis.LIVE_METADATA,
                new ScanScope("shop", null, List.of("orders"), List.of("audit_log")),
                snapshot);
        ScanResult result = new ScanResult("MYSQL", "shop", null, inventory);

        JsonNode root = JSON.readTree(new JsonResultWriter().write(result, true, true));
        JsonNode output = root.path("metadataInventory");

        assertEquals("COMPLETE", output.path("status").asText());
        assertEquals("LIVE_METADATA", output.path("basis").asText());
        assertEquals("shop", output.path("scope").path("catalog").asText());
        assertEquals("", output.path("scope").path("schema").asText());
        assertEquals(List.of("orders"),
                JSON.readerForListOf(String.class).readValue(output.path("scope").path("includeTables")));
        assertEquals(List.of("audit_log"),
                JSON.readerForListOf(String.class).readValue(output.path("scope").path("excludeTables")));
        assertEquals(1, output.path("counts").path("tables").asInt());
        assertEquals(1, output.path("counts").path("columns").asInt());
        assertEquals(1, output.path("counts").path("constraints").asInt());
        assertEquals(1, output.path("counts").path("indexes").asInt());
        assertEquals("orders", output.path("tables").get(0).path("tableName").asText());
        assertEquals("id", output.path("columns").get(0).path("columnName").asText());
        assertEquals("PRIMARY", output.path("constraints").get(0).path("constraintName").asText());
        assertEquals("PRIMARY", output.path("indexes").get(0).path("indexName").asText());
        assertEquals("FULL_COLUMN",
                output.path("indexes").get(0).path("members").get(0).path("kind").asText());
        assertEquals(1,
                output.path("indexes").get(0).path("members").get(0).path("ordinal").asInt());
    }

    @Test
    void warningSuppressionDoesNotHidePartialInventoryState() throws Exception {
        MetadataInventory inventory = MetadataInventory.from(
                MetadataInventoryStatus.PARTIAL,
                MetadataInventoryBasis.DDL_DECLARATIONS,
                new ScanScope("shop", null, List.of(), List.of()),
                new MetadataSnapshot());
        ScanResult result = new ScanResult("MYSQL", "shop", null, inventory);

        JsonNode root = JSON.readTree(new JsonResultWriter().write(result, true, false));

        assertEquals("PARTIAL", root.path("metadataInventory").path("status").asText());
        assertEquals("DDL_DECLARATIONS", root.path("metadataInventory").path("basis").asText());
        assertFalse(root.path("metadataInventory").isMissingNode());
        assertEquals(0, root.path("summary").path("warningCount").asInt());
    }

    @Test
    void legacyScanResultConstructorEmitsNotRequestedInventory() throws Exception {
        ScanResult result = new ScanResult("MYSQL", "shop", null);

        JsonNode inventory = JSON.readTree(new JsonResultWriter().write(result, true, true))
                .path("metadataInventory");

        assertEquals("NOT_REQUESTED", inventory.path("status").asText());
        assertEquals("NONE", inventory.path("basis").asText());
        assertEquals(0, inventory.path("counts").path("tables").asInt());
        assertEquals(0, inventory.path("counts").path("columns").asInt());
        assertEquals(0, inventory.path("counts").path("constraints").asInt());
        assertEquals(0, inventory.path("counts").path("indexes").asInt());
    }
}
