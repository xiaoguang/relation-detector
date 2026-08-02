package com.relationdetector.semantic.extraction.prompt;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPromptBuilder;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SemanticExtractionPromptBuilderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void rendersEvidenceBundleAsCompactJsonWithoutChangingItsContent() {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.putObject("database").put("type", "MYSQL");
        bundle.putArray("items").addObject().put("id", "fact-1");

        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(bundle);

        assertTrue(prompt.userPrompt().contains(
                "{\"database\":{\"type\":\"MYSQL\"},\"items\":[{\"id\":\"fact-1\"}]}"));
    }

    @Test
    void modelProjectionLeavesDeterministicTripletMaterializationToCoreBackfill() {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.putObject("database").put("type", "MYSQL");
        bundle.withArray("tripletCandidates").addObject()
                .put("id", "triplet-candidate:orders")
                .put("subject", "orders.customer_id")
                .put("predicate", "references")
                .put("object", "customers.id");
        bundle.withArray("eventCandidates").addObject()
                .put("id", "event-candidate:create-order");
        bundle.putObject("shardContext").withArray("ownedCandidateRefs")
                .add("triplet-candidate:orders")
                .add("event-candidate:create-order");

        SemanticExtractionPrompt prompt = new SemanticExtractionPromptBuilder().build(bundle);

        assertTrue(prompt.evidenceBundle().path("tripletCandidates").size() == 1,
                "the complete bundle remains available to deterministic backfill and closure validation");
        assertTrue(prompt.userPrompt().contains("event-candidate:create-order"));
        assertFalse(prompt.userPrompt().contains("triplet-candidate:orders"));
        assertFalse(prompt.developerPrompt().contains("Do not omit tripletCandidates"));
        assertTrue(prompt.developerPrompt().contains("deterministically backfills"));
        assertTrue(prompt.developerPrompt().contains(
                "Leave event inputs, outputs, inputEntityRefs, and outputEntityRefs empty"));
        assertTrue(prompt.developerPrompt().contains(
                "The core rebuilds those"));
        assertTrue(prompt.developerPrompt().contains(
                "deterministic endpoint fields from the owned eventCandidate"));
        assertTrue(prompt.developerPrompt().contains(
                "Set semanticGraph and validation to null"));
        assertTrue(prompt.developerPrompt().contains(
                "The core rebuilds both after deterministic backfill"));
    }
}
