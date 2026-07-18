package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void zeroLimitsMeanUnlimitedCandidates() {
        List<JsonNode> relationships = new ArrayList<>();
        List<JsonNode> lineage = new ArrayList<>();
        List<JsonNode> namingEvidence = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            relationships.add(relationship("orders_" + i, "customer_id", "customers_" + i, "id"));
            lineage.add(lineage("payments_" + i, "amount", "sales_fact_" + i, "paid_amount",
                    "ROUTINE:erp.sp_rebuild_sales_fact_" + i));
            ObjectNode naming = JSON.createObjectNode();
            naming.put("id", "naming:orders_" + i + ".customer_id->customers_" + i + ".id:TABLE_ID");
            naming.set("source", endpoint("orders_" + i, "customer_id"));
            naming.set("target", endpoint("customers_" + i, "id"));
            naming.put("rule", "TABLE_ID");
            naming.put("directionHint", true);
            naming.putArray("evidence").addObject().put("type", "NAMING_MATCH").put("source", "ddl.sql");
            namingEvidence.add(naming);
        }
        ScanBundle bundle = new ScanBundle("mysql", "sample_data", "", List.of("object-files"), List.of(),
                Map.of(), relationships, lineage, List.of(), List.of(), namingEvidence, List.of());

        ObjectNode evidenceBundle = new SemanticExtractionBundleBuilder().build(bundle, "", 0, 0, 0);

        assertEquals(6, evidenceBundle.path("relationships").size());
        assertEquals(6, evidenceBundle.path("lineage").size());
        assertEquals(6, evidenceBundle.path("namingEvidence").size());
        assertEquals(6, evidenceBundle.path("eventCandidates").size());
        assertEquals(36, evidenceBundle.path("tripletCandidates").size());
    }

    @Test
    void positiveLimitsExplicitlyCreatePromptPreview() {
        List<JsonNode> relationships = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            relationships.add(relationship("orders_" + i, "customer_id", "customers_" + i, "id"));
        }
        ScanBundle bundle = new ScanBundle("mysql", "sample_data", "", List.of("logs"), List.of(),
                Map.of(), relationships, List.of(), List.of(), List.of(), List.of(), List.of());

        ObjectNode evidenceBundle = new SemanticExtractionBundleBuilder().build(bundle, "", 2, 2, 2);

        assertEquals(2, evidenceBundle.path("relationships").size());
        assertEquals(4, evidenceBundle.path("tripletCandidates").size());
    }

    @Test
    void includesDeterministicEventCandidatesForWriteLineage() {
        JsonNode lineage = lineage("sales_orders", "id", "sales_fact", "order_id",
                "ROUTINE:erp.sp_rebuild_sales_fact");
        ScanBundle bundle = new ScanBundle("mysql", "sample_data", "", List.of("object-files"), List.of(),
                Map.of(), List.of(), List.of(lineage), List.of(), List.of(), List.of(), List.of());

        ObjectNode evidenceBundle = new SemanticExtractionBundleBuilder().build(bundle, "", 50, 50, 50);

        assertEquals(1, evidenceBundle.path("eventCandidates").size());
        JsonNode event = evidenceBundle.path("eventCandidates").get(0);
        assertEquals("event-candidate:routine:erp.sp_rebuild_sales_fact", event.path("id").asText());
        assertEquals("FACT_REFRESH", event.path("eventKind").asText());
        assertEquals("ROUTINE", event.path("sourceType").asText());
        assertEquals("ROUTINE", event.path("sourceObjectType").asText());
        assertEquals("erp.sp_rebuild_sales_fact", event.path("sourceObject").asText());
        assertEquals("erp.sp_rebuild_sales_fact", event.path("sourceObjectName").asText());
        assertEquals("sales_orders.id", event.path("inputEndpoints").get(0).asText());
        assertEquals("sales_fact.order_id", event.path("outputEndpoints").get(0).asText());
        assertEquals("重建销售事实表", event.path("readableNameHint").asText());
        assertTrue(event.path("businessActionHint").asText().contains("sales_fact"));
        assertEquals("EVENT_KIND_AND_OUTPUT_TABLE", event.path("eventNameBasis").asText());
        assertFalse(event.path("evidenceRefs").isEmpty());
        assertEquals(event.path("lineageRefs").get(0).asText(), event.path("evidenceRefs").get(0).asText());
        assertEquals(event.path("lineageRefs").get(0).asText(), evidenceBundle.path("lineage").get(0).path("id").asText());
    }

    @Test
    void exposesReviewAndTripletCandidatesForPromptGrounding() {
        JsonNode relationship = relationship("orders", "customer_id", "customers", "id");
        JsonNode lineage = lineage("payments", "amount", "sales_fact", "paid_amount",
                "ROUTINE:erp.sp_rebuild_sales_fact");
        ObjectNode naming = JSON.createObjectNode();
        naming.put("id", "naming:orders.customer_id->customers.id:TABLE_ID");
        naming.set("source", endpoint("orders", "customer_id"));
        naming.set("target", endpoint("customers", "id"));
        naming.put("rule", "TABLE_ID");
        naming.put("directionHint", true);
        naming.putArray("evidence").addObject().put("type", "NAMING_MATCH").put("source", "ddl.sql");
        ObjectNode diagnostic = JSON.createObjectNode();
        diagnostic.put("code", "SEMANTIC_REVIEW_NEEDED");
        diagnostic.put("severity", "WARNING");
        diagnostic.put("message", "Metric candidate requires owner review.");
        diagnostic.put("source", "sample.sql");
        ScanBundle bundle = new ScanBundle("mysql", "sample_data", "", List.of("object-files"), List.of(),
                Map.of(), List.of(relationship), List.of(lineage), List.of(), List.of(), List.of(naming),
                List.of(diagnostic));

        ObjectNode evidenceBundle = new SemanticExtractionBundleBuilder().build(bundle, "", 50, 50, 50);

        assertFalse(evidenceBundle.path("reviewItemCandidates").isEmpty());
        assertEquals(evidenceBundle.path("diagnostics").get(0).path("id").asText(),
                evidenceBundle.path("reviewItemCandidates").get(0).path("targetRef").asText());

        JsonNode triplets = evidenceBundle.path("tripletCandidates");
        assertTrue(hasTripletType(triplets, "ENTITY_RELATION"), triplets::toPrettyString);
        assertTrue(hasTripletType(triplets, "EVENT_INPUT_OUTPUT"), triplets::toPrettyString);
        assertTrue(hasTripletType(triplets, "LINEAGE_TRANSFORM"), triplets::toPrettyString);
        assertTrue(hasTripletType(triplets, "METRIC_SOURCE"), triplets::toPrettyString);
        assertTrue(hasTripletType(triplets, "DIMENSION_OF"), triplets::toPrettyString);
        assertTrue(hasTripletType(triplets, "NAMING_ALIAS"), triplets::toPrettyString);
    }

    @Test
    void emitsStableEvidenceRegistryAndStringReferences() {
        ScanBundle bundle = new ScanBundle("mysql", "shop", "", "", List.of("logs"), List.of(), Map.of(),
                List.of(relationship("orders", "customer_id", "customers", "id")),
                List.of(), List.of(), List.of(), List.of(), List.of());

        ObjectNode evidenceBundle = new SemanticExtractionBundleBuilder().build(bundle, "", 10, 10, 10);

        assertEquals(1, evidenceBundle.path("evidence").size());
        JsonNode evidenceRef = evidenceBundle.path("relationships").get(0).path("evidenceRefs").get(0);
        assertTrue(evidenceRef.isTextual());
        assertEquals(evidenceBundle.path("evidence").get(0).path("id").asText(), evidenceRef.asText());
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

    private JsonNode lineage(String sourceTable, String sourceColumn, String targetTable, String targetColumn,
            String evidenceSource) {
        ObjectNode lineage = JSON.createObjectNode();
        lineage.putArray("sources").add(endpoint(sourceTable, sourceColumn));
        lineage.set("target", endpoint(targetTable, targetColumn));
        lineage.put("flowKind", "VALUE");
        lineage.put("transformType", "DIRECT");
        lineage.put("confidence", 0.9);
        lineage.putArray("evidence").addObject()
                .put("transformType", "DIRECT")
                .put("source", evidenceSource)
                .put("detail", "insert select");
        lineage.putArray("rawEvidence");
        return lineage;
    }

    private ObjectNode endpoint(String table, String column) {
        ObjectNode endpoint = JSON.createObjectNode();
        endpoint.put("table", table);
        endpoint.put("column", column);
        return endpoint;
    }

    private boolean hasTripletType(JsonNode triplets, String type) {
        for (JsonNode triplet : triplets) {
            if (type.equals(triplet.path("type").asText())) {
                return true;
            }
        }
        return false;
    }
}
