package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.SemanticDiskBackedSession;

final class SemanticPathBackedPipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void metadataOnlyTablesRemainOwnedFactsInSeparateTypedShards() throws Exception {
        Path input = writeMetadataOnlyScan();
        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("session-work"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false));

            assertEquals(2, plan.shards().size());
            for (SemanticPathShard shard : plan.shards()) {
                JsonNode bundle = JSON.readTree(shard.bundlePath().toFile());
                assertEquals(1, bundle.path("metadataTables").size());
                assertEquals(1, bundle.path("metadataColumns").size());
                assertFalse(bundle.path("shardContext").path("ownedFactRefs").isEmpty());
                assertTrue(bundle.path("relationships").isEmpty());
            }
        }
    }

    @Test
    void laterShardFailureKeepsCompletedShardAuditWithoutPublishingRun() throws Exception {
        Path input = writeMetadataOnlyScan();
        Path output = tempDir.resolve("runs");
        AtomicInteger calls = new AtomicInteger();

        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("failure-session-work"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false));

            assertThrows(IllegalStateException.class, () ->
                    new SemanticPathRunArtifactWriter().executeAndWrite(
                            output,
                            plan,
                            session.evidenceStore(),
                            prompt -> {
                                if (calls.incrementAndGet() == 2) {
                                    throw new IllegalStateException("synthetic second-shard failure");
                                }
                                return modelResult(prompt.evidenceBundle());
                            },
                            null,
                            "test-provider",
                            "test-model",
                            "test-effort",
                            ArtifactRetention.FULL,
                            ignored -> {
                            }));
        }

        Path staging = onlyDirectory(output, ".staging-");
        JsonNode manifest = JSON.readTree(staging.resolve("run-manifest.json").toFile());
        assertEquals("FAILED", manifest.path("status").asText());
        assertEquals("COMPLETE", manifest.path("shards").get(0).path("status").asText());
        assertEquals("PENDING", manifest.path("shards").get(1).path("status").asText());
        assertTrue(Files.isRegularFile(staging.resolve(
                "shards/shard-0001/semantic-extraction-result.json")));
        assertFalse(hasDirectory(output, "run-"));
    }

    private SemanticExtractionResult modelResult(JsonNode bundle) {
        JsonNode metadata = bundle.path("metadataTables").get(0);
        String factId = metadata.path("id").asText();
        String table = metadata.path("table").asText();
        ObjectNode raw = emptySemanticDocument();
        ObjectNode entity = raw.withArray("entities").addObject()
                .put("name", table)
                .put("type", "PHYSICAL_ENTITY")
                .put("physicalName", table);
        entity.putArray("ownedGroundingRefs").add(factId);
        entity.putArray("evidenceRefs").add(factId);
        ObjectNode relation = raw.withArray("relations").addObject()
                .put("from", table)
                .put("to", table)
                .put("type", "SELF_REFERENCE");
        relation.putArray("ownedGroundingRefs").add(factId);
        relation.putArray("evidenceRefs").add(factId);
        ObjectNode response = JSON.createObjectNode();
        response.put("output_text", raw.toString());
        response.putObject("usage").put("input_tokens", 10).put("output_tokens", 10);
        return new SemanticExtractionResult("{}", response.toString(), raw.toString(), response, 1);
    }

    private ObjectNode emptySemanticDocument() {
        ObjectNode result = JSON.createObjectNode();
        for (String section : List.of(
                "entities", "events", "relations", "lineage",
                "metrics", "dimensions", "triplets", "reviewItems")) {
            result.putArray(section);
        }
        return result;
    }

    private Path writeMetadataOnlyScan() throws Exception {
        ObjectNode root = JSON.createObjectNode();
        root.putObject("database").put("type", "mysql").put("catalog", "shop").put("schema", "");
        root.put("generatedAt", "2026-07-28T00:00:00Z");
        ObjectNode summary = root.putObject("summary");
        for (String field : List.of(
                "directRelationshipCount", "derivedRelationshipCount", "totalRelationshipCount",
                "directDataLineageCount", "derivedDataLineageCount", "totalDataLineageCount",
                "directNamingEvidenceCount", "derivedNamingEvidenceCount", "totalNamingEvidenceCount",
                "warningCount")) {
            summary.put(field, 0);
        }
        summary.putArray("sources").add("metadata");
        ObjectNode inventory = root.putObject("metadataInventory");
        inventory.put("status", "COMPLETE");
        ObjectNode scope = inventory.putObject("scope");
        scope.put("catalog", "shop");
        scope.putNull("schema");
        scope.putArray("includeTables");
        scope.putArray("excludeTables");
        inventory.putObject("counts")
                .put("tables", 2)
                .put("columns", 2)
                .put("constraints", 0)
                .put("indexes", 0);
        addMetadataTable(inventory, "orders");
        addMetadataTable(inventory, "customers");
        addMetadataColumn(inventory, "orders");
        addMetadataColumn(inventory, "customers");
        inventory.putArray("constraints");
        inventory.putArray("indexes");
        for (String section : List.of(
                "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
                "namingEvidence", "derivedNamingEvidence", "warnings")) {
            root.putArray(section);
        }
        Path input = tempDir.resolve("metadata-only-scan.json");
        JSON.writeValue(input.toFile(), root);
        return input;
    }

    private void addMetadataTable(ObjectNode inventory, String table) {
        inventory.withArray("tables").addObject()
                .put("catalog", "shop")
                .putNull("schema")
                .put("tableName", table)
                .put("tableType", "BASE TABLE");
    }

    private void addMetadataColumn(ObjectNode inventory, String table) {
        inventory.withArray("columns").addObject()
                .put("catalog", "shop")
                .putNull("schema")
                .put("tableName", table)
                .put("columnName", "id")
                .put("dataType", "bigint")
                .put("columnType", "bigint")
                .put("nullable", false)
                .put("ordinalPosition", 1);
    }

    private Path onlyDirectory(Path root, String prefix) throws Exception {
        try (var entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private boolean hasDirectory(Path root, String prefix) throws Exception {
        try (var entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }
}
