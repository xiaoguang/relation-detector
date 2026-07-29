package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.SemanticDiskBackedSession;
import com.relationdetector.semantic.reader.ScanResultReader;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;
import com.relationdetector.semantic.reader.SemanticInputStore;

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
    void completeEmptyBundleProducesOneGlobalShard() throws Exception {
        Path input = writeEmptyScan();
        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("empty-session-work"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 8, false));

            assertEquals(1, plan.shards().size());
            SemanticPathShard shard = plan.shards().get(0);
            assertEquals("shard-0001", shard.id());
            assertEquals("global", shard.ownerKey());
            assertEquals(0, shard.ownedFactCount());
            assertEquals(0, shard.ownedCandidateCount());
            assertEquals(0, Files.size(plan.ownerManifestPath()));

            JsonNode context = JSON.readTree(shard.bundlePath().toFile()).path("shardContext");
            assertTrue(context.path("ownedFactRefs").isEmpty());
            assertTrue(context.path("ownedCandidateRefs").isEmpty());
            assertTrue(context.path("overlapRefs").isEmpty());
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

    @Test
    void rawBufferSizeDoesNotChangeGlobalOwnerManifestOrShardBundles() throws Exception {
        Path input = writeMetadataOnlyScan();

        SemanticPathRunPlan large = plan(input, "large", 1024 * 1024);
        SemanticPathRunPlan tiny = plan(input, "tiny", 1);

        assertEquals(Files.readString(large.ownerManifestPath()), Files.readString(tiny.ownerManifestPath()));
        assertEquals(shardFingerprints(large), shardFingerprints(tiny));
        assertOwnerCoverage(large);
        assertOwnerCoverage(tiny);
    }

    @Test
    void resultStoreRejectsDuplicateManifestIdentityAndOwnedOverlapIntersection() throws Exception {
        Path input = writeMetadataOnlyScan();
        SemanticPathRunPlan plan = plan(input, "owner-validation", 1);
        SemanticPathShard first = plan.shards().get(0);
        ObjectNode bundle = (ObjectNode) JSON.readTree(first.bundlePath().toFile());

        String firstLine = Files.readAllLines(plan.ownerManifestPath()).get(0);
        Files.writeString(
                plan.ownerManifestPath(),
                Files.readString(plan.ownerManifestPath()) + firstLine + System.lineSeparator());
        SemanticPathRunPlan duplicateManifestPlan = new SemanticPathRunPlan(
                plan.fullBundlePath(),
                plan.fullBundleHash(),
                plan.shards(),
                plan.reconcile(),
                plan.maxInputTokens(),
                plan.ownerManifestPath(),
                sha256(plan.ownerManifestPath()));
        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                     List.of(input), tempDir.resolve("duplicate-owner-session"));
             SemanticPathResultStore results = new SemanticPathResultStore(
                     tempDir.resolve("duplicate-owner-results"),
                     session.evidenceStore(),
                     duplicateManifestPlan)) {
            assertThrows(SemanticExtractionValidationException.class,
                    () -> results.append(first, bundle, emptySemanticDocument()));
        }

        SemanticPathRunPlan clean = plan(input, "owner-intersection", 1);
        SemanticPathShard cleanFirst = clean.shards().get(0);
        ObjectNode intersecting = (ObjectNode) JSON.readTree(cleanFirst.bundlePath().toFile());
        String owned = intersecting.path("shardContext").path("ownedFactRefs").get(0).asText();
        intersecting.withObject("/shardContext").withArray("overlapRefs").add(owned);
        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                     List.of(input), tempDir.resolve("intersecting-owner-session"));
             SemanticPathResultStore results = new SemanticPathResultStore(
                     tempDir.resolve("intersecting-owner-results"), session.evidenceStore(), clean)) {
            assertThrows(SemanticExtractionValidationException.class,
                    () -> results.append(cleanFirst, intersecting, emptySemanticDocument()));
        }
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

    private SemanticPathRunPlan plan(Path input, String prefix, long bufferBytes) throws Exception {
        Path inputWork = tempDir.resolve(prefix + "-input-work");
        Path evidenceWork = tempDir.resolve(prefix + "-evidence-work");
        try (SemanticInputStore store = new ScanResultReader().open(List.of(input), inputWork);
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(store, evidenceWork, bufferBytes)) {
            return new SemanticPathBackedPlanner().plan(
                    evidence,
                    tempDir.resolve(prefix + "-plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false));
        }
    }

    private List<String> shardFingerprints(SemanticPathRunPlan plan) throws Exception {
        List<String> result = new ArrayList<>();
        for (SemanticPathShard shard : plan.shards()) {
            result.add(com.relationdetector.semantic.StableSemanticId.canonicalJson(
                    JSON.readTree(shard.bundlePath().toFile())));
        }
        return result;
    }

    private void assertOwnerCoverage(SemanticPathRunPlan plan) throws Exception {
        Set<String> owned = new LinkedHashSet<>();
        for (SemanticPathShard shard : plan.shards()) {
            JsonNode context = JSON.readTree(shard.bundlePath().toFile()).path("shardContext");
            Set<String> shardOwned = new LinkedHashSet<>();
            for (String field : List.of("ownedFactRefs", "ownedCandidateRefs")) {
                for (JsonNode value : context.path(field)) {
                    assertTrue(shardOwned.add(value.asText()), () -> "duplicate shard owner for " + value.asText());
                    assertTrue(owned.add(value.asText()), () -> "duplicate global owner for " + value.asText());
                }
            }
            Set<String> overlap = new HashSet<>();
            context.path("overlapRefs").forEach(value -> overlap.add(value.asText()));
            assertTrue(java.util.Collections.disjoint(shardOwned, overlap));
        }
        assertEquals(Files.readAllLines(plan.ownerManifestPath()).size(), owned.size());
    }

    private String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
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

    private Path writeEmptyScan() throws Exception {
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
                .put("tables", 0)
                .put("columns", 0)
                .put("constraints", 0)
                .put("indexes", 0);
        for (String section : List.of("tables", "columns", "constraints", "indexes")) {
            inventory.putArray(section);
        }
        for (String section : List.of(
                "relationships", "dataLineages", "derivedRelationships", "derivedDataLineages",
                "namingEvidence", "derivedNamingEvidence", "warnings")) {
            root.putArray(section);
        }
        Path input = tempDir.resolve("empty-scan.json");
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
