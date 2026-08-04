package com.relationdetector.semantic.extraction.normalization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.extraction.model.SemanticExtractionDocument;

final class SemanticFormalIdentityCanonicalizerTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticExtractionDocumentCodec codec = new SemanticExtractionDocumentCodec();
    private final SemanticFormalIdentityCanonicalizer canonicalizer =
            new SemanticFormalIdentityCanonicalizer();

    @Test
    void explicitAndNullModelIdsProduceTheSameFormalDocument() throws Exception {
        ObjectNode explicit = document("\"entity:model\"", "\"event:model\"");
        ObjectNode omitted = document("null", "null");

        assertEquals(canonicalize(omitted), canonicalize(explicit));
    }

    @Test
    void rewritesSectionScopedAliasesBeforeCanonicalizingDependentSections() throws Exception {
        SemanticExtractionDocument source = codec.read(JSON.readTree("""
                {
                  "entities": [
                    {"id":"shared","name":"订单","physicalName":"sales.orders","evidenceRefs":["e1"]},
                    {"id":"entity:customer-model","name":"客户","physicalName":"sales.customers","evidenceRefs":["e2"]}
                  ],
                  "events": [{
                    "id":"shared","name":"创建订单","eventCandidateRef":"event-candidate:routine:erp.create/order",
                    "inputEntityRefs":["entity:customer-model"],"outputEntityRefs":["shared"],
                    "evidenceRefs":["event-candidate:routine:erp.create/order"]
                  }],
                  "relations": [{
                    "id":"relation:model","fromEntityRef":"entity:customer-model","toEntityRef":"shared",
                    "type":"CUSTOMER_ORDER","evidenceRefs":["e1"]
                  }],
                  "lineage": [{
                    "id":"lineage:model","fromPhysical":["sales.customers.id"],
                    "toPhysical":"sales.orders.customer_id","transform":"DIRECT",
                    "sourceEntityRefs":["entity:customer-model"],"targetEntityRef":"shared","evidenceRefs":["e1"]
                  }],
                  "metrics": [{
                    "id":"metric:model","name":"订单金额","physicalField":"sales.orders.total",
                    "sourceFields":["sales.orders.total"],"ownerEntityRef":"shared",
                    "sourceEntityRefs":["shared"],"evidenceRefs":["e1"]
                  }],
                  "dimensions": [{
                    "id":"dimension:model","name":"客户","physicalField":"sales.orders.customer_id",
                    "dimensionTable":"sales.customers","ownerEntityRef":"shared",
                    "dimensionEntityRef":"entity:customer-model","evidenceRefs":["e1"]
                  }],
                  "triplets": [{
                    "id":"triplet:model","candidateRef":"triplet-candidate:relation/customer-order",
                    "subjectRef":"entity:customer-model","objectRef":"shared","predicate":"places",
                    "evidenceRefs":["e1"]
                  }],
                  "reviewItems": [{
                    "id":"review:model","targetRef":"relation:model","targetSection":"relations",
                    "type":"REVIEW_NEEDED","evidenceRefs":["e1"]
                  }]
                }
                """));

        JsonNode result = codec.write(canonicalizer.canonicalize(source));
        String orderId = SemanticCanonicalIdentity.entity(
                "sales.orders", "订单", null, null, List.of()).canonicalId();
        String customerId = SemanticCanonicalIdentity.entity(
                "sales.customers", "客户", null, null, List.of()).canonicalId();
        String eventId = SemanticCanonicalIdentity.event("event-candidate:routine:erp.create/order");
        String relationId = SemanticCanonicalIdentity.relation(
                customerId, orderId, null, "CUSTOMER_ORDER");
        String tripletId = SemanticCanonicalIdentity.triplet(
                "triplet-candidate:relation/customer-order");

        assertEquals(orderId, itemByField(
                result.path("entities"), "physicalName", "sales.orders").path("id").asText());
        assertEquals(eventId, result.path("events").get(0).path("id").asText());
        assertEquals(customerId,
                result.path("events").get(0).path("inputEntityRefs").get(0).asText());
        assertEquals(orderId,
                result.path("events").get(0).path("outputEntityRefs").get(0).asText());
        assertEquals(relationId, result.path("relations").get(0).path("id").asText());
        assertEquals(customerId,
                result.path("lineage").get(0).path("sourceEntityRefs").get(0).asText());
        assertEquals(orderId,
                result.path("lineage").get(0).path("targetEntityRef").asText());
        assertEquals(orderId,
                result.path("metrics").get(0).path("ownerEntityRef").asText());
        assertEquals(customerId,
                result.path("dimensions").get(0).path("dimensionEntityRef").asText());
        assertEquals(tripletId, result.path("triplets").get(0).path("id").asText());
        assertEquals(relationId,
                result.path("reviewItems").get(0).path("targetRef").asText());
        assertEquals(SemanticCanonicalIdentity.review(
                        relationId, "relations", "REVIEW_NEEDED"),
                result.path("reviewItems").get(0).path("id").asText());
        assertFalse(result.toString().contains("\"relation:model\""));
        assertFalse(result.toString().contains("\"triplet:model\""));
        assertFalse(result.toString().contains("\"metric:model\""));
        assertFalse(result.toString().contains("\"dimension:model\""));
        assertFalse(result.toString().contains("\"review:model\""));
    }

    @Test
    void arrayAndAliasOrderDoNotChangeCanonicalIds() throws Exception {
        SemanticExtractionDocument first = codec.read(JSON.readTree("""
                {
                  "entities": [], "events": [], "relations": [],
                  "lineage": [{
                    "id":"lineage:first",
                    "fromPhysical":["sales.items.amount","sales.orders.total","sales.items.amount"],
                    "toPhysical":"sales.facts.amount","transform":"SUM","evidenceRefs":["e2","e1"]
                  }],
                  "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """));
        SemanticExtractionDocument reordered = codec.read(JSON.readTree("""
                {
                  "entities": [], "events": [], "relations": [],
                  "lineage": [{
                    "id":"lineage:second",
                    "fromPhysical":["sales.orders.total","sales.items.amount"],
                    "toPhysical":"sales.facts.amount","transform":"SUM","evidenceRefs":["e1","e2"]
                  }],
                  "metrics": [], "dimensions": [], "triplets": [], "reviewItems": []
                }
                """));

        JsonNode firstResult = codec.write(canonicalizer.canonicalize(first));
        JsonNode reorderedResult = codec.write(canonicalizer.canonicalize(reordered));

        assertEquals(firstResult.path("lineage").get(0).path("id"),
                reorderedResult.path("lineage").get(0).path("id"));
        assertEquals(firstResult.path("lineage").get(0).path("fromPhysical"),
                reorderedResult.path("lineage").get(0).path("fromPhysical"));
    }

    @Test
    void differentAliasesWithIdenticalCanonicalContentDeduplicate() throws Exception {
        SemanticExtractionDocument source = codec.read(JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:first","name":"订单","physicalName":"sales.orders","evidenceRefs":["e1"]},
                    {"id":"entity:second","name":"订单","physicalName":"sales.orders","evidenceRefs":["e1"]}
                  ],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """));

        JsonNode result = codec.write(canonicalizer.canonicalize(source));

        assertEquals(1, result.path("entities").size());
        assertEquals(SemanticCanonicalIdentity.entity(
                        "sales.orders", "订单", null, null, List.of()).canonicalId(),
                result.path("entities").get(0).path("id").asText());
    }

    @Test
    void oneAliasCannotResolveToDifferentCanonicalContent() throws Exception {
        SemanticExtractionDocument source = codec.read(JSON.readTree("""
                {
                  "entities": [
                    {"id":"entity:model","name":"订单","physicalName":"sales.orders","evidenceRefs":["e1"]},
                    {"id":"entity:model","name":"客户","physicalName":"sales.customers","evidenceRefs":["e2"]}
                  ],
                  "events": [], "relations": [], "lineage": [], "metrics": [],
                  "dimensions": [], "triplets": [], "reviewItems": []
                }
                """));

        assertThrows(SemanticExtractionValidationException.class,
                () -> canonicalizer.canonicalize(source));
        assertEquals("entity:model", source.entities.get(0).id);
        assertEquals("entity:model", source.entities.get(1).id);
    }

    @Test
    void oneCanonicalIdCannotHideConflictingFormalContent() throws Exception {
        SemanticExtractionDocument source = codec.read(JSON.readTree("""
                {
                  "entities": [],
                  "events": [
                    {"id":"event:first","name":"创建订单","eventCandidateRef":"event-candidate:orders","evidenceRefs":["e1"]},
                    {"id":"event:second","name":"删除订单","eventCandidateRef":"event-candidate:orders","evidenceRefs":["e1"]}
                  ],
                  "relations": [], "lineage": [], "metrics": [], "dimensions": [],
                  "triplets": [], "reviewItems": []
                }
                """));

        assertThrows(SemanticExtractionValidationException.class,
                () -> canonicalizer.canonicalize(source));
    }

    private ObjectNode canonicalize(ObjectNode source) {
        return codec.write(canonicalizer.canonicalize(codec.read(source)));
    }

    private JsonNode itemByField(JsonNode items, String field, String value) {
        for (JsonNode item : items) {
            if (value.equals(item.path(field).asText())) {
                return item;
            }
        }
        throw new AssertionError("missing semantic item " + field + "=" + value);
    }

    private ObjectNode document(String entityId, String eventId) throws Exception {
        return (ObjectNode) JSON.readTree("""
                {
                  "entities": [{
                    "id":%s,"name":"订单","physicalName":"sales.orders","evidenceRefs":["e1"]
                  }],
                  "events": [{
                    "id":%s,"name":"创建订单","eventCandidateRef":"event-candidate:orders",
                    "evidenceRefs":["event-candidate:orders"]
                  }],
                  "relations": [], "lineage": [], "metrics": [], "dimensions": [],
                  "triplets": [], "reviewItems": []
                }
                """.formatted(entityId, eventId));
    }
}
