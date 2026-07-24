package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticShardOutputOwnershipValidatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void acceptsObjectsGroundedByAnOwnedFactOrCandidate() {
        SemanticShard shard = shard(Set.of("rel-owned"), Set.of("trip-owned"), Set.of("rel-overlap"));
        ObjectNode output = emptyDocument();
        ObjectNode entity = output.withArray("entities").addObject()
                .put("name", "订单")
                .put("physicalName", "shop.orders");
        entity.putArray("ownedGroundingRefs").add("rel-owned");
        entity.putArray("evidenceRefs").add("rel-owned");
        ObjectNode triplet = output.withArray("triplets").addObject()
                .put("subject", "订单")
                .put("predicate", "属于")
                .put("object", "客户")
                .put("candidateRef", "trip-owned");
        triplet.putArray("ownedGroundingRefs").add("trip-owned");
        triplet.putArray("evidenceRefs").add("rel-owned");

        assertDoesNotThrow(() -> new SemanticShardOutputOwnershipValidator().validate(output, shard));
    }

    @Test
    void rejectsOwnedFactMentionedOnlyAsAuditEvidence() {
        SemanticShard shard = shard(Set.of("rel-owned"), Set.of(), Set.of());
        ObjectNode output = emptyDocument();
        output.withArray("entities").addObject()
                .put("name", "订单")
                .put("physicalName", "shop.orders")
                .putArray("evidenceRefs").add("rel-owned");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticShardOutputOwnershipValidator().validate(output, shard));
    }

    @Test
    void rejectsAnObjectGroundedOnlyByOverlapEvidence() {
        SemanticShard shard = shard(Set.of("rel-owned"), Set.of(), Set.of("rel-overlap"));
        ObjectNode output = emptyDocument();
        ObjectNode entity = output.withArray("entities").addObject()
                .put("name", "客户")
                .put("physicalName", "shop.customers");
        entity.putArray("ownedGroundingRefs").add("rel-overlap");
        entity.putArray("evidenceRefs").add("rel-overlap");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticShardOutputOwnershipValidator().validate(output, shard));
    }

    @Test
    void rejectsDirectReferencesOwnedByAnotherShard() {
        SemanticShard shard = shard(Set.of("rel-owned"), Set.of(), Set.of());
        ObjectNode output = emptyDocument();
        ObjectNode event = output.withArray("events").addObject()
                .put("name", "订单写入")
                .put("eventCandidateRef", "event-other");
        event.putArray("ownedGroundingRefs").add("rel-owned");
        event.putArray("evidenceRefs").add("rel-owned");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticShardOutputOwnershipValidator().validate(output, shard));
    }

    private SemanticShard shard(Set<String> ownedFacts, Set<String> ownedCandidates, Set<String> overlap) {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.putArray("tables");
        bundle.putArray("evidence");
        for (String section : SemanticShardBundleIndex.ITEM_SECTIONS) {
            bundle.putArray(section);
        }
        return new SemanticShard("shard-0001", "shop.orders", bundle,
                ownedFacts, ownedCandidates, overlap, 100);
    }

    private ObjectNode emptyDocument() {
        ObjectNode document = JSON.createObjectNode();
        for (String section : SemanticShardOutputOwnershipValidator.OUTPUT_SECTIONS) {
            document.putArray(section);
        }
        return document;
    }
}
