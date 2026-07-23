package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void requiresEvidenceBundleForFormalNormalization() throws Exception {
        JsonNode raw = JSON.readTree("""
                {"entities": [], "events": [], "relations": [], "lineage": [], "metrics": [],
                 "dimensions": [], "triplets": [], "reviewItems": []}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(raw));
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

        assertEquals("entity:sales_fact", normalized.path("entities").get(0).path("id").asText());
        assertEquals("event:event-candidate_routine_erp.sp_rebuild_sales_fact",
                normalized.path("events").get(0).path("id").asText());
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

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionDocumentNormalizer().normalize(
                        raw, evidenceBundle("sales_orders.id -> sales_fact.order_id")));
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

        assertEquals("event:event-candidate_routine_function_public.refresh_sales-bigint",
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
                    {"name":"订单","physicalName":"orders","evidenceRefs":["e1"]},
                    {"name":"客户","physicalName":"customers","evidenceRefs":["e1"]},
                    {"name":"供应商","physicalName":"suppliers","evidenceRefs":["e2"]}
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

    private ObjectNode evidenceBundle(String... evidenceIds) {
        ObjectNode root = JSON.createObjectNode();
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
