package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;

final class SemanticExtractionBundleBuilderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void noFocusUsesGlobalEvidenceUpToConfiguredLimits() {
        List<JsonNode> relationships = new ArrayList<>();
        List<JsonNode> derivedRelationships = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            relationships.add(relationship("orders_" + i, "customer_id", "customers_" + i, "id"));
            derivedRelationships.add(relationship("order_items_" + i, "order_id", "customers_" + i, "id"));
        }
        ScanBundle bundle = new ScanBundle("mysql", "sample_data", "", List.of("logs"), List.of(),
                Map.of(), relationships, List.of(), derivedRelationships, List.of(), List.of(), List.of());

        ObjectNode evidenceBundle = new SemanticExtractionBundleBuilder().build(bundle, "", 50, 50, 50);

        assertEquals(40, evidenceBundle.path("relationships").size());
        assertEquals(40, evidenceBundle.path("derivedRelationships").size());
        assertTrue(evidenceBundle.path("tables").toString().contains("orders_39"));
        assertTrue(evidenceBundle.path("tables").toString().contains("customers_39"));
    }

    private JsonNode relationship(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        ObjectNode relationship = JSON.createObjectNode();
        relationship.set("source", endpoint(sourceTable, sourceColumn));
        relationship.set("target", endpoint(targetTable, targetColumn));
        relationship.put("relationType", "FK_LIKE");
        relationship.put("confidence", 0.8);
        relationship.putArray("evidence").addObject()
                .put("type", "SQL_LOG_JOIN")
                .put("source", "query.sql")
                .put("detail", sourceTable + "." + sourceColumn + " = " + targetTable + "." + targetColumn);
        relationship.putArray("rawEvidence");
        return relationship;
    }

    private ObjectNode endpoint(String table, String column) {
        ObjectNode endpoint = JSON.createObjectNode();
        endpoint.put("table", table);
        endpoint.put("column", column);
        return endpoint;
    }
}
