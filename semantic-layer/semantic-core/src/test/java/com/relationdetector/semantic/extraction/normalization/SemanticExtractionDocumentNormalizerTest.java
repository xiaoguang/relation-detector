package com.relationdetector.semantic.extraction.normalization;

import com.relationdetector.semantic.extraction.normalization.SemanticSectionNormalizer;

import com.relationdetector.semantic.extraction.normalization.SemanticReviewGenerator;

import com.relationdetector.semantic.extraction.normalization.SemanticReferenceValidator;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionDocumentNormalizer;

import com.relationdetector.semantic.extraction.normalization.SemanticCandidateBackfill;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

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
    void rejectsUnknownEvidenceReference() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [{"name": "订单", "physicalName": "orders", "evidenceRefs": ["evidence:missing"]}],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("evidence:known")));
    }

    @Test
    void rejectsOwnedGroundingReferenceOutsideTheFullEvidenceBundle() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [{
                    "name": "订单",
                    "physicalName": "orders",
                    "ownedGroundingRefs": ["relationship:missing"],
                    "evidenceRefs": ["evidence:known"]
                  }],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("evidence:known")));
    }

    @Test
    void rejectsBusinessApprovedFromModelOutput() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [{
                    "name": "订单", "physicalName": "orders", "reviewStatus": "BUSINESS_APPROVED",
                    "evidenceRefs": ["evidence:known"]
                  }],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("evidence:known")));
    }

    @Test
    void rejectsPhysicalTableThatIsAbsentFromEvidenceBundle() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name":"订单","physicalName":"orders","evidenceRefs":["e1"]},
                    {"name":"虚构客户","physicalName":"invented_customers","evidenceRefs":["e1"]}
                  ],
                  "events": [],
                  "relations": [{"from":"订单","to":"虚构客户","type":"RELATES_TO","evidenceRefs":["e1"]}],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("orders");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, bundle));
    }

    @Test
    void rejectsPhysicalColumnThatIsAbsentFromEvidenceBundle() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [{"name":"销售事实","physicalName":"sales_fact","evidenceRefs":["e1"]}],
                  "events": [], "relations": [], "lineage": [],
                  "metrics": [{"name":"虚构金额","physicalField":"sales_fact.invented_amount","evidenceRefs":["e1"]}],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("sales_fact");
        ObjectNode lineage = bundle.putArray("lineage").addObject();
        lineage.put("id", "lineage:known");
        lineage.putArray("sources").add("sales_order_items.amount");
        lineage.put("target", "sales_fact.sales_amount");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, bundle));
    }

    @Test
    void rejectsDuplicateSemanticOwnerIdsAcrossSections() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [{"id":"semantic:duplicate","name":"订单","physicalName":"orders","evidenceRefs":["e1"]}],
                  "events": [],
                  "relations": [{"id":"semantic:duplicate","from":"订单","to":"订单","type":"SELF","evidenceRefs":["e1"]}],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("orders");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, bundle));
    }

    @Test
    void preservesParallelSemanticRelationsBetweenTheSameEntityPair() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:boms","name":"BOM","physicalName":"sample_data.boms","evidenceRefs":["e1"]},
                    {"id":"entity:products","name":"物料","physicalName":"sample_data.products","evidenceRefs":["e1"]}
                  ],
                  "events": [],
                  "relations": [
                    {
                      "id":"relation:parent","fromEntityRef":"entity:boms","toEntityRef":"entity:products",
                      "type":"组成关联","machineType":"FK_LIKE","evidenceRefs":["relationship:parent"]
                    },
                    {
                      "id":"relation:child","fromEntityRef":"entity:boms","toEntityRef":"entity:products",
                      "type":"组成关联","machineType":"FK_LIKE","evidenceRefs":["relationship:child"]
                    }
                  ],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("sample_data.boms").add("sample_data.products");
        bundle.withArray("relationships").addObject().put("id", "relationship:parent");
        bundle.withArray("relationships").addObject().put("id", "relationship:child");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        List<JsonNode> directRelationEdges = new java.util.ArrayList<>();
        normalized.path("semanticGraph").path("edges").forEach(edge -> {
            if ("组成关联".equals(edge.path("type").asText())) {
                directRelationEdges.add(edge);
            }
        });
        assertEquals(2, directRelationEdges.size());
        assertNotEquals(
                directRelationEdges.get(0).path("id").asText(),
                directRelationEdges.get(1).path("id").asText());
    }

    @Test
    void derivesPhysicalEntityIdFromTheCompletePhysicalName() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name":"订单","physicalName":"sales.orders","type":"BUSINESS_ENTITY","evidenceRefs":["e1"]}
                  ],
                  "events": [],
                  "relations": [
                    {"from":"订单","to":"订单","type":"SELF","evidenceRefs":["e1"]}
                  ],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("sales.orders");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertEquals(StableSemanticId.of("entity-physical", "sales.orders"),
                normalized.path("entities").get(0).path("id").asText());
    }

    @Test
    void derivesBusinessEntityIdFromNormalizedTypeAndOwnedGrounding() throws Exception {
        JsonNode first = businessEntityDocument("[\"e1\",\"e2\"]");
        JsonNode reordered = businessEntityDocument("[\"e2\",\"e1\"]");
        JsonNode differentGrounding = businessEntityDocument("[\"e1\",\"e3\"]");
        ObjectNode bundle = evidenceBundle("e1", "e2", "e3");
        SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();

        String firstId = normalizer.normalize(first, bundle).path("entities").get(0).path("id").asText();
        String reorderedId = normalizer.normalize(reordered, bundle).path("entities").get(0).path("id").asText();
        String differentId = normalizer.normalize(
                differentGrounding, bundle).path("entities").get(0).path("id").asText();

        assertEquals(firstId, reorderedId);
        assertNotEquals(firstId, differentId);
    }

    @Test
    void punctuationDistinctEventCandidateRefsProduceDistinctDefaultIds() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:orders","name":"订单","physicalName":"orders","evidenceRefs":["e1"]}
                  ],
                  "events": [
                    {
                      "name":"创建订单A","eventCandidateRef":"event-candidate:orders/a",
                      "outputEntityRefs":["entity:orders"],
                      "evidenceRefs":["event-candidate:orders/a"]
                    },
                    {
                      "name":"创建订单B","eventCandidateRef":"event-candidate:orders_a",
                      "outputEntityRefs":["entity:orders"],
                      "evidenceRefs":["event-candidate:orders_a"]
                    }
                  ],
                  "relations": [], "lineage": [], "metrics": [], "dimensions": [],
                  "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("orders");
        bundle.withArray("eventCandidates").addObject().put("id", "event-candidate:orders/a");
        bundle.withArray("eventCandidates").addObject().put("id", "event-candidate:orders_a");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertNotEquals(
                normalized.path("events").get(0).path("id").asText(),
                normalized.path("events").get(1).path("id").asText());
    }

    @Test
    void punctuationDistinctMetricAndDimensionInputsProduceDistinctDefaultIds() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:facts","name":"事实","physicalName":"facts","evidenceRefs":["e1"]},
                    {"id":"entity:dims","name":"维表","physicalName":"dims","evidenceRefs":["e1"]}
                  ],
                  "events": [], "relations": [], "lineage": [],
                  "metrics": [
                    {"name":"金额/A","physicalField":"facts.amount/a","sourceFields":["facts.source/a"],"evidenceRefs":["e1"]},
                    {"name":"金额_A","physicalField":"facts.amount_a","sourceFields":["facts.source_a"],"evidenceRefs":["e1"]}
                  ],
                  "dimensions": [
                    {"name":"维度/A","physicalField":"facts.key/a","dimensionTable":"dims","evidenceRefs":["e1"]},
                    {"name":"维度_A","physicalField":"facts.key_a","dimensionTable":"dims","evidenceRefs":["e1"]}
                  ],
                  "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("facts").add("dims");
        ObjectNode lineage = bundle.putArray("lineage").addObject();
        lineage.put("id", "lineage:identity-columns");
        lineage.putArray("sources")
                .add("facts.amount/a")
                .add("facts.amount_a")
                .add("facts.source/a")
                .add("facts.source_a")
                .add("facts.key/a")
                .add("facts.key_a");
        lineage.put("target", "facts.target");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertNotEquals(
                normalized.path("metrics").get(0).path("id").asText(),
                normalized.path("metrics").get(1).path("id").asText());
        assertNotEquals(
                normalized.path("dimensions").get(0).path("id").asText(),
                normalized.path("dimensions").get(1).path("id").asText());
    }

    @Test
    void punctuationDistinctOwnerIdsProduceDistinctGeneratedReviewIds() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:facts","name":"事实","physicalName":"facts","evidenceRefs":["e1"]}
                  ],
                  "events": [], "relations": [], "lineage": [],
                  "metrics": [
                    {
                      "id":"metric:a/b","name":"金额A","physicalField":"facts.amount_a",
                      "reviewStatus":"REVIEW_NEEDED","evidenceRefs":["e1"]
                    },
                    {
                      "id":"metric:a_b","name":"金额B","physicalField":"facts.amount_b",
                      "reviewStatus":"REVIEW_NEEDED","evidenceRefs":["e1"]
                    }
                  ],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.putArray("tables").add("facts");
        ObjectNode lineage = bundle.putArray("lineage").addObject();
        lineage.put("id", "lineage:review-columns");
        lineage.putArray("sources").add("facts.amount_a").add("facts.amount_b");
        lineage.put("target", "facts.target");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertEquals(2, normalized.path("reviewItems").size());
        assertNotEquals(
                normalized.path("reviewItems").get(0).path("id").asText(),
                normalized.path("reviewItems").get(1).path("id").asText());
    }

    @Test
    void reusesNormalizerConcurrentlyWithoutCrossDocumentValidationState() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [],
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
        JsonNode evidenceBundle = evidenceBundle("e1");
        String expected = normalizer.normalize(raw, evidenceBundle).toString();
        List<Callable<String>> tasks = java.util.stream.IntStream.range(0, 32)
                .mapToObj(index -> (Callable<String>) () -> normalizer.normalize(raw, evidenceBundle).toString())
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
                    {"name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["sales_orders.id -> sales_fact.order_id"]},
                    {"name": "销售订单明细", "physicalName": "sales_order_items", "type": "业务明细", "evidenceRefs": ["sales_order_items.amount -> sales_fact.sales_amount"]}
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

        ObjectNode evidenceBundle = evidenceBundle(
                "sales_orders.id -> sales_fact.order_id",
                "sales_order_items.amount -> sales_fact.sales_amount",
                "sales_fact.order_id -> sales_orders.id");
        evidenceBundle.withArray("eventCandidates").addObject()
                .put("id", "event-candidate:routine:erp.sp_rebuild_sales_fact");
        evidenceBundle.withArray("tripletCandidates").addObject()
                .put("id", "triplet-candidate:lineage:0");
        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle);

        String salesFactId = StableSemanticId.of("entity-physical", "sales_fact");
        String salesOrdersId = StableSemanticId.of("entity-physical", "sales_orders");
        assertEquals(salesFactId, normalized.path("entities").get(0).path("id").asText());
        assertEquals(StableSemanticId.of("event", "event-candidate:routine:erp.sp_rebuild_sales_fact"),
                normalized.path("events").get(0).path("id").asText());
        assertEquals(salesOrdersId, normalized.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertEquals(salesFactId, normalized.path("events").get(0).path("outputEntityRefs").get(0).asText());
        assertEquals(salesFactId, normalized.path("relations").get(0).path("fromEntityRef").asText());
        assertEquals(salesOrdersId, normalized.path("relations").get(0).path("toEntityRef").asText());
        assertEquals(salesFactId, normalized.path("metrics").get(0).path("ownerEntityRef").asText());
        assertEquals(salesOrdersId, normalized.path("dimensions").get(0).path("dimensionEntityRef").asText());
        for (String section : List.of(
                "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets")) {
            assertEquals("SYSTEM_PROPOSED",
                    normalized.path(section).get(0).path("reviewStatus").asText(),
                    section);
        }
        assertTrue(normalized.path("semanticGraph").path("nodes").isArray());
        assertTrue(normalized.path("semanticGraph").path("edges").isArray());
        assertFalse(normalized.path("semanticGraph").path("nodes").isEmpty());
        assertFalse(normalized.path("semanticGraph").path("edges").isEmpty());
        assertTrue(normalized.path("validation").path("isolatedEntities").isEmpty());
    }

    @Test
    void backfillsPhysicalEntitiesRequiredBySemanticLineage() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [],
                  "events": [],
                  "relations": [],
                  "lineage": [
                    {
                      "fromPhysical": ["sales_orders.id"],
                      "toPhysical": "sales_fact.order_id",
                      "transform": "DIRECT",
                      "evidenceRefs": ["e1"]
                    }
                  ],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("e1"));

        assertEquals(2, normalized.path("entities").size());
        assertEquals(
                StableSemanticId.of("entity-physical", "sales_orders"),
                normalized.path("lineage").get(0).path("sourceEntityRefs").get(0).asText());
        assertEquals(
                StableSemanticId.of("entity-physical", "sales_fact"),
                normalized.path("lineage").get(0).path("targetEntityRef").asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
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

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(
                        raw, evidenceBundle("sales_orders.id -> sales_fact.order_id")));
    }

    @Test
    void preservesExplicitEventEntityRefsAndCandidateRef() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:sales_fact","name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表", "evidenceRefs": ["event-candidate:fact"]},
                    {"id":"entity:sales_orders","name": "销售订单", "physicalName": "sales_orders", "type": "业务单据", "evidenceRefs": ["event-candidate:fact"]}
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

        ObjectNode evidenceBundle = evidenceBundle("event-candidate:fact");
        evidenceBundle.withArray("eventCandidates").addObject()
                .put("id", "event-candidate:routine:erp.sp_rebuild_sales_fact");
        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle);

        assertEquals("event-candidate:routine:erp.sp_rebuild_sales_fact",
                normalized.path("events").get(0).path("eventCandidateRef").asText());
        assertEquals("entity:sales_orders", normalized.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertEquals("entity:sales_fact", normalized.path("events").get(0).path("outputEntityRefs").get(0).asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void derivesDefaultEventIdFromValidatedCandidateInsteadOfRoutinePrefix() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"name": "销售事实表", "physicalName": "sales_fact", "type": "分析事实表",
                     "evidenceRefs": ["event-support"]}
                  ],
                  "events": [
                    {
                      "name": "刷新销售事实",
                      "physicalName": "ROUTINE:public.refresh_sales",
                      "type": "SQL_WRITE_OPERATION",
                      "eventCandidateRef": "event-candidate:routine:function:public.refresh_sales-bigint",
                      "outputs": ["销售事实表"],
                      "evidenceRefs": ["event-candidate:routine:function:public.refresh_sales-bigint"]
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
        ObjectNode evidenceBundle = evidenceBundle("event-support");
        evidenceBundle.withArray("eventCandidates").addObject()
                .put("id", "event-candidate:routine:function:public.refresh_sales-bigint");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle);

        assertEquals(StableSemanticId.of(
                        "event", "event-candidate:routine:function:public.refresh_sales-bigint"),
                normalized.path("events").get(0).path("id").asText());
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

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("metric:evidence"));

        assertEquals(1, normalized.path("reviewItems").size());
        JsonNode review = normalized.path("reviewItems").get(0);
        assertEquals(StableSemanticId.of(
                        "metric", "毛利率", "", "sales_fact.gross_margin_rate", ""),
                review.path("targetRef").asText());
        assertEquals("metrics", review.path("targetSection").asText());
        assertEquals("REVIEW_NEEDED", review.path("type").asText());
        assertEquals("REVIEW_NEEDED", review.path("reviewStatus").asText());
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

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("e1")));
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
                  "tables": ["sales_fact", "sales_orders"],
                  "evidence": [],
                  "metadataTables": [],
                  "metadataColumns": [],
                  "metadataConstraints": [],
                  "metadataIndexes": [],
                  "relationships": [{"id": "relationship:fact"}],
                  "lineage": [{"id": "lineage:sales_orders.id->sales_fact.order_id:VALUE:DIRECT:0"}],
                  "derivedRelationships": [],
                  "derivedLineage": [],
                  "namingEvidence": [],
                  "diagnostics": [{"id": "diagnostic:0:SEMANTIC_REVIEW_NEEDED"}],
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

    @Test
    void generatedSemanticIdsDoNotDependOnSectionOrder() throws Exception {
        JsonNode first = JSON.readTree(semanticDocument(false));
        JsonNode reordered = JSON.readTree(semanticDocument(true));
        ObjectNode evidenceBundle = evidenceBundle("e1", "e2");
        evidenceBundle.withArray("tripletCandidates").addObject().put("id", "triplet-candidate:first");
        evidenceBundle.withArray("tripletCandidates").addObject().put("id", "triplet-candidate:second");

        SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();
        JsonNode firstNormalized = normalizer.normalize(first, evidenceBundle);
        JsonNode reorderedNormalized = normalizer.normalize(reordered, evidenceBundle);

        for (String section : List.of("relations", "lineage", "triplets", "reviewItems")) {
            assertEquals(sectionIds(firstNormalized, section), sectionIds(reorderedNormalized, section), section);
        }
    }

    @Test
    void ownedTripletCandidateBackfillsMissingPhysicalEndpointEntities() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [], "events": [], "relations": [], "lineage": [],
                  "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle();
        bundle.withArray("tables").add("serial_number_logs").add("serial_numbers");
        bundle.withArray("tripletCandidates").addObject()
                .put("id", "triplet-candidate:serial-number")
                .put("type", "NAMING_ALIAS")
                .put("subject", "serial_number_logs.serial_number_id")
                .put("predicate", "命名指向")
                .put("object", "serial_numbers.id")
                .put("readable", "serial_number_logs.serial_number_id 命名指向 serial_numbers.id");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertEquals(2, normalized.path("entities").size());
        assertEquals(1, normalized.path("triplets").size());
        assertEquals(
                StableSemanticId.of("entity-physical", "serial_number_logs"),
                normalized.path("triplets").get(0).path("subjectRef").asText());
        assertEquals(
                StableSemanticId.of("entity-physical", "serial_numbers"),
                normalized.path("triplets").get(0).path("objectRef").asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void projectedEventCandidateAuditRefsRemainAvailableToDeterministicBackfill() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [], "events": [], "relations": [], "lineage": [],
                  "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle();
        bundle.withArray("eventCandidates").addObject()
                .put("id", "event-candidate:projected")
                .put("readableNameHint", "处理订单")
                .put("evidenceRefCount", 3)
                .put("evidenceRefsSha256", "abc123")
                .put("lineageRefCount", 2)
                .put("lineageRefsSha256", "def456");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertEquals(1, normalized.path("events").size());
        assertEquals("event-candidate:projected",
                normalized.path("events").get(0).path("eventCandidateRef").asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void deterministicEventCandidateOwnsEndpointLabelsAndEntityRefs() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {
                      "id":"entity:orders", "name":"销售订单", "physicalName":"orders",
                      "machineType":"BUSINESS_DOCUMENT", "evidenceRefs":["event-candidate:orders"]
                    },
                    {
                      "id":"entity:ledger", "name":"销售台账", "physicalName":"sales_ledger",
                      "machineType":"BUSINESS_FACT", "evidenceRefs":["event-candidate:orders"]
                    }
                  ],
                  "events": [
                    {
                      "id":"event:orders", "name":"过账订单", "machineType":"SQL_WRITE_OPERATION",
                      "eventCandidateRef":"event-candidate:orders",
                      "inputs":[], "outputs":[],
                      "inputEntityRefs":[], "outputEntityRefs":[],
                      "evidenceRefs":["event-candidate:orders"]
                    }
                  ],
                  "relations": [], "lineage": [], "metrics": [], "dimensions": [],
                  "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle();
        bundle.withArray("tables").add("orders").add("sales_ledger");
        ObjectNode candidate = bundle.withArray("eventCandidates").addObject()
                .put("id", "event-candidate:orders");
        candidate.withArray("inputEndpoints").add("orders.id");
        candidate.withArray("outputEndpoints").add("sales_ledger.order_id");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);
        JsonNode event = normalized.path("events").get(0);

        assertEquals(JSON.readTree("[\"销售订单\"]"), event.path("inputs"));
        assertEquals(JSON.readTree("[\"销售台账\"]"), event.path("outputs"));
        assertEquals(JSON.readTree("[\"entity:orders\"]"), event.path("inputEntityRefs"));
        assertEquals(JSON.readTree("[\"entity:ledger\"]"), event.path("outputEntityRefs"));
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void evidenceBackedIsolatedEntityIsReportedWithoutBreakingReferenceClosure() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {
                      "name": "账户余额视图",
                      "physicalName": "v_account_balance",
                      "type": "分析视图候选",
                      "evidenceRefs": ["metadata:account-balance"]
                    }
                  ],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("metadata:account-balance");
        bundle.withArray("tables").add("v_account_balance");

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(raw, bundle);

        assertEquals(1, normalized.path("validation").path("isolatedEntities").size());
        assertTrue(normalized.path("validation").path("unresolvedReferences").isEmpty());
        assertTrue(normalized.path("validation").path("missingEvidenceRefs").isEmpty());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void preservesExplicitEntityRefsWhenSameNamedBusinessEntitiesAreAmbiguous() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:sales-order","name":"订单","machineType":"BUSINESS_ENTITY","evidenceRefs":["e1"]},
                    {"id":"entity:service-order","name":"订单","machineType":"BUSINESS_ENTITY","evidenceRefs":["e2"]}
                  ],
                  "events": [],
                  "relations": [
                    {
                      "id":"relation:order-domains",
                      "from":"订单",
                      "to":"订单",
                      "fromEntityRef":"entity:sales-order",
                      "toEntityRef":"entity:service-order",
                      "type":"DOMAIN_RELATED",
                      "evidenceRefs":["e1","e2"]
                    }
                  ],
                  "lineage": [],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [],
                  "reviewItems": []
                }
                """);

        JsonNode normalized = new SemanticExtractionDocumentNormalizer().normalize(
                raw, evidenceBundle("e1", "e2"));

        assertEquals("entity:sales-order",
                normalized.path("relations").get(0).path("fromEntityRef").asText());
        assertEquals("entity:service-order",
                normalized.path("relations").get(0).path("toEntityRef").asText());
        assertTrue(normalized.path("validation").path("isRefClosed").asBoolean());
    }

    @Test
    void rejectsEveryUnresolvedDisplayInputEvenWhenAnotherInputResolved() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:orders","name":"订单","physicalName":"orders","evidenceRefs":["e1"]}
                  ],
                  "events": [{
                    "name":"处理订单","eventCandidateRef":"event:process-orders",
                    "inputs":["订单","不存在的客户"],
                    "outputEntityRefs":["entity:orders"],
                    "evidenceRefs":["event:process-orders"]
                  }],
                  "relations": [], "lineage": [], "metrics": [], "dimensions": [],
                  "triplets": [], "reviewItems": []
                }
                """);
        ObjectNode bundle = evidenceBundle("e1");
        bundle.withArray("eventCandidates").addObject().put("id", "event:process-orders");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, bundle));
    }

    @Test
    void explicitTypedEntityRefsCannotFallBackToResolvableDisplayNames() throws Exception {
        JsonNode raw = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:orders","name":"订单","physicalName":"orders","evidenceRefs":["e1"]},
                    {"id":"entity:customers","name":"客户","physicalName":"customers","evidenceRefs":["e1"]}
                  ],
                  "events": [],
                  "relations": [{
                    "from":"订单","to":"客户",
                    "fromEntityRef":"entity:missing","toEntityRef":"entity:customers",
                    "type":"CUSTOMER_ORDER","evidenceRefs":["e1"]
                  }],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """);

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw, evidenceBundle("e1")));
    }

    @Test
    void reviewTargetMustBelongToDeclaredSectionAndReasonDoesNotAffectDefaultId() throws Exception {
        JsonNode wrongSection = JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:orders","name":"订单","physicalName":"orders","evidenceRefs":["e1"]}
                  ],
                  "events": [],
                  "relations": [{"fromEntityRef":"entity:orders","toEntityRef":"entity:orders",
                    "type":"SELF","evidenceRefs":["e1"]}],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [],
                  "reviewItems": [{
                    "targetRef":"entity:orders","targetSection":"metrics",
                    "type":"REVIEW_NEEDED","reason":"确认订单","evidenceRefs":["e1"]
                  }]
                }
                """);
        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(wrongSection, evidenceBundle("e1")));

        JsonNode first = reviewDocument("第一次说明");
        JsonNode second = reviewDocument("修改后的说明");
        SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();
        String firstId = normalizer.normalize(first, evidenceBundle("e1"))
                .path("reviewItems").get(0).path("id").asText();
        String secondId = normalizer.normalize(second, evidenceBundle("e1"))
                .path("reviewItems").get(0).path("id").asText();
        assertEquals(firstId, secondId);
    }

    private JsonNode reviewDocument(String reason) throws Exception {
        return JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:orders","name":"订单","physicalName":"orders","evidenceRefs":["e1"]}
                  ],
                  "events": [],
                  "relations": [{"fromEntityRef":"entity:orders","toEntityRef":"entity:orders",
                    "type":"SELF","evidenceRefs":["e1"]}],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [],
                  "reviewItems": [{
                    "targetRef":"entity:orders","targetSection":"entities",
                    "type":"REVIEW_NEEDED","reason":"%s","evidenceRefs":["e1"]
                  }]
                }
                """.formatted(reason));
    }

    private String semanticDocument(boolean reversed) {
        String relations = reversed
                ? """
                  {"type":"供应关系","from":"订单","to":"供应商","evidenceRefs":["e2"]},
                  {"type":"客户关系","from":"订单","to":"客户","evidenceRefs":["e1"]}
                  """
                : """
                  {"type":"客户关系","from":"订单","to":"客户","evidenceRefs":["e1"]},
                  {"type":"供应关系","from":"订单","to":"供应商","evidenceRefs":["e2"]}
                  """;
        String lineage = reversed
                ? """
                  {"fromPhysical":["suppliers.id"],"toPhysical":"orders.supplier_id","transform":"DIRECT","evidenceRefs":["e2"]},
                  {"fromPhysical":["customers.id"],"toPhysical":"orders.customer_id","transform":"DIRECT","evidenceRefs":["e1"]}
                  """
                : """
                  {"fromPhysical":["customers.id"],"toPhysical":"orders.customer_id","transform":"DIRECT","evidenceRefs":["e1"]},
                  {"fromPhysical":["suppliers.id"],"toPhysical":"orders.supplier_id","transform":"DIRECT","evidenceRefs":["e2"]}
                  """;
        String triplets = reversed
                ? """
                  {"subject":"订单","predicate":"属于","object":"供应商","candidateRef":"triplet-candidate:second","evidenceRefs":["e2"]},
                  {"subject":"订单","predicate":"属于","object":"客户","candidateRef":"triplet-candidate:first","evidenceRefs":["e1"]}
                  """
                : """
                  {"subject":"订单","predicate":"属于","object":"客户","candidateRef":"triplet-candidate:first","evidenceRefs":["e1"]},
                  {"subject":"订单","predicate":"属于","object":"供应商","candidateRef":"triplet-candidate:second","evidenceRefs":["e2"]}
                  """;
        String reviews = reversed
                ? """
                  {"targetRef":"entity:suppliers","targetSection":"entities","type":"REVIEW_NEEDED","reason":"确认供应商","evidenceRefs":["e2"]},
                  {"targetRef":"entity:customers","targetSection":"entities","type":"REVIEW_NEEDED","reason":"确认客户","evidenceRefs":["e1"]}
                  """
                : """
                  {"targetRef":"entity:customers","targetSection":"entities","type":"REVIEW_NEEDED","reason":"确认客户","evidenceRefs":["e1"]},
                  {"targetRef":"entity:suppliers","targetSection":"entities","type":"REVIEW_NEEDED","reason":"确认供应商","evidenceRefs":["e2"]}
                  """;
        return """
                {
                  "entities": [
                    {"id":"entity:orders","name":"订单","physicalName":"orders","evidenceRefs":["e1"]},
                    {"id":"entity:customers","name":"客户","physicalName":"customers","evidenceRefs":["e1"]},
                    {"id":"entity:suppliers","name":"供应商","physicalName":"suppliers","evidenceRefs":["e2"]}
                  ],
                  "events": [],
                  "relations": [%s],
                  "lineage": [%s],
                  "metrics": [],
                  "dimensions": [],
                  "triplets": [%s],
                  "reviewItems": [%s]
                }
                """.formatted(relations, lineage, triplets, reviews);
    }

    private Set<String> sectionIds(JsonNode document, String section) {
        Set<String> ids = new LinkedHashSet<>();
        document.path(section).forEach(item -> ids.add(item.path("id").asText()));
        return ids;
    }

    private JsonNode businessEntityDocument(String ownedGroundingRefs) throws Exception {
        return JSON.readTree("""
                {
                  "entities": [
                    {
                      "name":"订单","machineType":"BUSINESS_ENTITY",
                      "ownedGroundingRefs":%s,"evidenceRefs":["e1"]
                    }
                  ],
                  "events": [],
                  "relations": [
                    {"from":"订单","to":"订单","type":"SELF","evidenceRefs":["e1"]}
                  ],
                  "lineage": [], "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """.formatted(ownedGroundingRefs));
    }

    private ObjectNode evidenceBundle(String... evidenceIds) {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("metadataTables");
        root.putArray("metadataColumns");
        root.putArray("metadataConstraints");
        root.putArray("metadataIndexes");
        var evidence = root.putArray("evidence");
        for (String id : evidenceIds) {
            evidence.addObject().put("id", id).put("type", "TEST").put("source", "test").put("detail", "test");
        }
        root.putArray("relationships");
        ObjectNode knownLineage = root.putArray("lineage").addObject();
        knownLineage.put("id", "physical:test-known");
        knownLineage.putArray("sources")
                .add("sales_orders.id")
                .add("sales_order_items.amount")
                .add("customers.id")
                .add("suppliers.id")
                .add("orders.customer_id")
                .add("orders.supplier_id")
                .add("sales_fact.order_id")
                .add("sales_fact.gross_margin_rate");
        knownLineage.put("target", "sales_fact.sales_amount");
        root.putArray("derivedRelationships");
        root.putArray("derivedLineage");
        root.putArray("namingEvidence");
        root.putArray("diagnostics");
        root.putArray("eventCandidates");
        root.putArray("tripletCandidates");
        root.putArray("reviewItemCandidates");
        root.putArray("tables")
                .add("orders")
                .add("customers")
                .add("suppliers")
                .add("sales_fact")
                .add("sales_orders")
                .add("sales_order_items");
        return root;
    }
}
