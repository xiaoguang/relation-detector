package com.relationdetector.semantic.reader;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.contracts.metadata.MetadataColumnFact;
import com.relationdetector.contracts.metadata.MetadataConstraintFact;
import com.relationdetector.contracts.metadata.MetadataIndexFact;
import com.relationdetector.contracts.metadata.MetadataIndexMemberFact;
import com.relationdetector.contracts.metadata.MetadataTableFact;
import com.relationdetector.contracts.spi.ScanScope;
import com.relationdetector.semantic.StableSemanticId;

final class SemanticMetadataInventoryEnvelopeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void streamingFingerprintMatchesCanonicalTreeContract() {
        ScanMetadataInventory inventory = inventory();
        ObjectNode oldFingerprintInput = JSON.createObjectNode();
        ObjectNode scope = oldFingerprintInput.putObject("scope");
        scope.put("catalog", "shop");
        scope.putNull("schema");
        scope.putArray("includeTables").add("orders");
        scope.putArray("excludeTables");
        oldFingerprintInput.set("tables", JSON.valueToTree(inventory.tables()));
        oldFingerprintInput.set("columns", JSON.valueToTree(inventory.columns()));
        oldFingerprintInput.set("constraints", JSON.valueToTree(inventory.constraints()));
        oldFingerprintInput.set("indexes", JSON.valueToTree(inventory.indexes()));
        String expected = StableSemanticId.of(
                "semantic-metadata-inventory",
                StableSemanticId.canonicalJson(oldFingerprintInput));

        assertEquals(expected,
                SemanticMetadataInventoryEnvelope.from(inventory).path("fingerprint").asText());
    }

    private ScanMetadataInventory inventory() {
        return ScanMetadataInventory.complete(
                new ScanScope("shop", null, List.of("orders"), List.of()),
                List.of(new MetadataTableFact(
                        "shop", null, "orders", "BASE TABLE", "InnoDB", "订单")),
                List.of(new MetadataColumnFact(
                        "shop", null, "orders", "customer_id", "bigint", "bigint",
                        false, null, "", "", 1)),
                List.of(new MetadataConstraintFact(
                        "shop", null, "orders", "pk_orders", "PRIMARY_KEY",
                        List.of("customer_id"), null, null, null, List.of(), null, null)),
                List.of(new MetadataIndexFact(
                        "shop", null, "orders", "idx_orders_customer", true, true,
                        "BTREE", true,
                        List.of(MetadataIndexMemberFact.fullColumn(1, "customer_id")))));
    }
}
