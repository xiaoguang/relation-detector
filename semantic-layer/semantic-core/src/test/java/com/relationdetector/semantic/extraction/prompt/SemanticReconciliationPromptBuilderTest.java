package com.relationdetector.semantic.extraction.prompt;

import com.relationdetector.semantic.extraction.artifact.SemanticResultStore;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.artifact.SemanticResultSelection;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.internal.store.ExternalJsonRecordStore;

class SemanticReconciliationPromptBuilderTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void pathBackedPromptSendsOnlyConflictClosure(@TempDir java.nio.file.Path workspace) {
        Map<SemanticResultStore.Section, ExternalJsonRecordStore> stores = new LinkedHashMap<>();
        try {
            for (SemanticResultStore.Section section : SemanticResultStore.Section.values()) {
                stores.put(section, new ExternalJsonRecordStore(workspace.resolve(section.wireName())));
            }
            stores.get(SemanticResultStore.Section.ENTITIES).append(
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
            stores.get(SemanticResultStore.Section.ENTITIES).append("entity:orders", conflict);
            stores.values().forEach(ExternalJsonRecordStore::finish);
            SemanticShardDescriptor shard = new SemanticShardDescriptor(
                    "shard-0001", "orders", workspace.resolve("shard.json"), 100, 0, 0);
            SemanticRunPlan plan = new SemanticRunPlan(
                    workspace.resolve("bundle.json"), "bundle-hash", List.of(shard), true, 100_000,
                    24_000, 16_000,
                    workspace.resolve("owners.json"), "owner-hash");

            SemanticExtractionPrompt prompt = new SemanticResultSelection(stores)
                    .reconciliationPrompt(plan, plan.maxInputTokens());

            assertFalse(prompt.evidenceBundle().has("semanticSummary"));
            assertTrue(prompt.evidenceBundle().path("conflicts").isArray());
            assertTrue(prompt.userPrompt().contains("variant:orders"));
            assertFalse(prompt.userPrompt().contains("Unrelated payload"));
        } finally {
            stores.values().forEach(ExternalJsonRecordStore::close);
        }
    }

}
