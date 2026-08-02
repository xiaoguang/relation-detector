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
        assertTrue(Files.isRegularFile(staging.resolve(
                "shards/shard-0001/external-audit-refs.tsv")));
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
    void shardsKeepStableEvidenceRefsWithoutDuplicatingFullEvidencePayloads() throws Exception {
        Path input = writeMetadataOnlyScan();

        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("external-evidence-session"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 128, false));

            assertFalse(read(plan.fullBundlePath()).path("evidence").isEmpty());
            for (SemanticPathShard shard : plan.shards()) {
                ObjectNode bundle = (ObjectNode) read(shard.bundlePath());
                assertTrue(bundle.path("evidence").isEmpty());
                int externalAuditRefCount =
                        bundle.path("shardContext").path("externalAuditRefCount").asInt();
                assertTrue(externalAuditRefCount > 0);
                assertEquals(
                        externalAuditRefCount,
                        SemanticExternalAuditReferences.read(
                                SemanticExternalAuditReferences.sidecar(shard.bundlePath())).size());
                assertPromptAuditReferencesAreSummarized(bundle);
            }

            Path run = new SemanticPathRunArtifactWriter().writeRequestOnly(
                    tempDir.resolve("external-evidence-requests"),
                    plan,
                    ignored -> "{}",
                    null,
                    "test-model",
                    "xhigh",
                    ArtifactRetention.FULL,
                    ignored -> {
                    });
            for (SemanticPathShard shard : plan.shards()) {
                Path planned = SemanticExternalAuditReferences.sidecar(shard.bundlePath());
                Path published = run.resolve("shards").resolve(shard.id())
                        .resolve("external-audit-refs.tsv");
                assertEquals(
                        SemanticExternalAuditReferences.read(planned),
                        SemanticExternalAuditReferences.read(published));
                assertTrue(Files.readString(published).startsWith(
                        "#semantic-external-audit-refs-v2"));
            }
        }
    }

    @Test
    void requestOnlyPackageReconstructsCompleteBundleWithoutOriginalScanOrPlannerWorkspace()
            throws Exception {
        Path input = writeMetadataOnlyScan();
        Path output = tempDir.resolve("portable-request-package");
        Path run;

        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("portable-request-session"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false));
            run = new SemanticPathRunArtifactWriter().writeRequestOnly(
                    output,
                    plan,
                    ignored -> "{}",
                    null,
                    "test-model",
                    "xhigh",
                    ArtifactRetention.FULL,
                    ignored -> {
                    });
        }

        Files.delete(input);
        assertFalse(Files.exists(run.resolve("full-evidence-bundle.json")));
        Path indexPath = run.resolve("request-bundle-index.json");
        JsonNode index = read(indexPath);
        assertEquals(2, index.path("shards").size());
        assertTrue(Files.isRegularFile(run.resolve(
                index.path("evidenceArchive").path("path").asText())));

        Path reconstructed = tempDir.resolve("reconstructed-evidence-bundle.json");
        SemanticRequestBundleReconstructor.Result result =
                new SemanticRequestBundleReconstructor().reconstruct(run, reconstructed);

        assertEquals(index.path("fullBundleCanonicalSha256").asText(), result.canonicalSha256());
        assertTrue(Files.isRegularFile(reconstructed));
        JsonNode bundle = read(reconstructed);
        assertEquals(2, bundle.path("metadataTables").size());
        assertEquals(2, bundle.path("metadataColumns").size());
        assertFalse(bundle.path("evidence").isEmpty());
    }

    @Test
    void requestOnlyPackageAcceptsBareRelativeInputFileForAuditDigest() throws Exception {
        Path input = Path.of("request-package-relative-input-" + System.nanoTime() + ".json");
        try {
            JSON.writeValue(input.toFile(), writeMetadataOnlyScanDocument());
            Path run;
            try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                    List.of(input), tempDir.resolve("relative-input-session"))) {
                SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                        session.evidenceStore(),
                        session.workPath("plan"),
                        new SemanticShardingOptions(
                                SemanticShardMode.AUTO, 10_000, 50_000, 8, false));
                run = new SemanticPathRunArtifactWriter().writeRequestOnly(
                        tempDir.resolve("relative-input-package"),
                        plan,
                        ignored -> "{}",
                        null,
                        "test-model",
                        "xhigh",
                        ArtifactRetention.FULL,
                        ignored -> {
                        });
            }

            JsonNode inputAudit = read(run.resolve("request-bundle-index.json"))
                    .path("inputScans").get(0);
            assertEquals(input.toString(), inputAudit.path("path").asText());
            assertEquals(Files.size(input), inputAudit.path("bytes").asLong());
            assertEquals(sha256(input), inputAudit.path("sha256").asText());
        } finally {
            Files.deleteIfExists(input);
        }
    }

    @Test
    void requestOnlyPackageRejectsTamperedSidecarWithoutPartialReconstruction() throws Exception {
        Path input = writeMetadataOnlyScan();
        Path output = tempDir.resolve("tampered-request-package");
        Path run;

        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("tampered-request-session"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 8, false));
            run = new SemanticPathRunArtifactWriter().writeRequestOnly(
                    output,
                    plan,
                    ignored -> "{}",
                    null,
                    "test-model",
                    "xhigh",
                    ArtifactRetention.FULL,
                    ignored -> {
                    });
        }

        JsonNode index = read(run.resolve("request-bundle-index.json"));
        Path sidecar = run.resolve(index.path("shards").get(0)
                .path("sidecar").path("path").asText());
        Files.writeString(sidecar, "tampered", java.nio.file.StandardOpenOption.APPEND);
        Path reconstructed = tempDir.resolve("tampered-reconstruction.json");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticRequestBundleReconstructor().reconstruct(run, reconstructed));
        assertFalse(Files.exists(reconstructed));
    }

    @Test
    void codexCompletionReportsMissingShardResponsesWithoutPublishing() throws Exception {
        CodexFixture fixture = codexFixture("codex-pending", false);
        Path responses = tempDir.resolve("codex-pending-responses");

        SemanticCodexSessionCompletionService.Result result =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, tempDir.resolve("codex-pending-output"));

        assertEquals(SemanticCodexSessionCompletionService.Status.PENDING, result.status());
        JsonNode pending = read(responses.resolve("pending-responses.json"));
        assertEquals(fixture.shardIds().size(), pending.path("missing").size());
        assertFalse(hasDirectory(tempDir.resolve("codex-pending-output"), "run-"));
    }

    @Test
    void codexCompletionPublishesOnlyAfterAllShardResponsesCloseOwnership() throws Exception {
        CodexFixture fixture = codexFixture("codex-complete", false);
        Path responses = tempDir.resolve("codex-complete-responses");
        String requestHash = sha256(fixture.requestRun().resolve("request-bundle-index.json"));
        writeShardResponses(fixture, responses);

        SemanticCodexSessionCompletionService.Result result =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, tempDir.resolve("codex-complete-output"));

        assertEquals(SemanticCodexSessionCompletionService.Status.COMPLETE, result.status());
        assertTrue(Files.isRegularFile(result.runDirectory().resolve("semantic-extraction-result.json")));
        assertEquals("COMPLETE", read(result.runDirectory().resolve("run-manifest.json"))
                .path("status").asText());
        assertEquals(requestHash, sha256(fixture.requestRun().resolve("request-bundle-index.json")));
        assertFalse(Files.exists(responses.resolve("pending-responses.json")));
    }

    @Test
    void codexCompletionGeneratesBudgetedReconciliationRequestBeforeFinalPublish() throws Exception {
        CodexFixture fixture = codexFixture("codex-reconcile", true);
        Path responses = tempDir.resolve("codex-reconcile-responses");
        Path output = tempDir.resolve("codex-reconcile-output");
        writeShardResponses(fixture, responses);

        SemanticCodexSessionCompletionService.Result pending =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output);

        assertEquals(SemanticCodexSessionCompletionService.Status.PENDING, pending.status());
        assertTrue(Files.isRegularFile(responses.resolve(
                "reconciliation/semantic-extraction-prompt.md")));
        assertFalse(hasDirectory(output, "run-"));

        Path patch = responses.resolve(
                "reconciliation/semantic-reconciliation-result.json");
        Files.writeString(patch, "{\"resolutions\":[],\"renames\":[]}");
        SemanticCodexSessionCompletionService.Result complete =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output);

        assertEquals(SemanticCodexSessionCompletionService.Status.COMPLETE, complete.status());
        assertTrue(Files.isRegularFile(complete.runDirectory().resolve(
                "reconciliation/patch.json")));
    }

    @Test
    void codexCompletionRejectsCrossShardOwnedGroundingWithoutPartialRun() throws Exception {
        CodexFixture fixture = codexFixture("codex-owner", false);
        Path responses = tempDir.resolve("codex-owner-responses");
        writeShardResponses(fixture, responses);
        String firstId = fixture.shardIds().get(0);
        String secondId = fixture.shardIds().get(1);
        ObjectNode firstResponse = rawSemanticDocument(read(fixture.requestRun().resolve(
                "shards/" + firstId + "/evidence-bundle.json")));
        String foreign = read(fixture.requestRun().resolve(
                "shards/" + secondId + "/evidence-bundle.json")).path("shardContext")
                .path("ownedFactRefs").get(0).asText();
        ((ObjectNode) firstResponse.path("entities").get(0)).withArray("ownedGroundingRefs")
                .removeAll().add(foreign);
        JSON.writeValue(responses.resolve("shards").resolve(firstId)
                .resolve("semantic-extraction-result.json").toFile(), firstResponse);
        Path output = tempDir.resolve("codex-owner-output");

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output));
        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void highFanoutEventKeepsAllFactsGloballyOwnedWithoutExpandingThemIntoOneShard()
            throws Exception {
        ObjectNode root = (ObjectNode) writeMetadataOnlyScanDocument();
        addRoutineLineage(root, "lineage:event", "shop.orders", "id", "id", "INSERT_SELECT");
        for (int index = 0; index < 40; index++) {
            addFanoutRelationship(root, index);
        }
        Path input = tempDir.resolve("high-fanout-event.json");
        JSON.writeValue(input.toFile(), root);

        try (SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve("high-fanout-session-work"))) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 128, false));

            assertOwnerCoverage(plan);
            SemanticPathShard eventShard = plan.shards().stream()
                    .filter(shard -> !read(shard.bundlePath()).path("eventCandidates").isEmpty())
                    .findFirst()
                    .orElseThrow();
            ObjectNode eventBundle = (ObjectNode) read(eventShard.bundlePath());
            JsonNode eventContext = eventBundle.path("shardContext");
            assertFalse(eventContext.has("externalAuditRefs"));
            assertTrue(eventContext.path("externalAuditRefCount").asInt() >= 40);
            assertEquals(64, eventContext.path("externalAuditRefsSha256").asText().length());
            JsonNode eventCandidate = eventBundle.path("eventCandidates").get(0);
            assertFalse(eventCandidate.has("relationshipRefs"));
            assertTrue(eventCandidate.path("relationshipRefCount").asInt() >= 40);
            assertEquals(64, eventCandidate.path("relationshipRefsSha256").asText().length());
            JsonNode fullEventCandidate =
                    read(plan.fullBundlePath()).path("eventCandidates").get(0);
            assertTrue(fullEventCandidate.path("relationshipRefs").size() >= 40);
            SemanticExternalAuditReferences.Snapshot relationshipSnapshot =
                    SemanticExternalAuditReferences.snapshot(
                            textSet(fullEventCandidate.path("relationshipRefs")));
            assertEquals(
                    relationshipSnapshot.count(),
                    eventCandidate.path("relationshipRefCount").asInt());
            assertEquals(
                    relationshipSnapshot.sha256(),
                    eventCandidate.path("relationshipRefsSha256").asText());
            assertEquals(
                    eventContext.path("externalAuditRefCount").asInt(),
                    SemanticExternalAuditReferences.read(
                            SemanticExternalAuditReferences.sidecar(eventShard.bundlePath())).size());

            ObjectNode tampered = eventBundle.deepCopy();
            tampered.withObject("/shardContext").put(
                    "externalAuditRefCount",
                    eventContext.path("externalAuditRefCount").asInt() + 1);
            try (SemanticPathResultStore results = new SemanticPathResultStore(
                    tempDir.resolve("external-audit-tamper-results"),
                    session.evidenceStore(),
                    plan)) {
                assertThrows(SemanticExtractionValidationException.class,
                        () -> results.append(eventShard, tampered, emptySemanticDocument()));
            }

            Path sidecar = SemanticExternalAuditReferences.sidecar(eventShard.bundlePath());
            Files.delete(sidecar);
            Set<String> unresolved = Set.of("unresolved:audit-reference");
            SemanticExternalAuditReferences.write(sidecar, unresolved);
            SemanticExternalAuditReferences.Snapshot unresolvedSnapshot =
                    SemanticExternalAuditReferences.snapshot(unresolved);
            ObjectNode unresolvedBundle = eventBundle.deepCopy();
            unresolvedBundle.withObject("/shardContext")
                    .put("externalAuditRefCount", unresolvedSnapshot.count())
                    .put("externalAuditRefsSha256", unresolvedSnapshot.sha256());
            try (SemanticPathResultStore results = new SemanticPathResultStore(
                    tempDir.resolve("external-audit-unresolved-results"),
                    session.evidenceStore(),
                    plan)) {
                assertThrows(SemanticExtractionValidationException.class,
                        () -> results.append(eventShard, unresolvedBundle, emptySemanticDocument()));
            }
        }
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
        ObjectNode raw = rawSemanticDocument(bundle);
        ObjectNode response = JSON.createObjectNode();
        response.put("output_text", raw.toString());
        response.putObject("usage").put("input_tokens", 10).put("output_tokens", 10);
        return new SemanticExtractionResult("{}", response.toString(), raw.toString(), response, 1);
    }

    private ObjectNode rawSemanticDocument(JsonNode bundle) {
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
        return raw;
    }

    private CodexFixture codexFixture(String name, boolean reconcile) throws Exception {
        Path input = writeMetadataOnlyScan();
        SemanticDiskBackedSession session = SemanticDiskBackedSession.open(
                List.of(input), tempDir.resolve(name + "-session"));
        try (session) {
            SemanticPathRunPlan plan = new SemanticPathBackedPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(
                            SemanticShardMode.FORCE, 10_000, 50_000, 8, reconcile));
            Path requestRun = new SemanticPathRunArtifactWriter().writeCodexSession(
                    tempDir.resolve(name + "-requests"),
                    plan,
                    "gpt-5.6-sol",
                    "xhigh",
                    ArtifactRetention.FULL,
                    ignored -> {
                    });
            return new CodexFixture(
                    requestRun,
                    plan.shards().stream().map(SemanticPathShard::id).toList());
        }
    }

    private void writeShardResponses(CodexFixture fixture, Path responses) throws Exception {
        for (String shardId : fixture.shardIds()) {
            Path target = responses.resolve("shards").resolve(shardId)
                    .resolve("semantic-extraction-result.json");
            Files.createDirectories(target.getParent());
            JSON.writeValue(target.toFile(), rawSemanticDocument(read(fixture.requestRun().resolve(
                    "shards/" + shardId + "/evidence-bundle.json"))));
        }
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

    private void assertPromptAuditReferencesAreSummarized(ObjectNode bundle) {
        for (String section : List.of(
                "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
                "relationships", "lineage", "derivedRelationships", "derivedLineage",
                "namingEvidence", "diagnostics", "eventCandidates",
                "reviewItemCandidates", "tripletCandidates")) {
            for (JsonNode item : bundle.path(section)) {
                for (String field : List.of(
                        "evidenceRefs", "lineageRefs",
                        "supportingDerivedLineageRefs", "relationshipRefs")) {
                    assertFalse(item.has(field), () -> section + " retains audit field " + field);
                }
            }
        }
    }

    private Set<String> textSet(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.asText()));
        return result;
    }

    private record CodexFixture(Path requestRun, List<String> shardIds) {
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
        ObjectNode root = writeMetadataOnlyScanDocument();
        Path input = tempDir.resolve("metadata-only-scan.json");
        JSON.writeValue(input.toFile(), root);
        return input;
    }

    private ObjectNode writeMetadataOnlyScanDocument() {
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
        inventory.put("basis", "LIVE_METADATA");
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
        return root;
    }

    private void addRoutineLineage(
            ObjectNode root,
            String id,
            String sourceTable,
            String sourceColumn,
            String targetColumn,
            String mappingKind
    ) {
        ObjectNode lineage = root.withArray("dataLineages").addObject();
        lineage.put("id", id);
        lineage.putArray("sources").addObject()
                .put("table", sourceTable)
                .put("column", sourceColumn);
        lineage.putObject("target")
                .put("table", "shop.audit_log")
                .put("column", targetColumn);
        lineage.put("flowKind", "VALUE");
        lineage.put("transformType", "DIRECT");
        lineage.put("confidence", 0.82);
        ObjectNode evidence = lineage.putArray("evidence").addObject();
        evidence.put("type", "DATA_LINEAGE");
        evidence.put("transformType", "DIRECT");
        evidence.put("sourceType", "PLAIN_SQL");
        evidence.put("score", 0.82);
        evidence.put("source", "procedures/audit.sql");
        evidence.put("detail", "typed routine write");
        evidence.putObject("attributes")
                .put("sourceObjectType", "PROCEDURE")
                .put("sourceObjectName", "sp_write_audit")
                .put("sourceObjectIdentity", "shop.sp_write_audit(bigint)")
                .put("sourceStatementId", "routine:sp_write_audit")
                .put("mappingKind", mappingKind);
        lineage.putArray("rawEvidence");
        lineage.putArray("warnings");
        lineage.putObject("attributes").put("mappingKind", mappingKind);
        ObjectNode summary = (ObjectNode) root.path("summary");
        int count = root.path("dataLineages").size();
        summary.put("directDataLineageCount", count);
        summary.put("totalDataLineageCount", count);
    }

    private void addFanoutRelationship(ObjectNode root, int index) {
        ObjectNode relationship = root.withArray("relationships").addObject();
        relationship.put("id", "relationship:fanout:%04d".formatted(index));
        relationship.putObject("source")
                .put("table", "shop.orders")
                .put("column", "id");
        relationship.putObject("target")
                .put("table", "shop.audit_log")
                .put("column", "id_%04d".formatted(index));
        relationship.put("relationType", "CO_OCCURRENCE");
        relationship.put("relationSubType", "COLUMN_CO_OCCURRENCE");
        relationship.put("confidence", 0.8);
        ObjectNode evidence = relationship.putArray("evidence").addObject();
        evidence.put("type", "PROCEDURE_JOIN");
        evidence.put("sourceType", "PLAIN_SQL");
        evidence.put("score", 0.8);
        evidence.put("source", "procedures/audit.sql");
        evidence.put("detail", "x".repeat(20_000));
        evidence.putObject("attributes");
        relationship.putArray("rawEvidence");
        relationship.putArray("warnings");
        relationship.putObject("attributes");
        ObjectNode summary = (ObjectNode) root.path("summary");
        int count = root.path("relationships").size();
        summary.put("directRelationshipCount", count);
        summary.put("totalRelationshipCount", count);
    }

    private JsonNode read(Path path) {
        try {
            return JSON.readTree(path.toFile());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
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
        inventory.put("basis", "LIVE_METADATA");
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
