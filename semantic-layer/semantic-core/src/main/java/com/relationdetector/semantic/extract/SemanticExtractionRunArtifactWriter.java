package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 在独立 staging 目录先原子写入 IN_PROGRESS manifest，再写 payload 并以原子 manifest 替换记录终态；
 * full 直接发布完整 staging，final-only 从完整 staging 构建独立精简候选后发布。普通失败留下完整
 * staging 与 FAILED；若终态更新本身失败则保留最后一个可解析状态。
 * EN: Atomically writes an IN_PROGRESS manifest before any payload, then records terminal state through atomic
 * manifest replacement. Full retention publishes complete staging, while final-only publishes an independently
 * built reduced candidate. Ordinary failures retain complete staging with FAILED; a failed terminal update preserves
 * the last parseable state.
 */
public final class SemanticExtractionRunArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Consumer<Path> NO_SHARED_ARTIFACTS = ignored -> {
    };
    private final SemanticRequestArtifactWriter requestWriter = new SemanticRequestArtifactWriter();
    private final RunArtifactPublisher publisher;
    private final RunArtifactFileStore files = new RunArtifactFileStore(JSON);
    private final SemanticExtractionRunManifest manifests =
            new SemanticExtractionRunManifest(JSON, files);
    private final SemanticRunAuditArtifactWriter audits = new SemanticRunAuditArtifactWriter(files);

    public SemanticExtractionRunArtifactWriter() {
        this(new RunArtifactPublisher());
    }

    SemanticExtractionRunArtifactWriter(RunArtifactPublisher publisher) {
        if (publisher == null) {
            throw new IllegalArgumentException("semantic extraction artifact publisher is required");
        }
        this.publisher = publisher;
    }

    public Path writeCodexSession(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        return writeCodexSession(
                outputRoot, plan, model, reasoningEffort, retention, NO_SHARED_ARTIFACTS);
    }

    public Path writeCodexSession(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        return publish(
                outputRoot,
                plan,
                "AWAITING_MODEL_RESULTS",
                List.of(),
                null,
                "codex-session",
                model,
                reasoningEffort,
                retention,
                sharedArtifactWriter,
                output -> {
                    prepare(output, plan);
                    for (SemanticShardRequest request : plan.shardRequests()) {
                        requestWriter.writeCodexSessionRequest(
                                shardDirectory(output, request.shard().id()), request.prompt());
                    }
                    writeReconciliationTemplate(output, plan, null, true);
                });
    }

    public Path writeRequestOnly(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            Function<SemanticExtractionPrompt, String> shardRequestRenderer,
            Function<SemanticExtractionPrompt, String> reconciliationRequestRenderer,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        return writeRequestOnly(
                outputRoot, plan, shardRequestRenderer, reconciliationRequestRenderer, model, reasoningEffort,
                retention, NO_SHARED_ARTIFACTS);
    }

    public Path writeRequestOnly(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            Function<SemanticExtractionPrompt, String> shardRequestRenderer,
            Function<SemanticExtractionPrompt, String> reconciliationRequestRenderer,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        if (shardRequestRenderer == null) {
            throw new IllegalArgumentException("shard request renderer is required");
        }
        if (plan != null && plan.reconcile() && plan.shardRequests().size() > 1
                && reconciliationRequestRenderer == null) {
            throw new IllegalArgumentException("reconciliation request renderer is required");
        }
        return publish(
                outputRoot,
                plan,
                "REQUESTS_READY",
                List.of(),
                null,
                "openai-api",
                model,
                reasoningEffort,
                retention,
                sharedArtifactWriter,
                output -> {
                    prepare(output, plan);
                    for (SemanticShardRequest request : plan.shardRequests()) {
                        Path directory = shardDirectory(output, request.shard().id());
                        requestWriter.writeRequestOnly(
                                directory, request.prompt(), shardRequestRenderer.apply(request.prompt()));
                    }
                    writeReconciliationTemplate(output, plan, reconciliationRequestRenderer, false);
                });
    }

    public Path writeResult(
            Path outputRoot,
            SemanticExtractionRunResult run,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        return writeResult(
                outputRoot, run, provider, model, reasoningEffort, retention, NO_SHARED_ARTIFACTS);
    }

    /**
     * CN: 成功 run 完整落盘；共享 deterministic artifacts 与模型 artifacts 属于同一发布事务。
     * EN: Persists a successful run; shared deterministic artifacts and model artifacts belong to one publication transaction.
     */
    public Path writeResult(
            Path outputRoot,
            SemanticExtractionRunResult run,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        return publish(
                outputRoot,
                run.plan(),
                "COMPLETE",
                run.shardExecutions(),
                run,
                provider,
                model,
                reasoningEffort,
                retention,
                sharedArtifactWriter,
                output -> writeResultArtifacts(output, run));
    }

    /**
     * CN: 在 run staging 生命周期内执行模型流程并写入最终产物；普通失败原子写为 FAILED，若终态写入本身失败则保留先前可解析的 IN_PROGRESS，且任何失败都不发布半成品目录。
     * EN: Executes the model workflow inside the run staging lifecycle and writes final artifacts. Ordinary failures
     * atomically record FAILED; if that terminal update itself fails, the prior parseable IN_PROGRESS remains, and no
     * failure publishes a partial run.
     */
    public Path executeAndWriteResult(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            SemanticExtractionService service,
            SemanticModelClient shardClient,
            SemanticModelClient reconciliationClient,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        if (plan == null || service == null || shardClient == null) {
            throw new IllegalArgumentException("semantic extraction plan, service and shard client are required");
        }
        ArtifactRetention resolvedRetention = retention == null ? ArtifactRetention.FULL : retention;
        Consumer<Path> resolvedSharedWriter = sharedArtifactWriter == null
                ? NO_SHARED_ARTIFACTS
                : sharedArtifactWriter;
        RunArtifactPublisher.RunDirectory runDirectory = publisher.begin(outputRoot);
        List<SemanticShardExecution> completedShards = new ArrayList<>();
        SemanticExtractionRunManifest.ReconciliationAudit[] completedReconciliation =
                new SemanticExtractionRunManifest.ReconciliationAudit[1];
        try {
            writeInitialManifest(
                    runDirectory, plan, provider, model, reasoningEffort, resolvedRetention);
            prepare(runDirectory.stagingDirectory(), plan);
            resolvedSharedWriter.accept(runDirectory.stagingDirectory());
            SemanticExtractionExecutionObserver observer = new SemanticExtractionExecutionObserver() {
                @Override
                public void shardCompleted(SemanticShardExecution execution) {
                    audits.writeShard(
                            runDirectory.stagingDirectory(),
                            execution.request().shard().id(),
                            execution.request().prompt(),
                            execution.result(),
                            execution.trustedNormalizedDocument());
                    completedShards.add(execution);
                }

                @Override
                public void reconciliationCompleted(
                        SemanticExtractionPrompt prompt,
                        SemanticExtractionResult result,
                        JsonNode patch
                ) {
                    audits.writeReconciliationWithResult(
                            runDirectory.stagingDirectory(), prompt, result, patch);
                    completedReconciliation[0] =
                            new SemanticExtractionRunManifest.ReconciliationAudit(prompt, result, patch);
                }
            };
            SemanticExtractionRunResult run = service.execute(
                    plan, shardClient, reconciliationClient, observer);
            if (run == null || run.plan() == null
                    || !run.plan().shardPlan().fullBundleHash().equals(plan.shardPlan().fullBundleHash())) {
                throw new IllegalArgumentException("semantic extraction execution returned a different run plan");
            }
            writeFinalResultArtifacts(runDirectory.stagingDirectory(), run);
            ObjectNode manifest = manifests.create(
                    plan, "COMPLETE", run.shardExecutions(), run, null,
                    provider, model, reasoningEffort);
            return publishCompleteRun(runDirectory, manifest, resolvedRetention);
        } catch (RuntimeException | Error failure) {
            writeFailedManifest(
                    runDirectory,
                    plan,
                    completedShards,
                    completedReconciliation[0],
                    provider,
                    model,
                    reasoningEffort,
                    resolvedRetention,
                    failure);
            throw failure;
        }
    }

    private Path publish(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            String status,
            List<SemanticShardExecution> executions,
            SemanticExtractionRunResult run,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter,
            Consumer<Path> runArtifactWriter
    ) {
        ArtifactRetention resolvedRetention = retention == null ? ArtifactRetention.FULL : retention;
        Consumer<Path> resolvedSharedWriter = sharedArtifactWriter == null
                ? NO_SHARED_ARTIFACTS
                : sharedArtifactWriter;
        RunArtifactPublisher.RunDirectory runDirectory = publisher.begin(outputRoot);
        try {
            writeInitialManifest(
                    runDirectory, plan, provider, model, reasoningEffort, resolvedRetention);
            resolvedSharedWriter.accept(runDirectory.stagingDirectory());
            runArtifactWriter.accept(runDirectory.stagingDirectory());
            ObjectNode manifest = manifests.create(
                    plan, status, executions, run, null, provider, model, reasoningEffort);
            if ("COMPLETE".equals(status)) {
                return publishCompleteRun(runDirectory, manifest, resolvedRetention);
            }
            manifests.finish(
                    manifest,
                    runDirectory,
                    resolvedRetention,
                    Instant.now(),
                    List.of(),
                    runDirectory.stagingDirectory());
            files.writeManifest(runDirectory.stagingDirectory(), manifest);
            return publisher.publish(runDirectory);
        } catch (RuntimeException | Error failure) {
            writeFailedManifest(
                    runDirectory,
                    plan,
                    executions,
                    manifests.reconciliationAudit(run),
                    provider,
                    model,
                    reasoningEffort,
                    resolvedRetention,
                    failure);
            throw failure;
        }
    }

    private void writeFailedManifest(
            RunArtifactPublisher.RunDirectory runDirectory,
            SemanticExtractionRunPlan plan,
            List<SemanticShardExecution> completedShards,
            SemanticExtractionRunManifest.ReconciliationAudit completedReconciliation,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Throwable failure
    ) {
        if (!Files.isDirectory(runDirectory.stagingDirectory())) {
            return;
        }
        try {
            ObjectNode manifest = manifests.create(
                    plan,
                    "FAILED",
                    completedShards,
                    null,
                    completedReconciliation,
                    provider,
                    model,
                    reasoningEffort);
            manifest.put("failureType", failure.getClass().getSimpleName());
            manifests.finish(
                    manifest,
                    runDirectory,
                    retention,
                    null,
                    List.of(),
                    runDirectory.stagingDirectory());
            files.writeManifest(runDirectory.stagingDirectory(), manifest);
        } catch (RuntimeException manifestFailure) {
            failure.addSuppressed(manifestFailure);
        }
    }

    private void writeInitialManifest(
            RunArtifactPublisher.RunDirectory runDirectory,
            SemanticExtractionRunPlan plan,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        ObjectNode manifest = manifests.create(
                plan, "IN_PROGRESS", List.of(), null, null, provider, model, reasoningEffort);
        manifests.finish(
                manifest,
                runDirectory,
                retention,
                null,
                List.of(),
                runDirectory.stagingDirectory());
        files.writeManifest(runDirectory.stagingDirectory(), manifest);
    }

    private Path publishCompleteRun(
            RunArtifactPublisher.RunDirectory runDirectory,
            ObjectNode manifest,
            ArtifactRetention retention
    ) {
        if (retention != ArtifactRetention.FINAL_ONLY) {
            manifests.finish(
                    manifest,
                    runDirectory,
                    retention,
                    Instant.now(),
                    List.of(),
                    runDirectory.stagingDirectory());
            files.writeManifest(runDirectory.stagingDirectory(), manifest);
            return publisher.publish(runDirectory);
        }

        Path candidate = null;
        try {
            candidate = publisher.createPublishCandidate(runDirectory);
            manifests.copyRetainedFinalArtifacts(runDirectory.stagingDirectory(), candidate);
            List<RunArtifactFileStore.ArtifactEntry> pruned =
                    manifests.prunedArtifactEntries(runDirectory.stagingDirectory());
            manifests.finish(
                    manifest,
                    runDirectory,
                    retention,
                    Instant.now(),
                    pruned,
                    candidate);
            files.writeManifest(candidate, manifest);
            Path published = publisher.publishCandidate(runDirectory, candidate);
            files.deleteRecursivelyBestEffort(runDirectory.stagingDirectory());
            return published;
        } catch (RuntimeException | Error failure) {
            if (candidate != null) {
                files.deleteRecursivelyBestEffort(candidate);
            }
            throw failure;
        }
    }

    private void prepare(Path output, SemanticExtractionRunPlan plan) {
        createDirectory(output);
        files.writeJson(output.resolve("full-evidence-bundle.json"), plan.trustedFullBundle());
    }

    private void writeResultArtifacts(Path output, SemanticExtractionRunResult run) {
        prepare(output, run.plan());
        for (SemanticShardExecution execution : run.shardExecutions()) {
            Path directory = shardDirectory(output, execution.request().shard().id());
            writePromptArtifacts(
                    directory,
                    execution.request().prompt(),
                    execution.result(),
                    execution.trustedNormalizedDocument());
        }
        if (run.reconciliationPrompt() != null && run.reconciliationResult() != null) {
            Path directory = output.resolve("reconciliation");
            writePromptArtifacts(
                    directory,
                    run.reconciliationPrompt(),
                    run.reconciliationResult(),
                    run.reconciliationPatch());
            files.writeJson(directory.resolve("patch.json"), run.reconciliationPatch());
        }
        files.writeJson(output.resolve("merged-draft.json"), run.trustedMergedDraft());
        files.writeJson(output.resolve("semantic-extraction-result.json"), run.trustedFinalDocument());
    }

    private void writeFinalResultArtifacts(Path output, SemanticExtractionRunResult run) {
        files.writeJson(output.resolve("merged-draft.json"), run.trustedMergedDraft());
        files.writeJson(output.resolve("semantic-extraction-result.json"), run.trustedFinalDocument());
    }

    private void writePromptArtifacts(
            Path directory,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode normalized
    ) {
        createDirectory(directory);
        requestWriter.writeRequestOnly(directory, prompt, result.requestJson());
        files.writeText(directory.resolve("semantic-extraction-response.json"), result.responseJson());
        files.writeText(directory.resolve("semantic-extraction-result-raw.json"), result.outputText());
        files.writeJson(directory.resolve("semantic-extraction-result.json"), normalized);
    }

    private void writeReconciliationTemplate(
            Path output,
            SemanticExtractionRunPlan plan,
            Function<SemanticExtractionPrompt, String> requestRenderer,
            boolean codex
    ) {
        if (plan.shardRequests().size() <= 1 || !plan.reconcile()) {
            return;
        }
        Path directory = output.resolve("reconciliation").resolve("template");
        createDirectory(directory);
        SemanticExtractionPrompt prompt = new SemanticReconciliationPromptBuilder().template(plan.shardPlan());
        if (codex) {
            requestWriter.writeCodexSessionRequest(directory, prompt);
        } else if (requestRenderer != null) {
            requestWriter.writeRequestOnly(directory, prompt, requestRenderer.apply(prompt));
        }
    }

    private Path shardDirectory(Path output, String shardId) {
        return output.resolve("shards").resolve(shardId);
    }

    private void createDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to create semantic extraction artifact directory", error);
        }
    }

}
