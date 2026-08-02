package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.StableSemanticId;

final class SemanticShardResultMergerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void exactDuplicateItemsAreIdempotent() {
        ObjectNode left = documentWithEntity("entity:orders", "订单");
        ObjectNode right = documentWithEntity("entity:orders", "订单");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)));

        assertEquals(1, result.mergedDocument().path("entities").size());
        assertEquals(0, result.conflicts().size());
    }

    @Test
    void conflictingStableIdIsNotSilentlyOverwritten() {
        ObjectNode left = documentWithEntity("entity:orders", "订单");
        ObjectNode right = documentWithEntity("entity:orders", "销售单");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)));

        assertEquals(1, result.conflicts().size());
        assertEquals("entity:orders", result.conflicts().get(0).id());
        assertThrows(SemanticExtractionValidationException.class, result::requireConflictFree);
    }

    @Test
    void canonicalizesTheSamePhysicalEntityAndRewritesReferences() {
        ObjectNode left = documentWithEntity("model:orders:left", "订单", "shop.orders", "rel-left");
        ObjectNode event = left.withArray("events").addObject();
        event.put("id", "event:left");
        event.put("name", "订单创建");
        event.putArray("inputEntityRefs").add("model:orders:left");
        event.putArray("evidenceRefs").add("rel-left");
        ObjectNode right = documentWithEntity("model:orders:right", "销售订单", "shop.orders", "rel-right");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)),
                plan(Map.of("shard-0001", Set.of("rel-left"), "shard-0002", Set.of("rel-right"))));

        assertEquals(1, result.mergedDocument().path("entities").size());
        ObjectNode canonicalEntity = (ObjectNode) result.mergedDocument().path("entities").get(0);
        String canonicalId = canonicalEntity.path("id").asText();
        assertEquals(canonicalId,
                result.mergedDocument().path("events").get(0).path("inputEntityRefs").get(0).asText());
        Set<String> grounding = new LinkedHashSet<>();
        canonicalEntity.path("ownedGroundingRefs").forEach(value -> grounding.add(value.asText()));
        assertEquals(Set.of("rel-left", "rel-right"), grounding);
    }

    @Test
    void distinctPhysicalNamesWithTheSameReadableSlugKeepDistinctIds() {
        ObjectNode left = documentWithEntity(
                "model:sales-order:space", "销售订单", "shop.sales order", "rel-space");
        ObjectNode right = documentWithEntity(
                "model:sales-order:underscore", "销售订单", "shop.sales_order", "rel-underscore");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)),
                plan(Map.of(
                        "shard-0001", Set.of("rel-space"),
                        "shard-0002", Set.of("rel-underscore"))));

        assertEquals(2, result.mergedDocument().path("entities").size());
        assertEquals(2, result.mergedDocument().path("entities").findValuesAsText("id").stream()
                .distinct()
                .count());
    }

    @Test
    void mergesPureBusinessEntitiesWithTheSameOwnedGrounding() {
        ObjectNode document = emptyDocument();
        addBusinessEntity(document, "model:order:one", "订单", "BUSINESS_ENTITY", "rel-orders");
        addBusinessEntity(document, "model:order:two", " 订单 ", "BUSINESS_ENTITY", "rel-orders");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", document)),
                plan(Map.of("shard-0001", Set.of("rel-orders"))));

        assertEquals(1, result.mergedDocument().path("entities").size());
    }

    @Test
    void keepsSameNamedBusinessEntitiesWithDifferentGroundingAndCreatesReviews() {
        ObjectNode left = emptyDocument();
        addBusinessEntity(left, "model:order:sales", "订单", "BUSINESS_ENTITY", "rel-sales");
        ObjectNode right = emptyDocument();
        addBusinessEntity(right, "model:order:service", "订单", "BUSINESS_ENTITY", "rel-service");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)),
                plan(Map.of("shard-0001", Set.of("rel-sales"), "shard-0002", Set.of("rel-service"))));

        assertEquals(2, result.mergedDocument().path("entities").size());
        assertEquals(2, result.mergedDocument().path("reviewItems").size());
        assertTrue(result.mergedDocument().path("reviewItems").findValuesAsText("type").stream()
                .allMatch("POTENTIAL_SEMANTIC_DUPLICATE"::equals));
    }

    @Test
    void rejectsPureBusinessEntityWithoutOwnedGrounding() {
        ObjectNode document = emptyDocument();
        addBusinessEntity(document, "model:order", "订单", "BUSINESS_ENTITY", "raw-evidence");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticShardResultMerger().merge(List.of(
                        new SemanticShardNormalizedResult("shard-0001", document)),
                        plan(Map.of("shard-0001", Set.of("rel-orders")))));
    }

    @Test
    void surfacesIncompatibleTypesForTheSamePhysicalEntityAsAReconciliationConflict() {
        ObjectNode left = documentWithEntity("model:orders:left", "订单", "shop.orders", "rel-left");
        ObjectNode right = documentWithEntity("model:orders:right", "订单", "shop.orders", "rel-right");
        ((ObjectNode) right.withArray("entities").get(0)).put("machineType", "INCOMPATIBLE");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", left),
                new SemanticShardNormalizedResult("shard-0002", right)),
                plan(Map.of("shard-0001", Set.of("rel-left"), "shard-0002", Set.of("rel-right"))));

        assertEquals(1, result.mergedDocument().path("entities").size());
        assertEquals(1, result.conflicts().size());
        assertEquals("entities", result.conflicts().get(0).section());
        assertEquals(
                StableSemanticId.of("entity-physical", "shop.orders"),
                result.conflicts().get(0).id());
        assertEquals(2, result.conflicts().get(0).variants().size());
    }

    @Test
    void replacesGeneratedPhysicalPlaceholderWithEvidenceGroundedSemanticType() {
        ObjectNode enriched = documentWithEntity(
                "model:orders", "订单", "shop.orders", "rel-orders");
        ObjectNode placeholder = documentWithEntity(
                "generated:orders", "shop.orders", "shop.orders", "triplet-orders");
        ObjectNode generated = (ObjectNode) placeholder.withArray("entities").get(0);
        generated.put("type", "物理实体候选");
        generated.put("machineType", "PHYSICAL_ENTITY");

        SemanticShardMergeResult result = new SemanticShardResultMerger().merge(List.of(
                new SemanticShardNormalizedResult("shard-0001", enriched),
                new SemanticShardNormalizedResult("shard-0002", placeholder)),
                plan(Map.of(
                        "shard-0001", Set.of("rel-orders"),
                        "shard-0002", Set.of("triplet-orders"))));

        assertEquals(1, result.mergedDocument().path("entities").size());
        ObjectNode entity = (ObjectNode) result.mergedDocument().path("entities").get(0);
        assertEquals("BUSINESS_ENTITY", entity.path("machineType").asText());
        assertEquals(Set.of("rel-orders", "triplet-orders"), textSet(entity.path("evidenceRefs")));
    }

    private ObjectNode documentWithEntity(String id, String name) {
        return documentWithEntity(id, name, "shop.orders", "candidate:orders");
    }

    private ObjectNode documentWithEntity(
            String id,
            String name,
            String physicalName,
            String evidenceRef
    ) {
        ObjectNode root = emptyDocument();
        ObjectNode entity = root.putArray("entities").addObject()
                .put("id", id)
                .put("name", name)
                .put("type", "业务实体")
                .put("machineType", "BUSINESS_ENTITY")
                .put("physicalName", physicalName);
        entity.putArray("ownedGroundingRefs").add(evidenceRef);
        entity.putArray("evidenceRefs").add(evidenceRef);
        return root;
    }

    private void addBusinessEntity(
            ObjectNode document,
            String id,
            String name,
            String machineType,
            String evidenceRef
    ) {
        ObjectNode entity = document.withArray("entities").addObject()
                .put("id", id)
                .put("name", name)
                .put("type", "业务实体")
                .put("machineType", machineType);
        entity.putArray("ownedGroundingRefs").add(evidenceRef);
        entity.putArray("evidenceRefs").add(evidenceRef);
    }

    private ObjectNode emptyDocument() {
        ObjectNode root = JSON.createObjectNode();
        for (String section : List.of(
                "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems")) {
            root.putArray(section);
        }
        return root;
    }

    private SemanticShardPlan plan(Map<String, Set<String>> factsByShard) {
        List<SemanticShard> shards = factsByShard.entrySet().stream()
                .map(entry -> new SemanticShard(entry.getKey(), entry.getKey(), emptyBundle(),
                        entry.getValue(), Set.of(), Set.of(), 100))
                .toList();
        Map<String, String> owners = new LinkedHashMap<>();
        factsByShard.forEach((shard, facts) -> facts.forEach(fact -> owners.put(fact, shard)));
        return new SemanticShardPlan("bundle-hash", shards, owners, Map.of());
    }

    private ObjectNode emptyBundle() {
        ObjectNode root = JSON.createObjectNode();
        root.putArray("tables");
        root.putArray("evidence");
        for (String section : SemanticShardBundleIndex.ITEM_SECTIONS) {
            root.putArray(section);
        }
        return root;
    }

    private Set<String> textSet(com.fasterxml.jackson.databind.JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }
}
