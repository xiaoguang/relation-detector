package com.relationdetector.semantic.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class SemanticExtractionRunArtifactWriterTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void completeManifestIncludesReconciliationAndAggregateUsage() throws Exception {
        SemanticExtractionService service = new SemanticExtractionService();
        SemanticExtractionRunPlan plan = service.plan(twoComponentBundle(),
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));
        AtomicInteger attempts = new AtomicInteger();
        SemanticModelClient shardClient = prompt -> result(
                rawShardDocument(prompt.evidenceBundle()), attempts.incrementAndGet());
        SemanticModelClient reconciliationClient = prompt -> result(
                "{\"resolutions\":[],\"renames\":[],\"relations\":[]}",
                attempts.incrementAndGet());
        SemanticExtractionRunResult run = service.execute(plan, shardClient, reconciliationClient);

        Path published = new SemanticExtractionRunArtifactWriter().writeResult(
                tempDir, run, "openai-api", "current-model", "high", ArtifactRetention.FULL);

        JsonNode manifest = JSON.readTree(published.resolve("run-manifest.json").toFile());
        assertEquals("COMPLETE", manifest.path("status").asText());
        assertEquals("openai-api", manifest.path("provider").asText());
        assertEquals("current-model", manifest.path("model").asText());
        assertEquals("high", manifest.path("reasoningEffort").asText());
        assertEquals(published.getFileName().toString().substring("run-".length()),
                manifest.path("runId").asText());
        assertEquals("full", manifest.path("retention").asText());
        assertTrue(manifest.path("publishedAt").isTextual());
        assertEquals(0, manifest.path("mergeConflictCount").asInt());
        assertTrue(manifest.path("finalRefClosed").asBoolean());
        assertEquals(300, manifest.path("usage").path("inputTokens").asInt());
        assertEquals(150, manifest.path("usage").path("outputTokens").asInt());
        assertEquals(6, manifest.path("usage").path("transportAttempts").asInt());
        assertEquals("COMPLETE", manifest.path("reconciliation").path("status").asText());
        assertEquals(100, manifest.path("reconciliation").path("inputTokens").asInt());
        assertEquals(50, manifest.path("reconciliation").path("outputTokens").asInt());
        assertEquals(3, manifest.path("reconciliation").path("transportAttempts").asInt());
    }

    @Test
    void completeSingleShardRunKeepsLegacyRootArtifacts() {
        SemanticExtractionService service = new SemanticExtractionService();
        SemanticExtractionRunPlan plan = service.plan(singleComponentBundle(), SemanticShardingOptions.defaults());
        SemanticExtractionRunResult run = service.execute(
                plan,
                prompt -> result(rawShardDocument(prompt.evidenceBundle()), 1),
                null);

        Path published = new SemanticExtractionRunArtifactWriter().writeResult(
                tempDir, run, "openai-api", "gpt-test", "medium", ArtifactRetention.FULL);

        for (String name : List.of(
                "semantic-extraction-evidence-bundle.json",
                "semantic-extraction-prompt.md",
                "semantic-extraction-request.json",
                "semantic-extraction-response.json",
                "semantic-extraction-result-raw.json",
                "semantic-extraction-result.json")) {
            assertTrue(Files.isRegularFile(published.resolve(name)), name);
        }
    }

    @Test
    void reusableRootPublishesIndependentCodexAndRequestOnlyRuns() throws Exception {
        SemanticExtractionRunPlan plan = new SemanticExtractionService().plan(
                singleComponentBundle(), SemanticShardingOptions.defaults());
        SemanticModelClient requestClient = new SemanticModelClient() {
            @Override
            public SemanticExtractionResult extract(SemanticExtractionPrompt prompt) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String requestJson(SemanticExtractionPrompt prompt) {
                return "{\"model\":\"stale-request-model\",\"reasoning\":{\"effort\":\"low\"}}";
            }
        };
        SemanticExtractionRunArtifactWriter writer = new SemanticExtractionRunArtifactWriter();

        Path codexRun = writer.writeCodexSession(
                tempDir, plan, "codex-current", "medium", ArtifactRetention.FULL);
        Path requestRun = writer.writeRequestOnly(
                tempDir, plan, requestClient, null, "api-current", "xhigh", ArtifactRetention.FULL);

        assertNotEquals(codexRun, requestRun);
        assertEquals(tempDir, codexRun.getParent());
        assertEquals(tempDir, requestRun.getParent());
        assertTrue(codexRun.getFileName().toString().startsWith("run-"));
        assertTrue(requestRun.getFileName().toString().startsWith("run-"));
        assertTrue(Files.isRegularFile(codexRun.resolve("semantic-extraction-codex-session.md")));
        assertTrue(Files.isRegularFile(requestRun.resolve("semantic-extraction-request.json")));
        JsonNode requestManifest = JSON.readTree(requestRun.resolve("run-manifest.json").toFile());
        assertEquals("openai-api", requestManifest.path("provider").asText());
        assertEquals("api-current", requestManifest.path("model").asText());
        assertEquals("xhigh", requestManifest.path("reasoningEffort").asText());
        assertFalse(hasDirectory(tempDir, ".staging-"));
    }

    @Test
    void concurrentRunsUsingOneRootNeverShareAStagingDirectory() throws Exception {
        SemanticExtractionRunPlan plan = new SemanticExtractionService().plan(
                singleComponentBundle(), SemanticShardingOptions.defaults());
        SemanticExtractionRunArtifactWriter writer = new SemanticExtractionRunArtifactWriter();
        CountDownLatch bothStaged = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Path> first = executor.submit(() -> writer.writeCodexSession(
                    tempDir, plan, "model-a", "high", ArtifactRetention.FULL,
                    staging -> awaitBoth(bothStaged)));
            Future<Path> second = executor.submit(() -> writer.writeCodexSession(
                    tempDir, plan, "model-b", "low", ArtifactRetention.FULL,
                    staging -> awaitBoth(bothStaged)));

            Path firstRun = first.get(10, TimeUnit.SECONDS);
            Path secondRun = second.get(10, TimeUnit.SECONDS);

            assertNotEquals(firstRun, secondRun);
            assertEquals(2, directoryCount(tempDir, "run-"));
            assertFalse(hasDirectory(tempDir, ".staging-"));
            assertEquals("model-a", JSON.readTree(firstRun.resolve("run-manifest.json").toFile())
                    .path("model").asText());
            assertEquals("model-b", JSON.readTree(secondRun.resolve("run-manifest.json").toFile())
                    .path("model").asText());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedWriteKeepsFailedManifestInStagingWithoutPublishingFinalDirectory() throws Exception {
        SemanticExtractionRunPlan plan = new SemanticExtractionService().plan(
                singleComponentBundle(), SemanticShardingOptions.defaults());

        assertThrows(IllegalStateException.class,
                () -> new SemanticExtractionRunArtifactWriter().writeCodexSession(
                        tempDir, plan, "codex-current", "high", ArtifactRetention.FULL,
                        staging -> {
                            writeBytes(staging.resolve("partial.bin"), new byte[] {(byte) 0xff, 0x00});
                            throw new IllegalStateException("synthetic artifact failure");
                        }));

        Path staging = onlyDirectory(tempDir, ".staging-");
        JsonNode manifest = JSON.readTree(staging.resolve("run-manifest.json").toFile());
        assertEquals("FAILED", manifest.path("status").asText());
        assertEquals("codex-session", manifest.path("provider").asText());
        assertEquals("codex-current", manifest.path("model").asText());
        assertEquals("high", manifest.path("reasoningEffort").asText());
        assertTrue(manifest.path("publishedAt").isNull());
        assertTrue(hasArtifact(manifest, "partial.bin"));
        assertEquals(0, directoryCount(tempDir, "run-"));
    }

    @Test
    void failedModelExecutionAlsoLeavesAnUnpublishedFailedRun() throws Exception {
        SemanticExtractionRunPlan plan = new SemanticExtractionService().plan(
                singleComponentBundle(), SemanticShardingOptions.defaults());

        assertThrows(IllegalArgumentException.class,
                () -> new SemanticExtractionRunArtifactWriter().executeAndWriteResult(
                        tempDir,
                        plan,
                        () -> {
                            throw new IllegalArgumentException("synthetic model failure");
                        },
                        "openai-api",
                        "gpt-current",
                        "xhigh",
                        ArtifactRetention.FULL,
                        staging -> writeBytes(staging.resolve(
                                "deterministic-kg/semantic-kg.json"), "{}".getBytes())));

        Path staging = onlyDirectory(tempDir, ".staging-");
        JsonNode manifest = JSON.readTree(staging.resolve("run-manifest.json").toFile());
        assertEquals("FAILED", manifest.path("status").asText());
        assertEquals("gpt-current", manifest.path("model").asText());
        assertTrue(hasArtifact(manifest, "deterministic-kg/semantic-kg.json"));
        assertEquals(0, directoryCount(tempDir, "run-"));
    }

    @Test
    void finalOnlyPrunesIntermediatePayloadsAfterSuccessAndHashesBinaryArtifacts() throws Exception {
        SemanticExtractionService service = new SemanticExtractionService();
        SemanticExtractionRunPlan plan = service.plan(twoComponentBundle(),
                new SemanticShardingOptions(SemanticShardMode.FORCE, 240_000, 800_000, 128, true));
        SemanticExtractionRunResult run = service.execute(
                plan,
                prompt -> result(rawShardDocument(prompt.evidenceBundle()), 1),
                prompt -> result("{\"resolutions\":[],\"renames\":[],\"relations\":[]}", 1));
        byte[] binary = new byte[] {(byte) 0xff, 0x00, 0x01, (byte) 0x80};

        Path published = new SemanticExtractionRunArtifactWriter().writeResult(
                tempDir, run, "openai-api", "gpt-current", "xhigh", ArtifactRetention.FINAL_ONLY,
                staging -> writeBytes(staging.resolve("deterministic-kg/binary.bin"), binary));

        assertTrue(Files.isRegularFile(published.resolve("semantic-extraction-result.json")));
        assertTrue(Files.isRegularFile(published.resolve("deterministic-kg/binary.bin")));
        assertFalse(Files.exists(published.resolve("shards")));
        assertFalse(Files.exists(published.resolve("reconciliation")));
        assertFalse(Files.exists(published.resolve("full-evidence-bundle.json")));
        assertFalse(Files.exists(published.resolve("merged-draft.json")));
        JsonNode manifest = JSON.readTree(published.resolve("run-manifest.json").toFile());
        assertEquals("final-only", manifest.path("retention").asText());
        assertEquals(sha256(binary), artifactHash(manifest, "deterministic-kg/binary.bin"));
        assertTrue(hasPrunedArtifact(manifest, "shards/shard-0001/semantic-extraction-response.json"));
        assertTrue(hasPrunedArtifact(manifest, "reconciliation/patch.json"));
        assertTrue(hasPrunedArtifact(manifest, "full-evidence-bundle.json"));
        assertTrue(hasPrunedArtifact(manifest, "merged-draft.json"));
    }

    private void awaitBoth(CountDownLatch latch) {
        latch.countDown();
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    private boolean hasDirectory(Path root, String prefix) throws IOException {
        return directoryCount(root, prefix) > 0;
    }

    private long directoryCount(Path root, String prefix) throws IOException {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .count();
        }
    }

    private Path onlyDirectory(Path root, String prefix) throws IOException {
        try (Stream<Path> entries = Files.list(root)) {
            return entries.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private boolean hasArtifact(JsonNode manifest, String path) {
        for (JsonNode artifact : manifest.path("artifacts")) {
            if (path.equals(artifact.path("path").asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPrunedArtifact(JsonNode manifest, String path) {
        for (JsonNode artifact : manifest.path("prunedArtifacts")) {
            if (path.equals(artifact.path("path").asText())) {
                return true;
            }
        }
        return false;
    }

    private String artifactHash(JsonNode manifest, String path) {
        for (JsonNode artifact : manifest.path("artifacts")) {
            if (path.equals(artifact.path("path").asText())) {
                return artifact.path("sha256").asText();
            }
        }
        return "";
    }

    private void writeBytes(Path path, byte[] bytes) {
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private ObjectNode twoComponentBundle() {
        ObjectNode root = emptyBundle();
        addRelationship(root, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addRelationship(root, "rel-stock", "shop.stock.supplier_id", "shop.suppliers.id", "ev-stock");
        addTriplet(root, "trip-orders", "rel-orders", "ev-orders", "shop.orders", "shop.customers");
        addTriplet(root, "trip-stock", "rel-stock", "ev-stock", "shop.stock", "shop.suppliers");
        return root;
    }

    private ObjectNode singleComponentBundle() {
        ObjectNode root = emptyBundle();
        addRelationship(root, "rel-orders", "shop.orders.customer_id", "shop.customers.id", "ev-orders");
        addTriplet(root, "trip-orders", "rel-orders", "ev-orders", "shop.orders", "shop.customers");
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

    private SemanticExtractionResult result(String output, int attempts) {
        ObjectNode response = JSON.createObjectNode();
        response.put("output_text", output);
        response.putObject("usage").put("input_tokens", 100).put("output_tokens", 50);
        return new SemanticExtractionResult("{}", response.toString(), output, response, attempts);
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
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return;
            }
        }
        array.add(value);
    }

    private String table(String endpoint) {
        return endpoint.substring(0, endpoint.lastIndexOf('.'));
    }
}
