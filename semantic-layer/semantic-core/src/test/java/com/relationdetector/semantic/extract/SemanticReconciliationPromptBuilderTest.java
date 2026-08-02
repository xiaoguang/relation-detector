package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ExternalJsonRecordStore;

class SemanticReconciliationPromptBuilderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void sendsOnlyConflictClosureInsteadOfTheCompleteMergedSummary() {
        ObjectNode merged = emptyDocument();
        merged.withArray("entities").addObject()
                .put("id", "entity:unrelated")
                .put("name", "Unrelated payload that must not enter reconciliation");
        ObjectNode variant = JSON.createObjectNode()
                .put("id", "entity:orders")
                .put("name", "Orders")
                .put("machineType", "BUSINESS_ENTITY");
        SemanticShardConflict conflict = new SemanticShardConflict(
                "entities",
                "entity:orders",
                List.of(new SemanticShardVariant("shard-0001", "variant:orders", variant)));
        SemanticShard shard = new SemanticShard(
                "shard-0001", "orders", emptyBundle(), Set.of(), Set.of(), Set.of(), 100);
        SemanticShardPlan plan = new SemanticShardPlan(
                "bundle-hash", List.of(shard), Map.of(), Map.of());

        SemanticExtractionPrompt prompt = new SemanticReconciliationPromptBuilder().build(
                new SemanticShardMergeResult(merged, List.of(conflict)), plan);

        assertFalse(prompt.evidenceBundle().has("semanticSummary"));
        assertTrue(prompt.evidenceBundle().path("conflicts").isArray());
        assertTrue(prompt.userPrompt().contains("variant:orders"));
        assertFalse(prompt.userPrompt().contains("Unrelated payload"));
    }

    @Test
    void pathBackedPromptSendsOnlyConflictClosure(@TempDir java.nio.file.Path workspace) {
        Map<SemanticPathResultStore.Section, ExternalJsonRecordStore> stores = new LinkedHashMap<>();
        try {
            for (SemanticPathResultStore.Section section : SemanticPathResultStore.Section.values()) {
                stores.put(section, new ExternalJsonRecordStore(workspace.resolve(section.wireName)));
            }
            stores.get(SemanticPathResultStore.Section.ENTITIES).append(
                    "entity:unrelated",
                    JSON.createObjectNode()
                            .put("id", "entity:unrelated")
                            .put("name", "Unrelated payload that must not enter reconciliation"));
            ObjectNode conflict = JSON.createObjectNode().put("id", "entity:orders");
            conflict.putArray("__semanticVariants")
                    .addObject()
                    .put("hash", "variant:orders")
                    .set("document", JSON.createObjectNode()
                            .put("id", "entity:orders")
                            .put("name", "Orders")
                            .put("machineType", "BUSINESS_ENTITY"));
            stores.get(SemanticPathResultStore.Section.ENTITIES).append("entity:orders", conflict);
            stores.values().forEach(ExternalJsonRecordStore::finish);
            SemanticPathShard shard = new SemanticPathShard(
                    "shard-0001", "orders", workspace.resolve("shard.json"), 100, 0, 0);
            SemanticPathRunPlan plan = new SemanticPathRunPlan(
                    workspace.resolve("bundle.json"), "bundle-hash", List.of(shard), true, 100_000,
                    workspace.resolve("owners.json"), "owner-hash");

            SemanticExtractionPrompt prompt = new SemanticPathResultSelection(stores)
                    .reconciliationPrompt(plan, plan.maxInputTokens());

            assertFalse(prompt.evidenceBundle().has("semanticSummary"));
            assertTrue(prompt.evidenceBundle().path("conflicts").isArray());
            assertTrue(prompt.userPrompt().contains("variant:orders"));
            assertFalse(prompt.userPrompt().contains("Unrelated payload"));
        } finally {
            stores.values().forEach(ExternalJsonRecordStore::close);
        }
    }

    private ObjectNode emptyDocument() {
        ObjectNode root = JSON.createObjectNode();
        for (String section : List.of(
                "entities", "events", "relations", "lineage", "metrics", "dimensions", "triplets", "reviewItems")) {
            root.putArray(section);
        }
        return root;
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
}
