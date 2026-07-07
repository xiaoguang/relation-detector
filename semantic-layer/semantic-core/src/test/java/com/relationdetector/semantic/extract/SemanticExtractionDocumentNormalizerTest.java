package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticExtractionDocumentNormalizerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void addsStableRefsAndGraphForSemanticExtractionDocument() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "events": [
                    {"name": "重建销售事实表", "physicalName": "ROUTINE:erp.sp_rebuild_sales_fact", "type": "数据加工事件", "inputs": ["销售订单"], "outputs": ["销售事实表"], "evidenceRefs": ["ROUTINE:erp.sp_rebuild_sales_fact"]}
                  ],
                  "relations": [
                    {"type": "数据来源关系", "from": "销售事实表", "to": "销售订单", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "lineage": [
                    {"from": ["销售订单.订单ID"], "fromPhysical": ["sales_orders.id"], "to": "销售事实表.订单ID", "toPhysical": "sales_fact.order_id", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "metrics": [
                    {"name": "销售金额", "physicalField": "sales_fact.sales_amount", "sourceFields": ["sales_order_items.amount"], "evidenceRefs": ["sales_order_items.amount -> sales_fact.sales_amount"]}
                  ],
                  "dimensions": [
                    {"name": "销售订单", "physicalField": "sales_fact.order_id", "dimensionTable": "sales_orders", "evidenceRefs": ["sales_fact.order_id -> sales_orders.id"]}
                  ],
                  "triplets": [
                    {"subject": "销售事实表", "predicate": "来源于", "object": "销售订单", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw);

        assertEquals("entity:sales_fact", normalized.path("entities").get(0).path("id").asText());
        assertEquals("event:erp.sp_rebuild_sales_fact", normalized.path("events").get(0).path("id").asText());
        assertEquals("entity:sales_orders", normalized.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertEquals("entity:sales_fact", normalized.path("events").get(0).path("outputEntityRefs").get(0).asText());
        assertEquals("entity:sales_fact", normalized.path("relations").get(0).path("fromEntityRef").asText());
        assertEquals("entity:sales_orders", normalized.path("relations").get(0).path("toEntityRef").asText());
        assertEquals("entity:sales_fact", normalized.path("metrics").get(0).path("ownerEntityRef").asText());
        assertEquals("entity:sales_orders", normalized.path("dimensions").get(0).path("dimensionEntityRef").asText());
        assertTrue(normalized.path("semanticGraph").path("nodes").isArray());
        assertTrue(normalized.path("semanticGraph").path("edges").isArray());
        assertFalse(normalized.path("semanticGraph").path("nodes").isEmpty());
        assertFalse(normalized.path("semanticGraph").path("edges").isEmpty());
        assertTrue(normalized.path("validation").path("isolatedEntities").isEmpty());
    }

    @Test
    void reportsUnresolvedReferencesAndMissingEvidenceRefs() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "events": [
                    {"name": "重建销售事实表", "physicalName": "ROUTINE:erp.sp_rebuild_sales_fact", "type": "数据加工事件", "inputs": ["销售订单"], "outputs": ["销售事实表"], "evidenceRefs": []}
                  ],
                  "relations": [
                    {"type": "数据来源关系", "from": "销售事实表", "to": "销售订单", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "lineage": [
                    {"fromPhysical": ["sales_orders.id"], "toPhysical": "sales_fact.order_id", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw);

        assertFalse(normalized.path("validation").path("isRefClosed").asBoolean());
        assertFalse(normalized.path("validation").path("unresolvedReferences").isEmpty());
        assertFalse(normalized.path("validation").path("missingEvidenceRefs").isEmpty());
        assertEquals("event:erp.sp_rebuild_sales_fact", normalized.path("validation").path("missingEvidenceRefs").get(0)
                .path("id").asText());
    }
}
