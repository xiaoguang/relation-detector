package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class SemanticExtractionDocumentNormalizerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void delegatesTypedNormalizationInsteadOfOwningSectionImplementations() {
        Class<?> normalizer = SemanticExtractionDocumentNormalizer.class;
        assertTrue(Arrays.stream(normalizer.getDeclaredFields())
                .anyMatch(field -> field.getType() == SemanticCandidateBackfill.class));
        assertTrue(Arrays.stream(normalizer.getDeclaredFields())
                .anyMatch(field -> field.getType() == SemanticSectionNormalizer.class));
        assertTrue(Arrays.stream(normalizer.getDeclaredFields())
                .anyMatch(field -> field.getType() == SemanticReviewGenerator.class));
        assertTrue(Arrays.stream(normalizer.getDeclaredFields())
                .anyMatch(field -> field.getType() == SemanticReferenceValidator.class));
        assertFalse(Arrays.stream(normalizer.getDeclaredMethods())
                .anyMatch(method -> method.getName().startsWith("normalizeEntities")
                        || method.getName().startsWith("normalizeEvents")
                        || method.getName().startsWith("normalizeRelations")));
    }

    @Test
    void reusesNormalizerConcurrentlyWithoutCrossDocumentValidationState() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "evidenceRefs": ["e1"]}
                  ],
                  "events": [],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);
        SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();
        String expected = normalizer.normalize(raw).toString();
        List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 32)
                .mapToObj(index -> (Callable<String>) () -> normalizer.normalize(raw).toString())
                .toList();

        var executor = Executors.newFixedThreadPool(8);
        try {
            for (var result : executor.invokeAll(tasks)) {
                assertEquals(expected, result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void addsStableRefsAndGraphForSemanticExtractionDocument() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
                  ],
                  "events": [
                    {"name": "重建销售事实表", "physicalName": "erp.sp_rebuild_sales_fact", "type": "数据加工事件", "eventCandidateRef": "event-candidate:routine:erp.sp_rebuild_sales_fact", "inputs": ["销售订单"], "outputs": ["销售事实表"], "evidenceRefs": ["event-candidate:routine:erp.sp_rebuild_sales_fact"]}
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
                    {"subject": "销售事实表", "predicate": "来源于", "object": "销售订单", "candidateRef": "triplet-candidate:lineage:0", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]}
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

    @Test
    void preservesExplicitEventEntityRefsAndCandidateRef() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["event-candidate:fact"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["event-candidate:fact"]}
                  ],
                  "events": [
                    {
                      "name": "重建销售事实表",
                      "type": "FactRefreshEvent",
                      "eventCandidateRef": "event-candidate:routine:erp.sp_rebuild_sales_fact",
                      "inputEntityRefs": ["entity:sales_orders"],
                      "outputEntityRefs": ["entity:sales_fact"],
                      "evidenceRefs": ["event-candidate:routine:erp.sp_rebuild_sales_fact"]
                    }
                  ],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw);

        assertEquals("event-candidate:routine:erp.sp_rebuild_sales_fact",
                normalized.path("events").get(0).path("eventCandidateRef").asText());
        assertEquals("entity:sales_orders", normalized.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertEquals("entity:sales_fact", normalized.path("events").get(0).path("outputEntityRefs").get(0).asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void generatesReviewItemForReviewNeededMetric() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["metric:evidence"]}
                  ],
                  "events": [],
                  "relations": [],
                  "lineage": [],
                  "metrics": [
                    {
                      "name": "毛利率",
                      "physicalField": "sales_fact.gross_margin_rate",
                      "reviewStatus": "REVIEW_NEEDED",
                      "evidenceRefs": ["metric:evidence"]
                    }
                  ],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw);

        assertEquals(1, normalized.path("reviewItems").size());
        JsonNode review = normalized.path("reviewItems").get(0);
        assertEquals("metric:sales_fact.gross_margin_rate", review.path("targetRef").asText());
        assertEquals("metrics", review.path("targetSection").asText());
        assertEquals("REVIEW_NEEDED", review.path("type").asText());
        assertEquals(1, normalized.path("validation").path("generatedReviewItemCount").asInt());
    }

    @Test
    void reportsTripletWithoutCandidateRef() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["e1"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["e1"]}
                  ],
                  "events": [],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [
                    {"subject": "销售事实表", "predicate": "来源于", "object": "销售订单", "evidenceRefs": ["e1"]}
                  ],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw);

        assertFalse(normalized.path("validation").path("isRefClosed").asBoolean());
        assertTrue(normalized.path("validation").path("unresolvedReferences").toString()
                .contains("candidateRef"));
    }

    @Test
    void fillsMissingCandidateBackedItemsFromEvidenceBundle() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["relationship:fact"]},
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["relationship:fact"]}
                  ],
                  "events": [],
                  "relations": [],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);
        JsonNode evidenceBundle = JSON.readTree("""
                {
                  "eventCandidates": [
                    {
                      "id": "event-candidate:routine:erp.sp_rebuild_sales_fact",
                      "eventKind": "FACT_REFRESH",
                      "sourceType": "ROUTINE",
                      "sourceObjectName": "erp.sp_rebuild_sales_fact",
                      "readableNameHint": "重建销售事实表",
                      "businessActionHint": "从 sales_orders 写入 sales_fact",
                      "inputEndpoints": ["sales_orders.id"],
                      "outputEndpoints": ["sales_fact.order_id"],
                      "evidenceRefs": ["lineage:sales_orders.id->sales_fact.order_id:VALUE:DIRECT:0"]
                    }
                  ],
                  "tripletCandidates": [
                    {
                      "id": "triplet-candidate:event:0",
                      "type": "EVENT_INPUT_OUTPUT",
                      "subject": "sales_orders",
                      "predicate": "写入",
                      "object": "sales_fact",
                      "readable": "销售订单 写入 销售事实表",
                      "evidenceRefs": ["event-candidate:routine:erp.sp_rebuild_sales_fact"]
                    }
                  ],
                  "reviewItemCandidates": [
                    {
                      "id": "review-candidate:diagnostic:0:SEMANTIC_REVIEW_NEEDED",
                      "targetRef": "diagnostic:0:SEMANTIC_REVIEW_NEEDED",
                      "targetSection": "diagnostics",
                      "type": "REVIEW_NEEDED",
                      "severity": "WARNING",
                      "reason": "Metric candidate requires owner review.",
                      "evidenceRefs": ["diagnostic:0:SEMANTIC_REVIEW_NEEDED"]
                    }
                  ]
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle);

        assertEquals(1, normalized.path("events").size());
        assertEquals("event-candidate:routine:erp.sp_rebuild_sales_fact",
                normalized.path("events").get(0).path("eventCandidateRef").asText());
        assertEquals(1, normalized.path("triplets").size());
        assertEquals("triplet-candidate:event:0", normalized.path("triplets").get(0).path("candidateRef").asText());
        assertEquals(1, normalized.path("reviewItems").size());
        assertEquals("diagnostic:0:SEMANTIC_REVIEW_NEEDED",
                normalized.path("reviewItems").get(0).path("targetRef").asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }
}
