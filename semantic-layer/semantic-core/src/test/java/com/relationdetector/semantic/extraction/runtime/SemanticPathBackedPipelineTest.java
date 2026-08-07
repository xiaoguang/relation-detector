package com.relationdetector.semantic.extraction.runtime;

import com.relationdetector.semantic.extraction.runtime.SemanticCodexSessionCompletionService;

import com.relationdetector.semantic.extraction.artifact.SemanticRunArtifactWriter;

import com.relationdetector.semantic.extraction.artifact.SemanticResultStore;

import com.relationdetector.semantic.extraction.artifact.SemanticRequestBundleReconstructor;
import com.relationdetector.semantic.extraction.artifact.SemanticRequestPackageLimits;
import com.relationdetector.semantic.extraction.artifact.SemanticCodexRequestSnapshot;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.shard.SemanticShardPlanner;

import com.relationdetector.semantic.extraction.artifact.SemanticExternalAuditReferences;
import com.relationdetector.semantic.extraction.artifact.SemanticArtifactRef;
import com.relationdetector.semantic.internal.io.SemanticFileDigest;
import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;
import com.relationdetector.semantic.extraction.normalization.SemanticCanonicalIdentity;

import com.relationdetector.semantic.extraction.config.SemanticShardingOptions;

import com.relationdetector.semantic.extraction.config.SemanticShardMode;

import com.relationdetector.semantic.extraction.config.ArtifactRetention;
import com.relationdetector.semantic.facade.SemanticExtractionFacade;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
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
import com.relationdetector.semantic.extraction.runtime.SemanticProcessingSession;
import com.relationdetector.semantic.ingest.ScanResultReader;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.ingest.SemanticInputStore;

final class SemanticPathBackedPipelineTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int SHARD_OUTPUT_TOKENS = 24_000;
    private static final int RECONCILIATION_OUTPUT_TOKENS = 16_000;

    @TempDir
    Path tempDir;

    @Test
    void metadataOnlyTablesRemainOwnedFactsInSeparateTypedShards() throws Exception {
        Path input = writeMetadataOnlyScan();
        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("session-work"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);

            assertEquals(2, plan.shards().size());
            for (SemanticShardDescriptor shard : plan.shards()) {
                JsonNode bundle = JSON.readTree(shard.bundle().path().toFile());
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
        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("empty-session-work"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);

            assertEquals(1, plan.shards().size());
            SemanticShardDescriptor shard = plan.shards().get(0);
            assertEquals("shard-0001", shard.id());
            assertEquals("global", shard.ownerKey());
            assertEquals(0, shard.ownedFactCount());
            assertEquals(0, shard.ownedCandidateCount());
            assertEquals(0, Files.size(plan.ownerManifest().path()));

            JsonNode context = JSON.readTree(shard.bundle().path().toFile()).path("shardContext");
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

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("failure-session-work"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);

            assertThrows(IllegalStateException.class, () ->
                    new SemanticRunArtifactWriter().executeAndWrite(
                            output,
                            plan,
                            session.evidenceStore(),
                            (prompt, context) -> {
                                if (calls.incrementAndGet() == 2) {
                                    throw new IllegalStateException("synthetic second-shard failure");
                                }
                                return modelResult(prompt.evidenceBundle(), context);
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
    void mutatedPlanArtifactFailsBeforeTheFirstModelCallAndPublishesNothing() throws Exception {
        Path input = writeMetadataOnlyScan();
        Path output = tempDir.resolve("mutated-plan-runs");
        AtomicInteger calls = new AtomicInteger();

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("mutated-plan-session"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(
                            SemanticShardMode.FORCE, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS,
                    RECONCILIATION_OUTPUT_TOKENS);
            Files.writeString(plan.shards().get(0).bundle().path(), "{}");

            assertThrows(SemanticExtractionValidationException.class,
                    () -> new SemanticRunArtifactWriter().executeAndWrite(
                            output,
                            plan,
                            session.evidenceStore(),
                            (prompt, context) -> {
                                calls.incrementAndGet();
                                return modelResult(prompt.evidenceBundle(), context);
                            },
                            null,
                            "test-provider",
                            "test-model",
                            "test-effort",
                            ArtifactRetention.FULL,
                            ignored -> {
                            }));
        }

        assertEquals(0, calls.get());
        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void maliciousClientArtifactOutsideFixedScratchPublishesNothing() throws Exception {
        Path input = writeMetadataOnlyScan();
        Path output = tempDir.resolve("outside-client-runs");

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("outside-client-session"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(
                            SemanticShardMode.FORCE, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS,
                    RECONCILIATION_OUTPUT_TOKENS);

            assertThrows(SemanticExtractionValidationException.class,
                    () -> new SemanticRunArtifactWriter().executeAndWrite(
                            output,
                            plan,
                            session.evidenceStore(),
                            (prompt, context) -> {
                                SemanticModelCallResult valid =
                                        modelResult(prompt.evidenceBundle(), context);
                                Path outside = tempDir.resolve("outside-client-output.json");
                                try {
                                    Files.writeString(outside, "{}");
                                    return new SemanticModelCallResult(
                                            valid.request(), valid.response(), artifact(outside),
                                            valid.inputTokens(), valid.outputTokens(),
                                            valid.transportAttempts());
                                } catch (Exception failure) {
                                    throw new IllegalStateException(failure);
                                }
                            },
                            null,
                            "malicious-provider",
                            "test-model",
                            "test-effort",
                            ArtifactRetention.FULL,
                            ignored -> {
                            }));
        }

        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void mutatedPlanArtifactFailsBeforeTheFirstRequestRender() throws Exception {
        Path input = writeMetadataOnlyScan();
        SemanticRunPlan plan = plan(input, "mutated-render-plan", 1024);
        Files.writeString(plan.ownerManifest().path(), "mutated");
        Path output = tempDir.resolve("mutated-render-runs");
        AtomicInteger renders = new AtomicInteger();

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticRunArtifactWriter().writeRequestOnly(
                        output,
                        plan,
                        (prompt, target) -> {
                            renders.incrementAndGet();
                            return renderRequest(prompt, target);
                        },
                        null,
                        "test-model",
                        "xhigh",
                        ArtifactRetention.FULL,
                        ignored -> {
                        }));

        assertEquals(0, renders.get());
        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void rawBufferSizeDoesNotChangeGlobalOwnerManifestOrShardBundles() throws Exception {
        Path input = writeMetadataOnlyScan();

        SemanticRunPlan large = plan(input, "large", 1024 * 1024);
        SemanticRunPlan tiny = plan(input, "tiny", 1);

        assertEquals(
                Files.readString(large.ownerManifest().path()),
                Files.readString(tiny.ownerManifest().path()));
        assertEquals(shardFingerprints(large), shardFingerprints(tiny));
        assertOwnerCoverage(large);
        assertOwnerCoverage(tiny);
    }

    @Test
    void shardsKeepStableEvidenceRefsWithoutDuplicatingFullEvidencePayloads() throws Exception {
        Path input = writeMetadataOnlyScan();

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("external-evidence-session"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 128, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);

            assertFalse(read(plan.fullBundle().path()).path("evidence").isEmpty());
            for (SemanticShardDescriptor shard : plan.shards()) {
                ObjectNode bundle = (ObjectNode) read(shard.bundle().path());
                assertTrue(bundle.path("evidence").isEmpty());
                int externalAuditRefCount =
                        bundle.path("shardContext").path("externalAuditRefCount").asInt();
                assertTrue(externalAuditRefCount > 0);
                assertEquals(
                        externalAuditRefCount,
                        SemanticExternalAuditReferences.read(
                                shard.externalAuditSidecar().path()).size());
                assertPromptAuditReferencesAreSummarized(bundle);
            }

            Path run = new SemanticRunArtifactWriter().writeRequestOnly(
                    tempDir.resolve("external-evidence-requests"),
                    plan,
                    this::renderRequest,
                    null,
                    "test-model",
                    "xhigh",
                    ArtifactRetention.FULL,
                    ignored -> {
                    });
            for (SemanticShardDescriptor shard : plan.shards()) {
                Path planned = shard.externalAuditSidecar().path();
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

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("portable-request-session"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);
            run = new SemanticRunArtifactWriter().writeRequestOnly(
                    output,
                    plan,
                    this::renderRequest,
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
    void legacyV1RequestPackageRemainsReconstructable() throws Exception {
        CodexFixture fixture = codexFixture("legacy-v1-reconstruction", false);
        Path indexPath = fixture.requestRun().resolve("request-bundle-index.json");
        ObjectNode index = (ObjectNode) read(indexPath);
        index.put("artifactSchemaVersion", 1);
        index.remove("shardMaxOutputTokens");
        index.remove("reconciliationMaxOutputTokens");
        JSON.writeValue(indexPath.toFile(), index);
        Path reconstructed = tempDir.resolve("legacy-v1-reconstructed-bundle.json");

        SemanticRequestBundleReconstructor.Result result =
                new SemanticRequestBundleReconstructor().reconstruct(
                        fixture.requestRun(), reconstructed);

        assertEquals(index.path("fullBundleCanonicalSha256").asText(), result.canonicalSha256());
        assertTrue(Files.isRegularFile(reconstructed));
    }

    @Test
    void requestOnlyPackageAcceptsBareRelativeInputFileForAuditDigest() throws Exception {
        Path input = Path.of("request-package-relative-input-" + System.nanoTime() + ".json");
        try {
            JSON.writeValue(input.toFile(), writeMetadataOnlyScanDocument());
            Path run;
            try (SemanticProcessingSession session = SemanticProcessingSession.open(
                    List.of(input), tempDir.resolve("relative-input-session"),
                    SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
                SemanticRunPlan plan = new SemanticShardPlanner().plan(
                        session.evidenceStore(),
                        session.workPath("plan"),
                        new SemanticShardingOptions(
                                SemanticShardMode.AUTO, 10_000, 50_000, 8, false),
                        SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);
                run = new SemanticRunArtifactWriter().writeRequestOnly(
                        tempDir.resolve("relative-input-package"),
                        plan,
                        this::renderRequest,
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

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("tampered-request-session"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);
            run = new SemanticRunArtifactWriter().writeRequestOnly(
                    output,
                    plan,
                    this::renderRequest,
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
    void codexCompletionRejectsOversizedManifestBeforePublishing() throws Exception {
        CodexFixture fixture = codexFixture("codex-oversized-manifest", false);
        SemanticRequestPackageLimits limits = tinyControlDocumentLimits();
        Path manifest = fixture.requestRun().resolve("run-manifest.json");
        assertTrue(Files.size(manifest) <= limits.maxIndexBytes());
        writeOversizedControlObject(manifest);
        assertTrue(Files.size(manifest) > limits.maxIndexBytes());

        assertCompletionRejectedWithoutPublication(
                fixture,
                "oversized-manifest",
                new SemanticCodexSessionCompletionService(limits));
    }

    @Test
    void codexCompletionRejectsOversizedIndexBeforePublishing() throws Exception {
        CodexFixture fixture = codexFixture("codex-oversized-index", false);
        SemanticRequestPackageLimits limits = tinyControlDocumentLimits();
        Path index = fixture.requestRun().resolve("request-bundle-index.json");
        assertTrue(Files.size(index) <= limits.maxIndexBytes());
        writeOversizedControlObject(index);
        assertTrue(Files.size(index) > limits.maxIndexBytes());

        assertCompletionRejectedWithoutPublication(
                fixture,
                "oversized-index",
                new SemanticCodexSessionCompletionService(limits));
    }

    @Test
    void codexCompletionRejectsSymlinkManifestAndIndexBeforePublishing() throws Exception {
        for (String control : List.of("run-manifest.json", "request-bundle-index.json")) {
            String name = control.substring(0, control.indexOf('.'));
            CodexFixture fixture = codexFixture("codex-symlink-" + name, false);
            Path path = fixture.requestRun().resolve(control);
            Path real = tempDir.resolve("codex-symlink-" + name + "-source.json");
            Files.move(path, real);
            Files.createSymbolicLink(path, real);

            assertCompletionRejectedWithoutPublication(fixture, "symlink-" + name);
        }
    }

    @Test
    void codexCompletionRejectsPackageBudgetsAboveTrustedLimitsBeforePublishing()
            throws Exception {
        for (String field : List.of(
                "maxInputTokens",
                "shardMaxOutputTokens",
                "reconciliationMaxOutputTokens")) {
            CodexFixture fixture = codexFixture("codex-escalated-" + field, false);
            Path indexPath = fixture.requestRun().resolve("request-bundle-index.json");
            ObjectNode index = (ObjectNode) read(indexPath);
            index.put(field, 8_000_001);
            JSON.writeValue(indexPath.toFile(), index);

            assertCompletionRejectedWithoutPublication(fixture, "escalated-" + field);
        }
    }

    @Test
    void codexRequestPackagePersistsBothConfiguredOutputBudgets() throws Exception {
        CodexFixture fixture = codexFixture("codex-budget-index", false, 321, 123);

        JsonNode index = read(fixture.requestRun().resolve("request-bundle-index.json"));
        JsonNode manifest = read(fixture.requestRun().resolve("run-manifest.json"));

        assertEquals(2, index.path("artifactSchemaVersion").asInt());
        assertEquals(321, index.path("shardMaxOutputTokens").asInt());
        assertEquals(123, index.path("reconciliationMaxOutputTokens").asInt());
        assertEquals(321, manifest.path("shardMaxOutputTokens").asInt());
        assertEquals(123, manifest.path("reconciliationMaxOutputTokens").asInt());
    }

    @Test
    void codexCompletionRejectsShardResponseAbovePersistedOutputBudget() throws Exception {
        CodexFixture fixture = codexFixture("codex-shard-budget", false, 500, 16_000);
        Path responses = tempDir.resolve("codex-shard-budget-responses");
        writeShardResponses(fixture, responses);
        String first = fixture.shardIds().get(0);
        Path oversized = responses.resolve("shards").resolve(first)
                .resolve("semantic-extraction-result.json");
        ObjectNode response = (ObjectNode) read(oversized);
        response.put("untrustedPadding", "x".repeat(20_000));
        JSON.writeValue(oversized.toFile(), response);
        Path output = tempDir.resolve("codex-shard-budget-output");

        SemanticExtractionValidationException failure = assertThrows(
                SemanticExtractionValidationException.class,
                () -> new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output));

        assertTrue(failure.getMessage().contains("semantic Codex shard result exceeds"));
        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void codexCompletionRejectsReconciliationResponseAboveItsIndependentOutputBudget() throws Exception {
        CodexFixture fixture = codexFixture("codex-reconciliation-budget", true, 24_000, 100);
        Path responses = tempDir.resolve("codex-reconciliation-budget-responses");
        Path output = tempDir.resolve("codex-reconciliation-budget-output");
        writeShardResponses(fixture, responses);
        SemanticCodexSessionCompletionService.Result pending =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output);
        assertEquals(SemanticCodexSessionCompletionService.Status.PENDING, pending.status());
        Path patch = responses.resolve("reconciliation/semantic-reconciliation-result.json");
        Files.writeString(patch, "{\"resolutions\":[],\"renames\":[],\"padding\":\""
                + "x".repeat(20_000) + "\"}");

        SemanticExtractionValidationException failure = assertThrows(
                SemanticExtractionValidationException.class,
                () -> new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output));

        assertTrue(failure.getMessage().contains("semantic Codex reconciliation result exceeds"));
        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void codexCompletionRejectsLegacyRequestPackageWithoutOutputBudgets() throws Exception {
        CodexFixture fixture = codexFixture("codex-legacy-budget", false);
        Path indexPath = fixture.requestRun().resolve("request-bundle-index.json");
        ObjectNode index = (ObjectNode) read(indexPath);
        index.put("artifactSchemaVersion", 1);
        index.remove("shardMaxOutputTokens");
        index.remove("reconciliationMaxOutputTokens");
        JSON.writeValue(indexPath.toFile(), index);

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), tempDir.resolve("codex-legacy-responses"),
                        tempDir.resolve("codex-legacy-output")));
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
        assertDoesNotThrow(() -> new SemanticExtractionFacade().verifyCompletedCodexRun(
                result.runDirectory(), "gpt-5.6-sol", "xhigh"));
    }

    @Test
    void completedRunVerifierRejectsArtifactTampering() throws Exception {
        CodexFixture fixture = codexFixture("codex-complete-tamper", false);
        Path responses = tempDir.resolve("codex-complete-tamper-responses");
        writeShardResponses(fixture, responses);
        SemanticCodexSessionCompletionService.Result result =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses,
                        tempDir.resolve("codex-complete-tamper-output"));
        Files.writeString(
                result.runDirectory().resolve("semantic-extraction-result.json"),
                "\n",
                java.nio.charset.StandardCharsets.UTF_8,
                java.nio.file.StandardOpenOption.APPEND);

        assertThrows(SemanticExtractionValidationException.class,
                () -> new SemanticExtractionFacade().verifyCompletedCodexRun(
                        result.runDirectory(), "gpt-5.6-sol", "xhigh"));
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
    void codexCompletionRejectsIdentityChangingRenameWithoutPublishing() throws Exception {
        CodexFixture fixture = codexFixture("codex-reject-identity-rename", true);
        Path responses = tempDir.resolve("codex-reject-identity-rename-responses");
        Path output = tempDir.resolve("codex-reject-identity-rename-output");
        writeShardResponses(fixture, responses);
        SemanticIdentityFixture identity = addBusinessEntityResponse(fixture, responses);

        SemanticCodexSessionCompletionService.Result pending =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output);
        assertEquals(SemanticCodexSessionCompletionService.Status.PENDING, pending.status());

        ObjectNode patch = emptyPatch();
        patch.withArray("renames").addObject()
                .put("section", "entities")
                .put("id", identity.businessEntityId())
                .put("name", "Materially different")
                .put("description", "Description changes remain allowed");
        JSON.writeValue(responses.resolve(
                "reconciliation/semantic-reconciliation-result.json").toFile(), patch);

        assertThrows(
                SemanticExtractionValidationException.class,
                () -> new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output));
        assertFalse(hasDirectory(output, "run-"));
    }

    @Test
    void codexCompletionKeepsFormalReviewAndGraphIdsAcrossSafeRenames() throws Exception {
        CodexFixture fixture = codexFixture("codex-safe-renames", true);
        Path responses = tempDir.resolve("codex-safe-renames-responses");
        Path output = tempDir.resolve("codex-safe-renames-output");
        writeShardResponses(fixture, responses);
        SemanticIdentityFixture identity = addBusinessEntityResponse(fixture, responses);

        SemanticCodexSessionCompletionService.Result pending =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output);
        assertEquals(SemanticCodexSessionCompletionService.Status.PENDING, pending.status());

        ObjectNode patch = emptyPatch();
        patch.withArray("renames").addObject()
                .put("section", "entities")
                .put("id", identity.businessEntityId())
                .putNull("name")
                .put("description", "Curated business description");
        patch.withArray("renames").addObject()
                .put("section", "entities")
                .put("id", identity.physicalEntityId())
                .put("name", "Customer orders")
                .putNull("description");
        JSON.writeValue(responses.resolve(
                "reconciliation/semantic-reconciliation-result.json").toFile(), patch);

        SemanticCodexSessionCompletionService.Result complete =
                new SemanticCodexSessionCompletionService().complete(
                        fixture.requestRun(), responses, output);
        JsonNode document = read(complete.runDirectory().resolve(
                "semantic-extraction-result.json"));
        JsonNode business = itemWithId(document.path("entities"), identity.businessEntityId());
        JsonNode physical = itemWithId(document.path("entities"), identity.physicalEntityId());
        String reviewId = SemanticCanonicalIdentity.review(
                identity.businessEntityId(), "entities", "REVIEW_NEEDED");

        assertEquals("Order domain", business.path("name").asText());
        assertEquals("Curated business description", business.path("description").asText());
        assertEquals("Customer orders", physical.path("name").asText());
        assertEquals(identity.physicalName(), physical.path("physicalName").asText());
        assertEquals(identity.businessEntityId(), business.path("id").asText());
        assertEquals(identity.physicalEntityId(), physical.path("id").asText());
        assertEquals(identity.businessEntityId(),
                itemWithId(document.path("reviewItems"), reviewId).path("targetRef").asText());
        assertEquals(identity.businessEntityId(), itemWithId(
                document.path("semanticGraph").path("nodes"),
                identity.businessEntityId()).path("id").asText());
        assertEquals(reviewId, itemWithId(
                document.path("semanticGraph").path("nodes"), reviewId).path("id").asText());
        assertEquals(1, document.path("validation").path("generatedReviewItemCount").asInt(),
                "the final count must include the unique generated review that survived canonical merge");
    }

    @Test
    void codexRequestSnapshotCleansExpectedNewRootAfterLateManifestFailure() throws Exception {
        CodexFixture fixture = codexFixture("codex-snapshot-cleanup", false);
        Path manifestPath = fixture.requestRun().resolve("run-manifest.json");
        ObjectNode manifest = (ObjectNode) read(manifestPath);
        manifest.put("shardCount", fixture.shardIds().size() + 1);
        JSON.writeValue(manifestPath.toFile(), manifest);
        Path snapshotRoot = tempDir.resolve("codex-snapshot-cleanup-root");

        assertThrows(
                SemanticExtractionValidationException.class,
                () -> SemanticCodexRequestSnapshot.capture(
                        fixture.requestRun(), snapshotRoot,
                        SemanticRequestPackageLimits.defaults()));
        assertFalse(Files.exists(snapshotRoot));
    }

    @Test
    void codexRequestSnapshotRejectsAndPreservesExistingRoot() throws Exception {
        CodexFixture fixture = codexFixture("codex-snapshot-existing", false);
        Path snapshotRoot = tempDir.resolve("codex-snapshot-existing-root");
        Files.createDirectory(snapshotRoot);
        Path marker = snapshotRoot.resolve("marker.txt");
        Files.writeString(marker, "caller-owned", java.nio.charset.StandardCharsets.UTF_8);

        assertThrows(
                SemanticExtractionValidationException.class,
                () -> SemanticCodexRequestSnapshot.capture(
                        fixture.requestRun(), snapshotRoot,
                        SemanticRequestPackageLimits.defaults()));
        assertEquals("caller-owned", Files.readString(marker));
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

        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve("high-fanout-session-work"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS)) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(SemanticShardMode.AUTO, 10_000, 50_000, 128, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);

            assertOwnerCoverage(plan);
            SemanticShardDescriptor eventShard = plan.shards().stream()
                    .filter(shard -> !read(shard.bundle().path()).path("eventCandidates").isEmpty())
                    .findFirst()
                    .orElseThrow();
            ObjectNode eventBundle = (ObjectNode) read(eventShard.bundle().path());
            JsonNode eventContext = eventBundle.path("shardContext");
            assertFalse(eventContext.has("externalAuditRefs"));
            assertTrue(eventContext.path("externalAuditRefCount").asInt() >= 40);
            assertEquals(64, eventContext.path("externalAuditRefsSha256").asText().length());
            JsonNode eventCandidate = eventBundle.path("eventCandidates").get(0);
            assertFalse(eventCandidate.has("relationshipRefs"));
            assertTrue(eventCandidate.path("relationshipRefCount").asInt() >= 40);
            assertEquals(64, eventCandidate.path("relationshipRefsSha256").asText().length());
            JsonNode fullEventCandidate =
                    read(plan.fullBundle().path()).path("eventCandidates").get(0);
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
                            eventShard.externalAuditSidecar().path()).size());

            ObjectNode tampered = eventBundle.deepCopy();
            tampered.withObject("/shardContext").put(
                    "externalAuditRefCount",
                    eventContext.path("externalAuditRefCount").asInt() + 1);
            try (SemanticResultStore results = new SemanticResultStore(
                    tempDir.resolve("external-audit-tamper-results"),
                    session.evidenceStore(),
                    plan)) {
                assertThrows(SemanticExtractionValidationException.class,
                        () -> results.append(eventShard, tampered, emptySemanticDocument()));
            }

            Path sidecar = eventShard.externalAuditSidecar().path();
            Files.delete(sidecar);
            Set<String> unresolved = Set.of("unresolved:audit-reference");
            SemanticExternalAuditReferences.write(sidecar, unresolved);
            SemanticExternalAuditReferences.Snapshot unresolvedSnapshot =
                    SemanticExternalAuditReferences.snapshot(unresolved);
            ObjectNode unresolvedBundle = eventBundle.deepCopy();
            unresolvedBundle.withObject("/shardContext")
                    .put("externalAuditRefCount", unresolvedSnapshot.count())
                    .put("externalAuditRefsSha256", unresolvedSnapshot.sha256());
            try (SemanticResultStore results = new SemanticResultStore(
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
        SemanticRunPlan plan = plan(input, "owner-validation", 1);
        SemanticShardDescriptor first = plan.shards().get(0);
        ObjectNode bundle = (ObjectNode) JSON.readTree(first.bundle().path().toFile());

        String firstLine = Files.readAllLines(plan.ownerManifest().path()).get(0);
        Files.writeString(
                plan.ownerManifest().path(),
                Files.readString(plan.ownerManifest().path()) + firstLine + System.lineSeparator());
        SemanticRunPlan duplicateManifestPlan = new SemanticRunPlan(
                plan.fullBundle(),
                plan.shards(),
                plan.reconcile(),
                plan.maxInputTokens(),
                plan.shardMaxOutputTokens(),
                plan.reconciliationMaxOutputTokens(),
                artifact(plan.ownerManifest().path()));
        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                     List.of(input), tempDir.resolve("duplicate-owner-session"),
                     SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS);
             SemanticResultStore results = new SemanticResultStore(
                     tempDir.resolve("duplicate-owner-results"),
                     session.evidenceStore(),
                     duplicateManifestPlan)) {
            assertThrows(SemanticExtractionValidationException.class,
                    () -> results.append(first, bundle, emptySemanticDocument()));
        }

        SemanticRunPlan clean = plan(input, "owner-intersection", 1);
        SemanticShardDescriptor cleanFirst = clean.shards().get(0);
        ObjectNode intersecting = (ObjectNode) JSON.readTree(cleanFirst.bundle().path().toFile());
        String owned = intersecting.path("shardContext").path("ownedFactRefs").get(0).asText();
        intersecting.withObject("/shardContext").withArray("overlapRefs").add(owned);
        try (SemanticProcessingSession session = SemanticProcessingSession.open(
                     List.of(input), tempDir.resolve("intersecting-owner-session"),
                     SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS);
             SemanticResultStore results = new SemanticResultStore(
                     tempDir.resolve("intersecting-owner-results"), session.evidenceStore(), clean)) {
            assertThrows(SemanticExtractionValidationException.class,
                    () -> results.append(cleanFirst, intersecting, emptySemanticDocument()));
        }
    }

    private SemanticModelCallResult modelResult(
            JsonNode bundle,
            SemanticModelCallContext context
    ) {
        ObjectNode raw = rawSemanticDocument(bundle);
        ObjectNode request = JSON.createObjectNode();
        request.put("max_output_tokens", context.maxOutputTokens());
        ObjectNode response = JSON.createObjectNode();
        response.put("output_text", raw.toString());
        response.putObject("usage").put("input_tokens", 10).put("output_tokens", 10);
        try {
            JSON.writeValue(context.requestPath().toFile(), request);
            JSON.writeValue(context.responsePath().toFile(), response);
            JSON.writeValue(context.outputPath().toFile(), raw);
            return new SemanticModelCallResult(
                    artifact(context.requestPath()),
                    artifact(context.responsePath()),
                    artifact(context.outputPath()),
                    10, 10, 1);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
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
        return codexFixture(
                name, reconcile, SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);
    }

    private CodexFixture codexFixture(
            String name,
            boolean reconcile,
            int shardMaxOutputTokens,
            int reconciliationMaxOutputTokens
    ) throws Exception {
        Path input = writeMetadataOnlyScan();
        SemanticProcessingSession session = SemanticProcessingSession.open(
                List.of(input), tempDir.resolve(name + "-session"),
                SemanticEvidenceStore.DEFAULT_MAX_INPUT_TOKENS);
        try (session) {
            SemanticRunPlan plan = new SemanticShardPlanner().plan(
                    session.evidenceStore(),
                    session.workPath("plan"),
                    new SemanticShardingOptions(
                            SemanticShardMode.FORCE, 10_000, 50_000, 8, reconcile),
                    shardMaxOutputTokens, reconciliationMaxOutputTokens);
            Path requestRun = new SemanticRunArtifactWriter().writeCodexSession(
                    tempDir.resolve(name + "-requests"),
                    plan,
                    "gpt-5.6-sol",
                    "xhigh",
                    ArtifactRetention.FULL,
                    ignored -> {
                    });
            return new CodexFixture(
                    requestRun,
                    plan.shards().stream().map(SemanticShardDescriptor::id).toList());
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

    private SemanticIdentityFixture addBusinessEntityResponse(
            CodexFixture fixture,
            Path responses
    ) throws Exception {
        String shardId = fixture.shardIds().get(0);
        JsonNode bundle = read(fixture.requestRun().resolve(
                "shards/" + shardId + "/evidence-bundle.json"));
        String ownedRef = bundle.path("shardContext").path("ownedFactRefs").get(0).asText();
        String physicalName = bundle.path("metadataTables").get(0).path("table").asText();
        String businessId = SemanticCanonicalIdentity.entity(
                null,
                "Order domain",
                "BUSINESS_ENTITY",
                null,
                List.of(ownedRef)).canonicalId();
        String physicalId = SemanticCanonicalIdentity.entity(
                physicalName,
                physicalName,
                null,
                "PHYSICAL_ENTITY",
                List.of(ownedRef)).canonicalId();
        Path response = responses.resolve("shards").resolve(shardId)
                .resolve("semantic-extraction-result.json");
        ObjectNode document = (ObjectNode) read(response);
        ObjectNode business = document.withArray("entities").addObject()
                .put("name", "Order domain")
                .put("machineType", "BUSINESS_ENTITY")
                .put("reviewStatus", "REVIEW_NEEDED");
        business.putArray("ownedGroundingRefs").add(ownedRef);
        business.putArray("evidenceRefs").add(ownedRef);
        JSON.writeValue(response.toFile(), document);
        return new SemanticIdentityFixture(businessId, physicalId, physicalName);
    }

    private JsonNode itemWithId(JsonNode values, String id) {
        for (JsonNode value : values) {
            if (id.equals(value.path("id").asText(""))) {
                return value;
            }
        }
        throw new AssertionError("missing semantic item " + id);
    }

    private ObjectNode emptyPatch() {
        ObjectNode patch = JSON.createObjectNode();
        patch.putArray("resolutions");
        patch.putArray("renames");
        return patch;
    }

    private void assertCompletionRejectedWithoutPublication(
            CodexFixture fixture,
            String name
    ) throws Exception {
        assertCompletionRejectedWithoutPublication(
                fixture, name, new SemanticCodexSessionCompletionService());
    }

    private void assertCompletionRejectedWithoutPublication(
            CodexFixture fixture,
            String name,
            SemanticCodexSessionCompletionService service
    ) throws Exception {
        Path responses = tempDir.resolve(name + "-responses");
        Path output = tempDir.resolve(name + "-output");

        assertThrows(
                SemanticExtractionValidationException.class,
                () -> service.complete(
                        fixture.requestRun(), responses, output));

        assertFalse(Files.exists(responses.resolve("pending-responses.json")));
        assertFalse(Files.exists(output) && hasDirectory(output, "run-"));
    }

    private void writeOversizedControlObject(Path path) throws Exception {
        byte[] document = Files.readAllBytes(path);
        int end = document.length - 1;
        while (end >= 0 && Character.isWhitespace(document[end])) {
            end--;
        }
        assertEquals('}', document[end]);
        byte[] chunk = "x".repeat(64 * 1024).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try (OutputStream output = Files.newOutputStream(path)) {
            output.write(document, 0, end);
            output.write(",\"oversizedPadding\":\"".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            output.write(chunk);
            output.write("\"}\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private SemanticRequestPackageLimits tinyControlDocumentLimits() {
        SemanticRequestPackageLimits defaults = SemanticRequestPackageLimits.defaults();
        return new SemanticRequestPackageLimits(
                32 * 1024,
                defaults.maxShards(),
                defaults.maxEstimatedTokensPerShardOrRecord(),
                defaults.maxOwnerManifestBytes(),
                defaults.maxSidecarBytes(),
                defaults.maxCompressedEvidenceBytes(),
                defaults.maxReconstructedBytes(),
                defaults.maxLineBytes(),
                defaults.maxJsonDepth(),
                defaults.maxStringCodePoints());
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

    private SemanticRunPlan plan(Path input, String prefix, long bufferBytes) throws Exception {
        Path inputWork = tempDir.resolve(prefix + "-input-work");
        Path evidenceWork = tempDir.resolve(prefix + "-evidence-work");
        try (SemanticInputStore store = new ScanResultReader().open(List.of(input), inputWork);
             SemanticEvidenceStore evidence = new SemanticEvidenceStore(store, evidenceWork, bufferBytes)) {
            return new SemanticShardPlanner().plan(
                    evidence,
                    tempDir.resolve(prefix + "-plan"),
                    new SemanticShardingOptions(SemanticShardMode.FORCE, 10_000, 50_000, 8, false),
                    SHARD_OUTPUT_TOKENS, RECONCILIATION_OUTPUT_TOKENS);
        }
    }

    private List<String> shardFingerprints(SemanticRunPlan plan) throws Exception {
        List<String> result = new ArrayList<>();
        for (SemanticShardDescriptor shard : plan.shards()) {
            result.add(com.relationdetector.semantic.StableSemanticId.canonicalJson(
                    JSON.readTree(shard.bundle().path().toFile())));
        }
        return result;
    }

    private void assertOwnerCoverage(SemanticRunPlan plan) throws Exception {
        Set<String> owned = new LinkedHashSet<>();
        for (SemanticShardDescriptor shard : plan.shards()) {
            JsonNode context = JSON.readTree(shard.bundle().path().toFile()).path("shardContext");
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
        assertEquals(Files.readAllLines(plan.ownerManifest().path()).size(), owned.size());
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

    private record SemanticIdentityFixture(
            String businessEntityId,
            String physicalEntityId,
            String physicalName
    ) {
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

    private SemanticArtifactRef artifact(Path path) throws Exception {
        SemanticFileDigest.Digest digest = SemanticFileDigest.computeNoFollow(path);
        return new SemanticArtifactRef(path, digest.bytes(), digest.sha256());
    }

    private SemanticArtifactRef renderRequest(
            SemanticExtractionPrompt ignored,
            Path target
    ) {
        try {
            Files.writeString(target, "{}");
            return artifact(target);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
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
