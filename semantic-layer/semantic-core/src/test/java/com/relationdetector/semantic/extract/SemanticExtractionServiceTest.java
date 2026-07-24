package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticExtractionServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void executesEveryShardThenReconcilesAndValidatesAgainstFullBundle() {
        ObjectNode bundle = bundleWithTwoComponents();
        SemanticExtractionRunPlan plan = new SemanticExtractionService().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));
        AtomicInteger shardCalls = new AtomicInteger();
        AtomicInteger reconciliationCalls = new AtomicInteger();
        SemanticModelClient shardClient = prompt -> {
            shardCalls.incrementAndGet();
            return result(rawShardDocument(prompt.evidenceBundle()));
        };
        SemanticModelClient reconciliationClient = prompt -> {
            reconciliationCalls.incrementAndGet();
            return result("""
                    {"resolutions":[],"renames":[],"relations":[]}
                    """);
        };

        SemanticExtractionRunResult run = new SemanticExtractionService().execute(
                plan, shardClient, reconciliationClient);

        assertEquals(2, shardCalls.get());
        assertEquals(1, reconciliationCalls.get());
        assertEquals(4, run.finalDocument().path("entities").size());
        assertEquals(2, run.finalDocument().path("triplets").size());
        assertEquals(0, run.mergeResult().conflicts().size());
    }

    @Test
    void failedShardDoesNotProducePartialRunResult() {
        ObjectNode bundle = bundleWithTwoComponents();
        SemanticExtractionRunPlan plan = new SemanticExtractionService().plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));
        AtomicInteger calls = new AtomicInteger();
        SemanticModelClient client = prompt -> {
            if (calls.incrementAndGet() == 2) {
                throw new IllegalArgumentException("model failed");
            }
            return result(rawShardDocument(prompt.evidenceBundle()));
        };

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionService().execute(plan, client, prompt -> result("{}")));
    }

    @Test
    void deterministicBackfillUsesOnlyCandidatesOwnedByTheCurrentShard() {
        ObjectNode bundle = emptyBundle();
        addRelationship(bundle, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addTriplet(bundle, "trip-orders", "rel-orders", "ev-orders", "shop.orders", "shop.customers");
        SemanticExtractionService service = new SemanticExtractionService();
        SemanticExtractionRunPlan plan = service.plan(bundle,
                new SemanticShardingOptions(SemanticShardMode.FORCE, 300, 800_000, 128, false));
        SemanticModelClient client = prompt -> {
            JsonNode context = prompt.evidenceBundle().path("shardContext");
            boolean ownsCandidate = context.path("ownedCandidateRefs").size() > 0;
            return result(ownsCandidate ? rawShardDocument(prompt.evidenceBundle()) : emptyDocument());
        };

        SemanticExtractionRunResult run = service.execute(plan, client, null);

        assertEquals(List.of(1, 0), run.shardExecutions().stream()
                .map(execution -> execution.normalizedDocument().path("triplets").size())
                .toList());
        assertEquals(1, run.finalDocument().path("triplets").size());
    }

    private ObjectNode bundleWithTwoComponents() {
        ObjectNode root = emptyBundle();
        addRelationship(root, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addRelationship(root, "rel-stock", "shop.stock.supplier_id", "shop.suppliers.id", "ev-stock");
        addTriplet(root, "trip-orders", "rel-orders", "ev-orders", "shop.orders", "shop.customers");
        addTriplet(root, "trip-stock", "rel-stock", "ev-stock", "shop.stock", "shop.suppliers");
        return root;
    }

    private String rawShardDocument(JsonNode bundle) {
        ObjectNode raw = JSON.createObjectNode();
        for (String section : List.of("entities", "events", "relations", "lineage", "metrics", "dimensions",
                "triplets", "reviewItems")) {
            raw.putArray(section);
        }
        String evidenceRef = bundle.path("relationships").get(0).path("id").asText();
        for (JsonNode table : bundle.path("tables")) {
            String physicalName = table.asText();
            ObjectNode entity = raw.withArray("entities").addObject()
                    .put("name", physicalName)
                    .put("type", "业务实体")
                    .put("physicalName", physicalName);
            entity.putArray("ownedGroundingRefs").add(evidenceRef);
            entity.putArray("evidenceRefs").add(evidenceRef);
        }
        try {
            return JSON.writeValueAsString(raw);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    private String emptyDocument() {
        ObjectNode raw = JSON.createObjectNode();
        for (String section : List.of("entities", "events", "relations", "lineage", "metrics", "dimensions",
                "triplets", "reviewItems")) {
            raw.putArray(section);
        }
        return raw.toString();
    }

    private SemanticExtractionResult result(String output) {
        ObjectNode response = JSON.createObjectNode();
        response.put("output_text", output);
        response.putObject("usage").put("input_tokens", 100).put("output_tokens", 50);
        return new SemanticExtractionResult("{}", response.toString(), output, response);
    }

    private ObjectNode emptyBundle() {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("database").put("type", "mysql").put("catalog", "shop").put("schema", "");
        root.put("focus", "");
        for (String section : List.of("inputFiles", "sources", "tables", "evidence", "relationships", "lineage",
                "eventCandidates", "derivedRelationships", "derivedLineage", "namingEvidence",
                "reviewItemCandidates", "tripletCandidates", "diagnostics")) {
            root.putArray(section);
        }
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
        addTextOnce(bundle.withArray("tables"), table(source));
        addTextOnce(bundle.withArray("tables"), table(target));
        bundle.withArray("evidence").addObject()
                .put("id", evidenceId)
                .put("type", "SQL_LOG_JOIN")
                .put("sourceType", "SQL_FILE")
                .put("score", 0.8)
                .put("source", "queries.sql")
                .put("detail", source + " = " + target)
                .putObject("attributes");
        bundle.withArray("relationships").addObject()
                .put("id", id)
                .put("source", source)
                .put("target", target)
                .put("type", "FK_LIKE")
                .put("subType", "SQL_JOIN")
                .put("confidence", 0.8)
                .putArray("evidenceRefs").add(evidenceId);
    }

    private void addTriplet(
            ObjectNode bundle,
            String id,
            String factRef,
            String evidenceRef,
            String subject,
            String object
    ) {
        bundle.withArray("tripletCandidates").addObject()
                .put("id", id)
                .put("type", "ENTITY_RELATION")
                .put("subject", subject)
                .put("predicate", "引用")
                .put("object", object)
                .put("factRef", factRef)
                .putArray("evidenceRefs").add(evidenceRef);
    }

    private void addTextOnce(com.fasterxml.jackson.databind.node.ArrayNode array, String value) {
        for (JsonNode item : array) if (value.equals(item.asText())) return;
        array.add(value);
    }

    private String table(String endpoint) {
        return endpoint.substring(0, endpoint.lastIndexOf('.'));
    }
}
