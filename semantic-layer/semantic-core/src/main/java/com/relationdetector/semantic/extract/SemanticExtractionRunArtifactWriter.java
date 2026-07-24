package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 在独立 staging 目录写入完整 run，生成可审计 manifest 后原子发布；失败 run 保留 FAILED staging。
 * EN: Writes a complete run in an isolated staging directory and atomically publishes it after its auditable manifest is complete. Failed runs remain staged with FAILED status.
 */
public final class SemanticExtractionRunArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Consumer<Path> NO_SHARED_ARTIFACTS = ignored -> {
    };
    private final SemanticExtractionArtifactWriter legacyWriter = new SemanticExtractionArtifactWriter();
    private final RunArtifactPublisher publisher = new RunArtifactPublisher();

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
                        legacyWriter.writeCodexSessionRequest(
                                shardDirectory(output, request.shard().id()), request.prompt());
                    }
                    writeReconciliationTemplate(output, plan, null, true);
                    writeCompatibilityArtifacts(output, plan, null, true);
                });
    }

    public Path writeRequestOnly(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            SemanticModelClient shardClient,
            SemanticModelClient reconciliationClient,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        return writeRequestOnly(
                outputRoot, plan, shardClient, reconciliationClient, model, reasoningEffort,
                retention, NO_SHARED_ARTIFACTS);
    }

    public Path writeRequestOnly(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            SemanticModelClient shardClient,
            SemanticModelClient reconciliationClient,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
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
                        legacyWriter.writeRequestOnly(
                                directory, request.prompt(), shardClient.requestJson(request.prompt()));
                    }
                    writeReconciliationTemplate(output, plan, reconciliationClient, false);
                    writeCompatibilityArtifacts(output, plan, shardClient, false);
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
     * CN: 在 run staging 生命周期内执行模型流程并写入最终产物；模型、归一化、闭包或 I/O 失败都只留下 FAILED staging，不发布半成品目录。
     * EN: Executes the model workflow inside the run staging lifecycle and writes final artifacts. Model, normalization, closure, or I/O failures leave only a FAILED staging directory.
     */
    public Path executeAndWriteResult(
            Path outputRoot,
            SemanticExtractionRunPlan plan,
            Supplier<SemanticExtractionRunResult> execution,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        if (plan == null || execution == null) {
            throw new IllegalArgumentException("semantic extraction plan and execution are required");
        }
        ArtifactRetention resolvedRetention = retention == null ? ArtifactRetention.FULL : retention;
        Consumer<Path> resolvedSharedWriter = sharedArtifactWriter == null
                ? NO_SHARED_ARTIFACTS
                : sharedArtifactWriter;
        RunArtifactPublisher.RunDirectory runDirectory = publisher.begin(outputRoot);
        try {
            resolvedSharedWriter.accept(runDirectory.stagingDirectory());
            SemanticExtractionRunResult run = execution.get();
            if (run == null || run.plan() == null
                    || !run.plan().shardPlan().fullBundleHash().equals(plan.shardPlan().fullBundleHash())) {
                throw new IllegalArgumentException("semantic extraction execution returned a different run plan");
            }
            writeResultArtifacts(runDirectory.stagingDirectory(), run);
            ObjectNode manifest = createManifest(
                    plan, "COMPLETE", run.shardExecutions(), run, provider, model, reasoningEffort);
            List<ArtifactEntry> pruned = resolvedRetention == ArtifactRetention.FINAL_ONLY
                    ? pruneIntermediateArtifacts(runDirectory.stagingDirectory())
                    : List.of();
            finishManifest(manifest, runDirectory, resolvedRetention, Instant.now(), pruned);
            writeJson(runDirectory.stagingDirectory().resolve("run-manifest.json"), manifest);
            return publisher.publish(runDirectory);
        } catch (RuntimeException | Error failure) {
            writeFailedManifest(
                    runDirectory, plan, provider, model, reasoningEffort, resolvedRetention, failure);
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
            resolvedSharedWriter.accept(runDirectory.stagingDirectory());
            runArtifactWriter.accept(runDirectory.stagingDirectory());
            ObjectNode manifest = createManifest(
                    plan, status, executions, run, provider, model, reasoningEffort);
            List<ArtifactEntry> pruned = "COMPLETE".equals(status)
                    && resolvedRetention == ArtifactRetention.FINAL_ONLY
                    ? pruneIntermediateArtifacts(runDirectory.stagingDirectory())
                    : List.of();
            finishManifest(
                    manifest, runDirectory, resolvedRetention, Instant.now(), pruned);
            writeJson(runDirectory.stagingDirectory().resolve("run-manifest.json"), manifest);
            return publisher.publish(runDirectory);
        } catch (RuntimeException | Error failure) {
            writeFailedManifest(
                    runDirectory, plan, provider, model, reasoningEffort, resolvedRetention, failure);
            throw failure;
        }
    }

    private void writeFailedManifest(
            RunArtifactPublisher.RunDirectory runDirectory,
            SemanticExtractionRunPlan plan,
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
            ObjectNode manifest = createManifest(
                    plan, "FAILED", List.of(), null, provider, model, reasoningEffort);
            manifest.put("failureType", failure.getClass().getSimpleName());
            finishManifest(manifest, runDirectory, retention, null, List.of());
            writeJson(runDirectory.stagingDirectory().resolve("run-manifest.json"), manifest);
        } catch (RuntimeException manifestFailure) {
            failure.addSuppressed(manifestFailure);
        }
    }

    private void prepare(Path output, SemanticExtractionRunPlan plan) {
        createDirectory(output);
        writeJson(output.resolve("full-evidence-bundle.json"), plan.trustedFullBundle());
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
        if (run.shardExecutions().size() == 1) {
            SemanticShardExecution execution = run.shardExecutions().get(0);
            writePromptArtifacts(
                    output,
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
            writeJson(directory.resolve("patch.json"), run.reconciliationPatch());
        }
        writeJson(output.resolve("merged-draft.json"), run.trustedMergedDraft());
        writeJson(output.resolve("semantic-extraction-result.json"), run.trustedFinalDocument());
    }

    private void writePromptArtifacts(
            Path directory,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode normalized
    ) {
        createDirectory(directory);
        legacyWriter.writeRequestOnly(directory, prompt, result.requestJson());
        write(directory.resolve("semantic-extraction-response.json"), result.responseJson());
        write(directory.resolve("semantic-extraction-result-raw.json"), result.outputText());
        writeJson(directory.resolve("semantic-extraction-result.json"), normalized);
    }

    private void writeCompatibilityArtifacts(
            Path output,
            SemanticExtractionRunPlan plan,
            SemanticModelClient client,
            boolean codex
    ) {
        if (plan.shardRequests().size() != 1) {
            return;
        }
        SemanticExtractionPrompt prompt = plan.shardRequests().get(0).prompt();
        if (codex) {
            legacyWriter.writeCodexSessionRequest(output, prompt);
        } else {
            legacyWriter.writeRequestOnly(output, prompt, client.requestJson(prompt));
        }
    }

    private void writeReconciliationTemplate(
            Path output,
            SemanticExtractionRunPlan plan,
            SemanticModelClient client,
            boolean codex
    ) {
        if (plan.shardRequests().size() <= 1 || !plan.reconcile()) {
            return;
        }
        Path directory = output.resolve("reconciliation").resolve("template");
        createDirectory(directory);
        SemanticExtractionPrompt prompt = new SemanticReconciliationPromptBuilder().template(plan.shardPlan());
        if (codex) {
            legacyWriter.writeCodexSessionRequest(directory, prompt);
        } else if (client != null) {
            legacyWriter.writeRequestOnly(directory, prompt, client.requestJson(prompt));
        }
    }

    private ObjectNode createManifest(
            SemanticExtractionRunPlan plan,
            String status,
            List<SemanticShardExecution> executions,
            SemanticExtractionRunResult run,
            String provider,
            String model,
            String reasoningEffort
    ) {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("status", status);
        manifest.put("provider", blankDefault(provider, "codex-session"));
        manifest.put("model", blankDefault(model, ""));
        manifest.put("reasoningEffort", blankDefault(reasoningEffort, ""));
        manifest.put("fullBundleHash", plan.shardPlan().fullBundleHash());
        manifest.put("shardCount", plan.shardPlan().shards().size());
        manifest.put("reconcile", plan.reconcile());
        manifest.put("ownedFactCount", plan.shardPlan().factOwners().size());
        manifest.put("ownedCandidateCount", plan.shardPlan().candidateOwners().size());
        manifest.put("mergeConflictCount", run == null ? 0 : run.mergeResult().conflicts().size());
        manifest.put("finalRefClosed", run != null
                && run.trustedFinalDocument().path("validation").path("isRefClosed").asBoolean(false));
        ArrayNode shards = manifest.putArray("shards");
        for (SemanticShard shard : plan.shardPlan().shards()) {
            ObjectNode item = shards.addObject();
            item.put("id", shard.id());
            item.put("ownerKey", shard.ownerKey());
            item.put("estimatedInputTokens", shard.estimatedInputTokens());
            SemanticShardExecution execution = executions.stream()
                    .filter(value -> value.request().shard().id().equals(shard.id()))
                    .findFirst()
                    .orElse(null);
            item.put("status", execution == null ? "PENDING" : "COMPLETE");
            item.put("actualInputTokens", execution == null ? 0 : execution.result().inputTokens());
            item.put("actualOutputTokens", execution == null ? 0 : execution.result().outputTokens());
            item.put("transportAttempts", execution == null ? 0 : execution.result().transportAttempts());
        }
        addUsage(manifest, executions, run);
        addReconciliation(manifest, plan, run);
        return manifest;
    }

    private void finishManifest(
            ObjectNode manifest,
            RunArtifactPublisher.RunDirectory runDirectory,
            ArtifactRetention retention,
            Instant publishedAt,
            List<ArtifactEntry> pruned
    ) {
        manifest.put("runId", runDirectory.runId());
        manifest.put("retention", retention.wireValue());
        if (publishedAt == null) {
            manifest.putNull("publishedAt");
        } else {
            manifest.put("publishedAt", publishedAt.toString());
        }
        addArtifactEntries(
                manifest.putArray("artifacts"),
                artifactEntries(runDirectory.stagingDirectory()));
        addArtifactEntries(manifest.putArray("prunedArtifacts"), pruned);
    }

    private void addArtifactEntries(ArrayNode target, List<ArtifactEntry> entries) {
        for (ArtifactEntry artifact : entries) {
            ObjectNode item = target.addObject();
            item.put("path", artifact.path());
            item.put("size", artifact.size());
            item.put("sha256", artifact.sha256());
        }
    }

    private List<ArtifactEntry> pruneIntermediateArtifacts(Path output) {
        List<Path> files = regularFiles(output).stream()
                .filter(path -> !retainedFinalArtifact(output, path))
                .toList();
        List<ArtifactEntry> pruned = files.stream()
                .map(path -> artifactEntry(output, path))
                .sorted(Comparator.comparing(ArtifactEntry::path))
                .toList();
        for (Path file : files) {
            delete(file);
        }
        try (Stream<Path> paths = Files.walk(output)) {
            paths.filter(Files::isDirectory)
                    .sorted(Comparator.reverseOrder())
                    .filter(path -> !path.equals(output))
                    .forEach(this::deleteEmptyDirectory);
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to prune semantic extraction artifact directories", error);
        }
        return pruned;
    }

    private boolean retainedFinalArtifact(Path output, Path artifact) {
        String relative = relativePath(output, artifact);
        return "semantic-extraction-result.json".equals(relative)
                || relative.startsWith("deterministic-kg/");
    }

    private List<ArtifactEntry> artifactEntries(Path output) {
        return regularFiles(output).stream()
                .filter(path -> !"run-manifest.json".equals(relativePath(output, path)))
                .map(path -> artifactEntry(output, path))
                .sorted(Comparator.comparing(ArtifactEntry::path))
                .toList();
    }

    private List<Path> regularFiles(Path output) {
        try (Stream<Path> paths = Files.walk(output)) {
            return paths.filter(Files::isRegularFile).toList();
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to inspect semantic extraction artifacts", error);
        }
    }

    private ArtifactEntry artifactEntry(Path output, Path artifact) {
        return new ArtifactEntry(relativePath(output, artifact), size(artifact), sha256(artifact));
    }

    private String relativePath(Path output, Path artifact) {
        return output.relativize(artifact).toString().replace('\\', '/');
    }

    private void addUsage(
            ObjectNode manifest,
            List<SemanticShardExecution> executions,
            SemanticExtractionRunResult run
    ) {
        int inputTokens = executions.stream().mapToInt(value -> value.result().inputTokens()).sum();
        int outputTokens = executions.stream().mapToInt(value -> value.result().outputTokens()).sum();
        int attempts = executions.stream().mapToInt(value -> value.result().transportAttempts()).sum();
        if (run != null && run.reconciliationResult() != null) {
            inputTokens += run.reconciliationResult().inputTokens();
            outputTokens += run.reconciliationResult().outputTokens();
            attempts += run.reconciliationResult().transportAttempts();
        }
        manifest.putObject("usage")
                .put("inputTokens", inputTokens)
                .put("outputTokens", outputTokens)
                .put("transportAttempts", attempts);
    }

    private void addReconciliation(
            ObjectNode manifest,
            SemanticExtractionRunPlan plan,
            SemanticExtractionRunResult run
    ) {
        ObjectNode reconciliation = manifest.putObject("reconciliation");
        boolean required = plan.shardRequests().size() > 1 && plan.reconcile();
        reconciliation.put("required", required);
        if (!required) {
            reconciliation.put("status", "NOT_REQUIRED");
            return;
        }
        if (run == null || run.reconciliationResult() == null) {
            reconciliation.put("status", "PENDING");
            return;
        }
        reconciliation.put("status", "COMPLETE");
        reconciliation.put("inputTokens", run.reconciliationResult().inputTokens());
        reconciliation.put("outputTokens", run.reconciliationResult().outputTokens());
        reconciliation.put("transportAttempts", run.reconciliationResult().transportAttempts());
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

    private void writeJson(Path path, Object value) {
        try {
            write(path, JSON.writeValueAsString(value));
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to serialize semantic extraction artifact", error);
        }
    }

    private void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content == null ? "" : content);
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to write semantic extraction artifact", error);
        }
    }

    private void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to prune semantic extraction artifact", error);
        }
    }

    private void deleteEmptyDirectory(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (DirectoryNotEmptyException ignored) {
            // Retained final artifacts keep their parent directories.
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to prune semantic extraction artifact directory", error);
        }
    }

    private long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException error) {
            throw new IllegalArgumentException("failed to inspect semantic extraction artifact", error);
        }
    }

    private String sha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream input = Files.newInputStream(path)) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException error) {
            throw new IllegalArgumentException("failed to hash semantic extraction artifact", error);
        }
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record ArtifactEntry(String path, long size, String sha256) {
    }
}
