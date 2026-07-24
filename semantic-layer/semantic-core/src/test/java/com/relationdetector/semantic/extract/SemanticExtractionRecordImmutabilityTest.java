package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticExtractionRecordImmutabilityTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void semanticShardDetachesBundleAndExposesImmutableSets() {
        assertJsonStateIsDetached(
                source -> new SemanticShard(
                        "shard-0001",
                        "shop.orders",
                        source,
                        Set.of("fact-1"),
                        Set.of("candidate-1"),
                        Set.of("overlap-1"),
                        100),
                SemanticShard::bundle);

        SemanticShard shard = shard(nestedDocument());
        assertThrows(UnsupportedOperationException.class, () -> shard.ownedFactRefs().add("fact-2"));
        assertThrows(UnsupportedOperationException.class, () -> shard.ownedCandidateRefs().add("candidate-2"));
        assertThrows(UnsupportedOperationException.class, () -> shard.overlapRefs().add("overlap-2"));
    }

    @Test
    void semanticExtractionPromptDetachesEvidenceBundle() {
        assertJsonStateIsDetached(
                source -> new SemanticExtractionPrompt("developer", "user", source),
                SemanticExtractionPrompt::evidenceBundle);
    }

    @Test
    void semanticExtractionRunPlanDetachesFullBundleAndExposesImmutableRequests() {
        assertJsonStateIsDetached(
                source -> runPlan(source),
                SemanticExtractionRunPlan::fullBundle);

        SemanticExtractionRunPlan plan = runPlan(nestedDocument());
        assertThrows(UnsupportedOperationException.class, () -> plan.shardRequests().add(request(nestedDocument())));
    }

    @Test
    void semanticExtractionRunResultDetachesEveryJsonValueAndExposesImmutableExecutions() {
        assertJsonStateIsDetached(
                source -> runResult(source, nestedDocument(), nestedDocument()),
                SemanticExtractionRunResult::reconciliationPatch);
        assertJsonStateIsDetached(
                source -> runResult(nestedDocument(), source, nestedDocument()),
                SemanticExtractionRunResult::mergedDraft);
        assertJsonStateIsDetached(
                source -> runResult(nestedDocument(), nestedDocument(), source),
                SemanticExtractionRunResult::finalDocument);

        SemanticExtractionRunResult result = runResult(
                nestedDocument(), nestedDocument(), nestedDocument());
        assertThrows(UnsupportedOperationException.class,
                () -> result.shardExecutions().add(execution(nestedDocument())));
    }

    @Test
    void semanticShardNormalizedResultDetachesDocument() {
        assertJsonStateIsDetached(
                source -> new SemanticShardNormalizedResult("shard-0001", source),
                SemanticShardNormalizedResult::document);
    }

    @Test
    void semanticShardExecutionDetachesNormalizedDocument() {
        assertJsonStateIsDetached(
                source -> execution(source),
                SemanticShardExecution::normalizedDocument);
    }

    @Test
    void semanticShardVariantDetachesDocument() {
        assertJsonStateIsDetached(
                source -> new SemanticShardVariant("shard-0001", "hash", source),
                SemanticShardVariant::document);
    }

    @Test
    void semanticShardMergeResultDetachesDocumentAndExposesImmutableConflicts() {
        assertJsonStateIsDetached(
                source -> new SemanticShardMergeResult(source, List.of()),
                SemanticShardMergeResult::mergedDocument);

        SemanticShardMergeResult result = mergeResult();
        assertThrows(UnsupportedOperationException.class,
                () -> result.conflicts().add(new SemanticShardConflict("entities", "entity-1", List.of())));
    }

    @Test
    void semanticExtractionResultDetachesResponse() {
        assertJsonStateIsDetached(
                source -> new SemanticExtractionResult("{}", "{}", "output", source),
                SemanticExtractionResult::response);
    }

    private <T> void assertJsonStateIsDetached(
            Function<ObjectNode, T> factory,
            Function<T, ? extends JsonNode> accessor
    ) {
        ObjectNode source = nestedDocument();
        T value = factory.apply(source);

        source.withObject("nested").put("value", "changed through constructor input");
        assertEquals("original", accessor.apply(value).path("nested").path("value").asText());

        JsonNode exposed = accessor.apply(value);
        ((ObjectNode) exposed.path("nested")).put("value", "changed through accessor");
        assertEquals("original", accessor.apply(value).path("nested").path("value").asText());
    }

    private SemanticExtractionRunPlan runPlan(ObjectNode fullBundle) {
        SemanticShard shard = shard(nestedDocument());
        return new SemanticExtractionRunPlan(
                fullBundle,
                new SemanticShardPlan("bundle-hash", List.of(shard), Map.of(), Map.of()),
                List.of(new SemanticShardRequest(
                        shard,
                        new SemanticExtractionPrompt("developer", "user", nestedDocument()))),
                true);
    }

    private SemanticExtractionRunResult runResult(
            JsonNode reconciliationPatch,
            ObjectNode mergedDraft,
            ObjectNode finalDocument
    ) {
        return new SemanticExtractionRunResult(
                runPlan(nestedDocument()),
                List.of(execution(nestedDocument())),
                mergeResult(),
                null,
                null,
                reconciliationPatch,
                mergedDraft,
                finalDocument);
    }

    private SemanticShardExecution execution(ObjectNode normalizedDocument) {
        return new SemanticShardExecution(
                request(nestedDocument()),
                new SemanticExtractionResult("{}", "{}", "output", nestedDocument()),
                normalizedDocument);
    }

    private SemanticShardRequest request(ObjectNode bundle) {
        return new SemanticShardRequest(
                shard(bundle),
                new SemanticExtractionPrompt("developer", "user", bundle));
    }

    private SemanticShard shard(ObjectNode bundle) {
        return new SemanticShard(
                "shard-0001",
                "shop.orders",
                bundle,
                Set.of("fact-1"),
                Set.of("candidate-1"),
                Set.of("overlap-1"),
                100);
    }

    private SemanticShardMergeResult mergeResult() {
        return new SemanticShardMergeResult(nestedDocument(), List.of());
    }

    private ObjectNode nestedDocument() {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("nested").put("value", "original");
        return root;
    }
}
