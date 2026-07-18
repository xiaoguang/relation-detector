package com.relationdetector.semantic.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanBundle;

final class SemanticEventExtractorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void groupsRoutineLineageIntoEvidenceBackedFactRefreshEvent() {
        ScanBundle bundle = new ScanBundle("mysql", "erp", "", List.of("object-files"), List.of(), Map.of(),
                List.of(), List.of(
                lineage("sales_orders", "id", "sales_fact", "order_id", "ROUTINE:erp.sp_rebuild_sales_fact",
                        "INSERT SELECT", "DIRECT"),
                lineage("payments", "amount", "sales_fact", "paid_amount", "ROUTINE:erp.sp_rebuild_sales_fact",
                        "INSERT SELECT", "AGGREGATE")
        ), List.of(), List.of(), List.of(), List.of());

        List<SemanticEventCandidate> events = new SemanticEventExtractor().extract(bundle);

        assertEquals(1, events.size());
        SemanticEventCandidate event = events.get(0);
        assertEquals("event-candidate:routine:erp.sp_rebuild_sales_fact", event.id());
        assertEquals("ROUTINE", event.sourceType());
        assertEquals("FACT_REFRESH", event.eventKind());
        assertEquals("erp.sp_rebuild_sales_fact", event.sourceObject());
        assertEquals("ROUTINE", event.sourceObjectType());
        assertEquals("erp.sp_rebuild_sales_fact", event.sourceObjectName());
        assertTrue(event.inputEndpoints().contains("sales_orders.id"));
        assertTrue(event.inputEndpoints().contains("payments.amount"));
        assertTrue(event.outputEndpoints().contains("sales_fact.order_id"));
        assertTrue(event.outputEndpoints().contains("sales_fact.paid_amount"));
        assertEquals(2, event.lineageRefs().size());
        assertTrue(event.evidenceRefs().containsAll(event.lineageRefs()));
    }

    @Test
    void extractsStandaloneSqlWriteAndTriggerEventsButIgnoresPureRelationships() {
        ScanBundle bundle = new ScanBundle("mysql", "erp", "", List.of("logs"), List.of(), Map.of(),
                List.of(relationship("orders", "customer_id", "customers", "id")), List.of(
                lineage("inventory_transactions", "quantity", "inventory", "quantity", "04-queries/stock.sql",
                        "UPDATE inventory SET quantity = quantity - x.quantity", "ARITHMETIC"),
                lineage("orders", "status", "order_audit", "status", "TRIGGER:erp.trg_orders_audit",
                        "INSERT audit row", "DIRECT")
        ), List.of(), List.of(), List.of(), List.of());

        List<SemanticEventCandidate> events = new SemanticEventExtractor().extract(bundle);

        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(event -> event.sourceType().equals("SQL_WRITE")
                && event.eventKind().equals("INVENTORY_MOVEMENT")
                && event.outputEndpoints().contains("inventory.quantity")));
        assertTrue(events.stream().anyMatch(event -> event.sourceType().equals("TRIGGER")
                && event.sourceObject().equals("erp.trg_orders_audit")));
    }

    @Test
    void doesNotCreateEventsWithoutWriteLineage() {
        ScanBundle bundle = new ScanBundle("mysql", "erp", "", List.of("logs"), List.of(), Map.of(),
                List.of(relationship("orders", "customer_id", "customers", "id")), List.of(),
                List.of(), List.of(), List.of(), List.of());

        assertTrue(new SemanticEventExtractor().extract(bundle).isEmpty());
    }

    @Test
    void derivedLineageDoesNotCreateStandaloneEventButCanSupportDirectEvent() {
        JsonNode direct = lineage("orders", "amount", "sales_fact", "gross_amount",
                "ROUTINE:erp.sp_rebuild_sales_fact", "INSERT SELECT", "DIRECT");
        JsonNode derived = derivedLineage("payments.amount", "sales_fact.net_amount");
        ScanBundle bundle = new ScanBundle("mysql", "erp", "", List.of("object-files"), List.of(), Map.of(),
                List.of(), List.of(direct), List.of(), List.of(derived), List.of(), List.of());

        List<SemanticEventCandidate> events = new SemanticEventExtractor().extract(bundle);

        assertEquals(1, events.size());
        assertEquals(1, events.get(0).lineageRefs().size());
        assertEquals(1, events.get(0).supportingDerivedLineageRefs().size());
        assertTrue(events.get(0).evidenceRefs().containsAll(events.get(0).supportingDerivedLineageRefs()));
    }

    @Test
    void derivedOnlyLineageDoesNotCreateEvent() {
        ScanBundle bundle = new ScanBundle("mysql", "erp", "", List.of("object-files"), List.of(), Map.of(),
                List.of(), List.of(), List.of(), List.of(derivedLineage("payments.amount", "sales_fact.net_amount")),
                List.of(), List.of());

        assertTrue(new SemanticEventExtractor().extract(bundle).isEmpty());
    }

    @Test
    void relationshipRefsOnlyIncludeDirectTouchingRelationships() {
        ScanBundle bundle = new ScanBundle("mysql", "erp", "", List.of("logs"), List.of(), Map.of(),
                List.of(
                        relationship("orders", "customer_id", "customers", "id"),
                        relationship("sales_fact", "order_id", "orders", "id"),
                        relationship("sales_fact", "sales_rep_id", "employees", "id"),
                        relationship("unrelated", "owner_id", "users", "id")
                ),
                List.of(lineage("orders", "customer_id", "sales_fact", "customer_id",
                        "04-queries/fact.sql", "INSERT SELECT", "DIRECT")),
                List.of(relationship("sales_fact", "customer_id", "customers", "id")),
                List.of(), List.of(), List.of());

        List<SemanticEventCandidate> events = new SemanticEventExtractor().extract(bundle);

        assertEquals(1, events.size());
        assertEquals(1, events.get(0).relationshipRefs().size());
        assertEquals(bundle.relationships().get(1).id(), events.get(0).relationshipRefs().get(0));
        assertFalse(events.get(0).relationshipRefs().contains(bundle.relationships().get(0).id()));
        assertFalse(events.get(0).relationshipRefs().contains(bundle.relationships().get(2).id()));
        assertFalse(events.get(0).relationshipRefs().contains(bundle.derivedRelationships().get(0).id()));
    }

    private JsonNode lineage(
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            String evidenceSource,
            String detail,
            String transformType
    ) {
        ObjectNode lineage = JSON.createObjectNode();
        lineage.putArray("sources").add(endpoint(sourceTable, sourceColumn));
        lineage.set("target", endpoint(targetTable, targetColumn));
        lineage.put("flowKind", "VALUE");
        lineage.put("transformType", transformType);
        lineage.put("confidence", 0.82);
        lineage.putArray("evidence").addObject()
                .put("transformType", transformType)
                .put("sourceType", "PLAIN_SQL")
                .put("score", 0.82)
                .put("source", evidenceSource)
                .put("detail", detail)
                .set("attributes", evidenceAttributes(evidenceSource));
        lineage.putArray("rawEvidence");
        return lineage;
    }

    private JsonNode derivedLineage(String source, String target) {
        ObjectNode lineage = JSON.createObjectNode();
        lineage.set("source", endpointFromDisplay(source));
        lineage.set("target", endpointFromDisplay(target));
        lineage.put("flowKind", "VALUE");
        lineage.put("transformType", "DIRECT");
        lineage.put("confidence", 0.6);
        lineage.put("pathLength", 2);
        lineage.putArray("evidence").addObject()
                .put("transformType", "DIRECT")
                .put("sourceType", "INFERENCE")
                .put("score", 0.6)
                .put("source", "derived")
                .put("detail", source + " -> " + target);
        lineage.putArray("rawEvidence");
        return lineage;
    }

    private JsonNode relationship(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
        ObjectNode relationship = JSON.createObjectNode();
        relationship.set("source", endpoint(sourceTable, sourceColumn));
        relationship.set("target", endpoint(targetTable, targetColumn));
        relationship.put("relationType", "FK_LIKE");
        relationship.put("confidence", 0.9);
        relationship.putArray("evidence").addObject().put("type", "DDL_FOREIGN_KEY").put("source", "schema.sql");
        relationship.putArray("rawEvidence");
        return relationship;
    }

    private ObjectNode endpoint(String table, String column) {
        ObjectNode endpoint = JSON.createObjectNode();
        endpoint.put("table", table);
        endpoint.put("column", column);
        return endpoint;
    }

    private ObjectNode endpointFromDisplay(String display) {
        int index = display.lastIndexOf('.');
        return endpoint(display.substring(0, index), display.substring(index + 1));
    }

    private ObjectNode evidenceAttributes(String evidenceSource) {
        ObjectNode attributes = JSON.createObjectNode();
        if (evidenceSource.startsWith("ROUTINE:")) {
            attributes.put("sourceObjectType", "ROUTINE");
            attributes.put("sourceObjectName", evidenceSource.substring("ROUTINE:".length()));
            attributes.put("sourceStatementId", evidenceSource.substring("ROUTINE:".length()));
        } else if (evidenceSource.startsWith("TRIGGER:")) {
            attributes.put("sourceObjectType", "TRIGGER");
            attributes.put("sourceObjectName", evidenceSource.substring("TRIGGER:".length()));
            attributes.put("sourceStatementId", evidenceSource.substring("TRIGGER:".length()));
        } else {
            attributes.put("sourceObjectType", "SQL_WRITE");
            attributes.put("sourceFile", evidenceSource);
            attributes.put("sourceStatementId", evidenceSource + ":1-1");
        }
        return attributes;
    }
}
