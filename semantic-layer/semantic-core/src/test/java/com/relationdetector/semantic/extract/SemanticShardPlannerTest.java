package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticShardPlannerTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void forceModePartitionsDisconnectedComponentsAndKeepsEvidenceClosure() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addRelationship(bundle, "rel-stock", "shop.stock.supplier_id", "shop.suppliers.id", "ev-stock");
        addTriplet(bundle, "trip-orders", "rel-orders", "ev-orders",
                "shop.orders", "shop.customers");
        addTriplet(bundle, "trip-stock", "rel-stock", "ev-stock",
                "shop.stock", "shop.suppliers");

        SemanticShardPlan plan = new SemanticShardPlanner().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));

        assertEquals(2, plan.shards().size());
        assertEquals(2, plan.candidateOwners().size());
        for (SemanticShard shard : plan.shards()) {
            assertEquals(1, shard.bundle().path("relationships").size());
            assertEquals(1, shard.bundle().path("tripletCandidates").size());
            assertEquals(1, shard.bundle().path("evidence").size());
            String evidenceRef = shard.bundle().path("relationships").get(0)
                    .path("evidenceRefs").get(0).asText();
            assertEquals(evidenceRef, shard.bundle().path("evidence").get(0).path("id").asText());
        }
    }

    @Test
    void autoModeKeepsSmallBundleAsOneRequest() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");

        SemanticShardPlan plan = new SemanticShardPlanner().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.AUTO, 240_000, 800_000, 128, true));

        assertEquals(1, plan.shards().size());
        assertEquals("full", plan.shards().get(0).ownerKey());
    }

    @Test
    void autoModePacksSmallDisconnectedComponentsIntoBoundedShards() {
        ObjectNode bundle = emptyBundle();
        for (int index = 0; index < 8; index++) {
            addRelationship(bundle, "rel-" + index,
                    "shop.source_" + index + ".foreign_id",
                    "shop.target_" + index + ".id",
                    "ev-" + index);
        }
        SemanticShardPlanner planner = new SemanticShardPlanner();
        SemanticShardPlan forced = planner.plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));
        SemanticShardPlan complete = planner.plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.OFF, 240_000, 800_000, 128, true));
        int largestUnit = forced.shards().stream()
                .mapToInt(SemanticShard::estimatedInputTokens)
                .max()
                .orElseThrow();
        int target = (largestUnit + complete.shards().get(0).estimatedInputTokens()) / 2;

        SemanticShardPlan automatic = planner.plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.AUTO, target, 800_000, 128, true));

        assertTrue(automatic.shards().size() > 1);
        assertTrue(automatic.shards().size() < forced.shards().size());
        assertTrue(automatic.shards().stream()
                .allMatch(shard -> shard.estimatedInputTokens() <= target + 1_000));
    }

    @Test
    void offModeUsesTheHardLimitRatherThanThePreferredTarget() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");

        SemanticShardPlan plan = new SemanticShardPlanner().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.OFF, 300, 800_000, 128, true));

        assertEquals(1, plan.shards().size());
    }

    @Test
    void offModeRejectsACompletePromptAboveTheHardLimit() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        bundle.path("evidence").get(0).withObject("/attributes").put("payload", "x".repeat(20_000));

        assertThrows(SemanticShardingException.class,
                () -> new SemanticShardPlanner().plan(bundle,
                        new SemanticShardingOptions(SemanticShardMode.OFF, 300, 500, 128, true)));
    }

    @Test
    void refusesBundleWhoseAtomicClosureExceedsHardLimit() {
        SemanticShardPlanner planner = new SemanticShardPlanner();
        int fixedEstimate = planner.plan(emptyBundle(),
                        new SemanticShardingOptions(SemanticShardMode.OFF, 300, 800_000, 128, true))
                .shards().get(0).estimatedInputTokens();
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        bundle.path("evidence").get(0).withObject("/attributes").put("payload", "x".repeat(20_000));

        SemanticShardingException error = assertThrows(SemanticShardingException.class,
                () -> planner.plan(bundle,
                        new SemanticShardingOptions(
                                SemanticShardMode.FORCE, 300, fixedEstimate + 250, 128, true)));

        assertTrue(error.getMessage().contains("atomic evidence closure"));
    }

    @Test
    void splitsOneTableOwnerWhenItsIndependentFactClosuresExceedHardLimitTogether() {
        SemanticShardPlanner planner = new SemanticShardPlanner();
        ObjectNode single = emptyBundle();
        addRelationship(single, "rel-0", "shop.orders.customer_id", "shop.customers.id", "ev-0");
        single.path("evidence").get(0).withObject("/attributes").put("payload", "x".repeat(5_000));
        SemanticShardPlan singlePlan = planner.plan(single,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 300, 800_000, 128, true));
        int maxSingleEstimate = singlePlan.shards().stream()
                .mapToInt(SemanticShard::estimatedInputTokens)
                .max()
                .orElseThrow();

        ObjectNode bundle = emptyBundle();
        for (int index = 0; index < 4; index++) {
            addRelationship(bundle, "rel-" + index,
                    "shop.orders.customer_id", "shop.customers.id", "ev-" + index);
            bundle.path("evidence").get(index).withObject("/attributes")
                    .put("payload", "x".repeat(5_000));
        }

        SemanticShardPlan plan = planner.plan(bundle,
                new SemanticShardingOptions(
                        SemanticShardMode.FORCE,
                        300,
                        maxSingleEstimate + 250,
                        128,
                        true));

        assertEquals(4, plan.factOwners().size());
        assertTrue(plan.shards().size() > 2);
        assertTrue(plan.shards().stream()
                .allMatch(shard -> shard.estimatedInputTokens() <= maxSingleEstimate + 250));
        assertTrue(plan.shards().stream()
                .map(SemanticShard::ownerKey)
                .anyMatch(owner -> owner.startsWith("shop.customers#part-")));
    }

    @Test
    void hardLimitAppliesAfterOwnershipContextIsAdded() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        SemanticShardPlanner planner = new SemanticShardPlanner();
        SemanticShardPlan reference = planner.plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 300, 800_000, 128, true));
        int finalEstimate = reference.shards().get(0).estimatedInputTokens();

        assertThrows(SemanticShardingException.class,
                () -> planner.plan(bundle,
                        new SemanticShardingOptions(
                                SemanticShardMode.FORCE, 300, finalEstimate - 1, 128, true)));
    }

    @Test
    void coverageValidatorRejectsCandidateWithoutCanonicalOwner() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addTriplet(bundle, "trip-orders", "rel-orders", "ev-orders",
                "shop.orders", "shop.customers");
        SemanticShardPlan valid = new SemanticShardPlanner().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));
        SemanticShardPlan broken = new SemanticShardPlan(
                valid.fullBundleHash(), valid.shards(), valid.factOwners(), java.util.Map.of());

        assertThrows(SemanticShardingException.class,
                () -> new SemanticShardCoverageValidator().validate(bundle, broken));
    }

    @Test
    void oversizedConnectedComponentPublishesOwnedAndOverlapRefsToEveryPromptBundle() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addTriplet(bundle, "trip-orders", "rel-orders", "ev-orders",
                "shop.orders", "shop.customers");

        SemanticShardPlan plan = new SemanticShardPlanner().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 300, 800_000, 128, true));

        assertEquals(2, plan.shards().size());
        assertEquals(1, plan.shards().stream()
                .filter(shard -> contains(shard.bundle().path("shardContext").path("ownedFactRefs"), "rel-orders"))
                .count());
        assertEquals(1, plan.shards().stream()
                .filter(shard -> contains(
                        shard.bundle().path("shardContext").path("ownedCandidateRefs"), "trip-orders"))
                .count());
        SemanticShard overlap = plan.shards().stream()
                .filter(shard -> contains(shard.bundle().path("shardContext").path("overlapRefs"), "rel-orders"))
                .findFirst()
                .orElseThrow();
        assertTrue(new SemanticExtractionPromptBuilder().build(overlap.bundle()).userPrompt()
                .contains("\"outputOwnedReferencesOnly\" : true"));
    }

    @Test
    void componentGraphUsesTypedEndpointsAndReferenceDependenciesOnly() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addRelationship(bundle, "rel-stock", "shop.stock.supplier_id", "shop.suppliers.id", "ev-stock");
        addTriplet(bundle, "trip-orders", "rel-orders", "ev-orders",
                "shop.stock", "shop.suppliers");
        bundle.withArray("relationships").get(0).withObject("/attributes")
                .put("description", "shop.stock and shop.suppliers are mentioned for display only");
        bundle.withArray("diagnostics").addObject()
                .put("id", "diag-orders")
                .put("message", "shop.stock must not create a component edge");

        SemanticShardPlan plan = new SemanticShardPlanner().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));

        assertEquals(2, plan.shards().size());
        SemanticShard orders = plan.shards().stream()
                .filter(shard -> contains(shard.bundle().path("relationships"), "rel-orders", "id"))
                .findFirst()
                .orElseThrow();
        assertTrue(contains(orders.bundle().path("tripletCandidates"), "trip-orders", "id"));
        assertTrue(!contains(orders.bundle().path("relationships"), "rel-stock", "id"));
    }

    private ObjectNode emptyBundle() {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("database").put("type", "mysql").put("catalog", "shop").put("schema", "");
        root.put("focus", "");
        root.putArray("inputFiles");
        root.putArray("sources");
        root.putArray("tables");
        root.putArray("evidence");
        root.putArray("relationships");
        root.putArray("lineage");
        root.putArray("eventCandidates");
        root.putArray("derivedRelationships");
        root.putArray("derivedLineage");
        root.putArray("namingEvidence");
        root.putArray("reviewItemCandidates");
        root.putArray("tripletCandidates");
        root.putArray("diagnostics");
        root.putObject("instructions");
        return root;
    }

    private void addRelationship(
            ObjectNode bundle,
            String id,
            String source,
            String target,
            String evidenceId
    ) {
        addTable(bundle.withArray("tables"), table(source));
        addTable(bundle.withArray("tables"), table(target));
        ObjectNode evidence = bundle.withArray("evidence").addObject();
        evidence.put("id", evidenceId);
        evidence.put("type", "SQL_LOG_JOIN");
        evidence.put("sourceType", "SQL_FILE");
        evidence.put("score", 0.8);
        evidence.put("source", "queries.sql");
        evidence.put("detail", source + " = " + target);
        evidence.putObject("attributes");
        ObjectNode relationship = bundle.withArray("relationships").addObject();
        relationship.put("id", id);
        relationship.put("source", source);
        relationship.put("target", target);
        relationship.put("type", "FK_LIKE");
        relationship.put("subType", "SQL_JOIN");
        relationship.put("confidence", 0.8);
        relationship.putArray("evidenceRefs").add(evidenceId);
        relationship.putArray("evidenceTypes").add("SQL_LOG_JOIN");
    }

    private void addTriplet(
            ObjectNode bundle,
            String id,
            String factRef,
            String evidenceRef,
            String subject,
            String object
    ) {
        ObjectNode candidate = bundle.withArray("tripletCandidates").addObject();
        candidate.put("id", id);
        candidate.put("type", "ENTITY_RELATION");
        candidate.put("subject", subject);
        candidate.put("predicate", "引用");
        candidate.put("object", object);
        candidate.put("factRef", factRef);
        candidate.putArray("evidenceRefs").add(evidenceRef);
    }

    private void addTable(ArrayNode tables, String table) {
        for (JsonNode existing : tables) {
            if (table.equals(existing.asText())) {
                return;
            }
        }
        tables.add(table);
    }

    private String table(String endpoint) {
        int split = endpoint.lastIndexOf('.');
        return endpoint.substring(0, split);
    }

    private boolean contains(JsonNode array, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(JsonNode array, String value, String field) {
        for (JsonNode item : array) {
            if (value.equals(item.path(field).asText())) {
                return true;
            }
        }
        return false;
    }
}
