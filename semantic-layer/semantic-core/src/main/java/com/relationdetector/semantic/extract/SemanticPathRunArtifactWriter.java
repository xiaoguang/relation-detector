package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;

/**
 * CN: 对path-backed semantic plan执行原子artifact事务；逐片加载、调用、归一化并立即落盘，最终通过外排
 * result store发布完整结果，失败保留已完成分片且不发布run目录。
 * EN: Owns the atomic artifact transaction for a path-backed semantic plan. It loads, calls, normalizes, and persists
 * one shard at a time, publishes only an externally merged complete result, and retains completed shard audits after
 * failure without publishing a run directory.
 */
public final class SemanticPathRunArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Consumer<Path> NO_SHARED_ARTIFACTS = ignored -> {
    };
    private final RunArtifactPublisher publisher = new RunArtifactPublisher();
    private final SemanticRequestArtifactWriter requestWriter = new SemanticRequestArtifactWriter();
    private final SemanticExtractionPromptBuilder promptBuilder = new SemanticExtractionPromptBuilder();
    private final SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();

    public Path writeCodexSession(
            Path outputRoot,
            SemanticPathRunPlan plan,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        return writeRequests(
                outputRoot,
                plan,
                "AWAITING_MODEL_RESULTS",
                null,
                null,
                true,
                model,
                reasoningEffort,
                retention,
                sharedArtifactWriter);
    }

    public Path writeRequestOnly(
            Path outputRoot,
            SemanticPathRunPlan plan,
            Function<SemanticExtractionPrompt, String> shardRenderer,
            Function<SemanticExtractionPrompt, String> reconciliationRenderer,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        if (shardRenderer == null) {
            throw new IllegalArgumentException("semantic shard request renderer is required");
        }
        return writeRequests(
                outputRoot,
                plan,
                "REQUESTS_READY",
                shardRenderer,
                reconciliationRenderer,
                false,
                model,
                reasoningEffort,
                retention,
                sharedArtifactWriter);
    }

    /**
     * CN: 顺序执行 path-backed shard、可选 reconciliation 和最终外排 merge；输入包含完整 evidence store
     * 与模型 client，成功时原子发布 run 目录，任一步失败则只保留带审计 manifest 的 staging。
     * EN: Sequentially executes path-backed shards, optional reconciliation, and the final external merge. Given
     * a complete evidence store and model clients, it atomically publishes a run only after success and otherwise
     * preserves an audited staging directory.
     */
    public Path executeAndWrite(
            Path outputRoot,
            SemanticPathRunPlan plan,
            SemanticEvidenceStore evidenceStore,
            SemanticModelClient shardClient,
            SemanticModelClient reconciliationClient,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        if (plan == null || evidenceStore == null || shardClient == null) {
            throw new IllegalArgumentException(
                    "semantic path plan, evidence store and shard client are required");
        }
        ArtifactRetention resolved = retention == null ? ArtifactRetention.FULL : retention;
        RunArtifactPublisher.RunDirectory run = publisher.begin(outputRoot);
        List<ShardAudit> completed = new ArrayList<>();
        ReconciliationAudit[] reconciliationAudit = new ReconciliationAudit[1];
        try {
            writeManifest(run, manifest(
                    run, plan, "IN_PROGRESS", provider, model, reasoningEffort,
                    resolved, completed, null, null));
            prepare(run.stagingDirectory(), plan);
            resolvedWriter(sharedArtifactWriter).accept(run.stagingDirectory());
            try (SemanticPathResultStore results = new SemanticPathResultStore(
                    run.stagingDirectory().resolve(".result-work"), evidenceStore)) {
                for (SemanticPathShard shard : plan.shards()) {
                    ObjectNode bundle = readObject(shard.bundlePath(), "semantic shard bundle");
                    SemanticExtractionPrompt prompt = promptBuilder.build(bundle);
                    requireBudget(prompt, plan.maxInputTokens());
                    SemanticExtractionResult response = shardClient.extract(prompt);
                    ObjectNode normalized = normalize(response.outputText(), bundle);
                    results.append(shard, bundle, normalized, plan.fullBundleHash());
                    writeShardAtomically(run.stagingDirectory(), shard.id(), prompt, response, normalized);
                    completed.add(new ShardAudit(shard, response));
                }
                results.finish();
                if (plan.shards().size() > 1 && plan.reconcile()) {
                    if (reconciliationClient == null) {
                        throw new IllegalArgumentException("semantic reconciliation client is required");
                    }
                    SemanticExtractionPrompt prompt = results.reconciliationPrompt(
                            plan, plan.maxInputTokens());
                    SemanticExtractionResult response = reconciliationClient.extract(prompt);
                    JsonNode patch = parseObject(
                            response.outputText(), "semantic reconciliation patch");
                    results.applyReconciliationPatch(patch);
                    writeReconciliationAtomically(
                            run.stagingDirectory(), prompt, response, patch);
                    reconciliationAudit[0] = new ReconciliationAudit(prompt, response);
                } else {
                    results.requireConflictFree();
                }
                results.writeMergedDraft(run.stagingDirectory().resolve("merged-draft.json"));
                results.writeFinalDocument(
                        run.stagingDirectory().resolve("semantic-extraction-result.json"));
            }
            ObjectNode complete = manifest(
                    run, plan, "COMPLETE", provider, model, reasoningEffort,
                    resolved, completed, reconciliationAudit[0], null);
            return publishComplete(run, complete, resolved);
        } catch (RuntimeException | Error failure) {
            writeFailedManifest(
                    run, plan, provider, model, reasoningEffort, resolved,
                    completed, reconciliationAudit[0], failure);
            throw failure;
        }
    }

    /**
     * CN: 为每个有界 shard 顺序生成 request-only 或 Codex request artifact；输入是 path plan 和 renderer，
     * 输出原子发布的请求 run，预算、写盘或 manifest 失败时不发布半成品。
     * EN: Sequentially renders request-only or Codex artifacts for each bounded shard. It returns an atomically
     * published request run and publishes nothing when budgeting, writing, or manifest creation fails.
     */
    private Path writeRequests(
            Path outputRoot,
            SemanticPathRunPlan plan,
            String status,
            Function<SemanticExtractionPrompt, String> shardRenderer,
            Function<SemanticExtractionPrompt, String> reconciliationRenderer,
            boolean codex,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter
    ) {
        if (plan == null) {
            throw new IllegalArgumentException("semantic path run plan is required");
        }
        ArtifactRetention resolved = retention == null ? ArtifactRetention.FULL : retention;
        RunArtifactPublisher.RunDirectory run = publisher.begin(outputRoot);
        try {
            writeManifest(run, manifest(
                    run, plan, "IN_PROGRESS", codex ? "codex-session" : "openai-api",
                    model, reasoningEffort, resolved, List.of(), null, null));
            prepare(run.stagingDirectory(), plan);
            resolvedWriter(sharedArtifactWriter).accept(run.stagingDirectory());
            for (SemanticPathShard shard : plan.shards()) {
                ObjectNode bundle = readObject(shard.bundlePath(), "semantic shard bundle");
                SemanticExtractionPrompt prompt = promptBuilder.build(bundle);
                requireBudget(prompt, plan.maxInputTokens());
                Path directory = run.stagingDirectory().resolve("shards").resolve(shard.id());
                if (codex) {
                    requestWriter.writeCodexSessionRequest(directory, prompt);
                } else {
                    requestWriter.writeRequestOnly(directory, prompt, shardRenderer.apply(prompt));
                }
            }
            if (plan.shards().size() > 1 && plan.reconcile()) {
                SemanticExtractionPrompt template = reconciliationTemplate(plan);
                Path directory = run.stagingDirectory().resolve("reconciliation").resolve("template");
                if (codex) {
                    requestWriter.writeCodexSessionRequest(directory, template);
                } else if (reconciliationRenderer != null) {
                    requestWriter.writeRequestOnly(
                            directory, template, reconciliationRenderer.apply(template));
                }
            }
            ObjectNode ready = manifest(
                    run, plan, status, codex ? "codex-session" : "openai-api",
                    model, reasoningEffort, resolved, List.of(), null, null);
            finishManifest(ready, run.stagingDirectory(), resolved, Instant.now());
            writeManifest(run, ready);
            return publisher.publish(run);
        } catch (RuntimeException | Error failure) {
            writeFailedManifest(
                    run, plan, codex ? "codex-session" : "openai-api",
                    model, reasoningEffort, resolved, List.of(), null, failure);
            throw failure;
        }
    }

    private void prepare(Path staging, SemanticPathRunPlan plan) {
        try {
            Files.copy(
                    plan.fullBundlePath(),
                    staging.resolve("full-evidence-bundle.json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to persist semantic evidence bundle", failure);
        }
    }

    private ObjectNode normalize(String output, ObjectNode bundle) {
        return normalizer.normalizeOwnedShard(
                parseObject(output, "semantic shard result"),
                bundle);
    }

    private JsonNode parseObject(String value, String label) {
        try {
            JsonNode result = JSON.readTree(value);
            if (result == null || !result.isObject()) {
                throw new SemanticExtractionValidationException(label + " must be a JSON object");
            }
            return result;
        } catch (SemanticExtractionValidationException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new SemanticExtractionValidationException(label + " must be valid JSON");
        }
    }

    private ObjectNode readObject(Path path, String label) {
        try {
            JsonNode result = JSON.readTree(path.toFile());
            if (result == null || !result.isObject()) {
                throw new SemanticExtractionValidationException(label + " must be a JSON object");
            }
            return (ObjectNode) result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to read bounded semantic artifact", failure);
        }
    }

    private void requireBudget(SemanticExtractionPrompt prompt, int maxInputTokens) {
        if (new SemanticPromptBudgetEstimator().estimate(prompt) > maxInputTokens) {
            throw new SemanticShardingException(
                    "semantic prompt exceeds the configured estimated input-token limit");
        }
    }

    private SemanticExtractionPrompt reconciliationTemplate(SemanticPathRunPlan plan) {
        ObjectNode bundle = JSON.createObjectNode();
        bundle.put("kind", "SEMANTIC_RECONCILIATION");
        bundle.put("fullBundleHash", plan.fullBundleHash());
        ArrayNode shards = bundle.putArray("shards");
        plan.shards().forEach(shard -> shards.addObject()
                .put("id", shard.id())
                .put("ownerKey", shard.ownerKey())
                .put("estimatedInputTokens", shard.estimatedInputTokens()));
        bundle.putObject("semanticSummary");
        bundle.putArray("conflicts");
        bundle.put("template", true);
        bundle.putObject("instructions")
                .put("patchOnly", true)
                .put("newPhysicalFactsForbidden", true)
                .put("newEvidenceReferencesForbidden", true);
        return new SemanticExtractionPrompt(
                "Return a constrained reconciliation patch with resolutions and renames arrays only.",
                "Use this template after all semantic shards are complete:\n" + bundle,
                bundle);
    }

    private void writeShardAtomically(
            Path staging,
            String shardId,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult response,
            JsonNode normalized
    ) {
        Path parent = staging.resolve("shards");
        Path target = parent.resolve(shardId);
        Path temporary = parent.resolve("." + shardId + ".tmp-" + UUID.randomUUID());
        writeDirectoryAtomically(temporary, target, directory -> {
            requestWriter.writeRequestOnly(directory, prompt, response.requestJson());
            write(directory.resolve("semantic-extraction-response.json"), response.responseJson());
            write(directory.resolve("semantic-extraction-result-raw.json"), response.outputText());
            writeJson(directory.resolve("semantic-extraction-result.json"), normalized);
        });
    }

    private void writeReconciliationAtomically(
            Path staging,
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult response,
            JsonNode patch
    ) {
        Path target = staging.resolve("reconciliation");
        Path temporary = staging.resolve(".reconciliation.tmp-" + UUID.randomUUID());
        writeDirectoryAtomically(temporary, target, directory -> {
            requestWriter.writeRequestOnly(directory, prompt, response.requestJson());
            write(directory.resolve("semantic-extraction-response.json"), response.responseJson());
            write(directory.resolve("semantic-extraction-result-raw.json"), response.outputText());
            writeJson(directory.resolve("patch.json"), patch);
        });
    }

    private void writeDirectoryAtomically(Path temporary, Path target, Consumer<Path> writer) {
        try {
            Files.createDirectories(temporary.getParent());
            writer.accept(temporary);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            deleteRecursivelyBestEffort(temporary);
            throw new IllegalArgumentException("failed to publish semantic audit directory", failure);
        } catch (RuntimeException | Error failure) {
            deleteRecursivelyBestEffort(temporary);
            throw failure;
        }
    }

    /**
     * CN: 根据 path plan、已完成 shard 和 reconciliation 审计构造当前运行 manifest；返回独立 JSON，
     * 不读取模型业务内容，失败详情只记录安全异常类型。
     * EN: Builds the current run manifest from the path plan and completed shard/reconciliation audits. It returns
     * a detached JSON document, never reads model business content, and records only a safe failure type.
     */
    private ObjectNode manifest(
            RunArtifactPublisher.RunDirectory run,
            SemanticPathRunPlan plan,
            String status,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            List<ShardAudit> completed,
            ReconciliationAudit reconciliation,
            Throwable failure
    ) {
        ObjectNode manifest = JSON.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("runId", run.runId());
        manifest.put("status", status);
        manifest.put("provider", text(provider));
        manifest.put("model", text(model));
        manifest.put("reasoningEffort", text(reasoningEffort));
        manifest.put("retention", retention.wireValue());
        manifest.put("fullBundleHash", plan.fullBundleHash());
        manifest.put("maxInputTokens", plan.maxInputTokens());
        manifest.put("shardCount", plan.shards().size());
        manifest.put("reconcile", plan.reconcile());
        manifest.put("ownedFactCount", plan.ownedFactCount());
        manifest.put("ownedCandidateCount", plan.ownedCandidateCount());
        manifest.put("finalRefClosed", "COMPLETE".equals(status));
        if (failure != null) {
            manifest.put("failureType", failure.getClass().getSimpleName());
        }
        ArrayNode shards = manifest.putArray("shards");
        for (SemanticPathShard shard : plan.shards()) {
            ShardAudit audit = completed.stream()
                    .filter(value -> value.shard.id().equals(shard.id()))
                    .findFirst()
                    .orElse(null);
            ObjectNode item = shards.addObject();
            item.put("id", shard.id());
            item.put("ownerKey", shard.ownerKey());
            item.put("estimatedInputTokens", shard.estimatedInputTokens());
            item.put("status", audit == null ? "PENDING" : "COMPLETE");
            item.put("actualInputTokens", audit == null ? 0 : audit.result.inputTokens());
            item.put("actualOutputTokens", audit == null ? 0 : audit.result.outputTokens());
            item.put("transportAttempts", audit == null ? 0 : audit.result.transportAttempts());
        }
        int inputTokens = completed.stream().mapToInt(value -> value.result.inputTokens()).sum();
        int outputTokens = completed.stream().mapToInt(value -> value.result.outputTokens()).sum();
        int attempts = completed.stream().mapToInt(value -> value.result.transportAttempts()).sum();
        if (reconciliation != null) {
            inputTokens += reconciliation.result.inputTokens();
            outputTokens += reconciliation.result.outputTokens();
            attempts += reconciliation.result.transportAttempts();
        }
        manifest.putObject("usage")
                .put("inputTokens", inputTokens)
                .put("outputTokens", outputTokens)
                .put("transportAttempts", attempts);
        ObjectNode reconciliationNode = manifest.putObject("reconciliation");
        boolean required = plan.shards().size() > 1 && plan.reconcile();
        reconciliationNode.put("required", required);
        reconciliationNode.put("maxInputTokens", plan.maxInputTokens());
        reconciliationNode.put("status", !required ? "NOT_REQUIRED"
                : reconciliation == null ? "PENDING" : "COMPLETE");
        if (reconciliation != null) {
            reconciliationNode.put("estimatedInputTokens",
                    new SemanticPromptBudgetEstimator().estimate(reconciliation.prompt));
            reconciliationNode.put("tokenEstimateExact", false);
            reconciliationNode.put("inputTokens", reconciliation.result.inputTokens());
            reconciliationNode.put("outputTokens", reconciliation.result.outputTokens());
        }
        manifest.putNull("publishedAt");
        manifest.putArray("artifacts");
        manifest.putArray("prunedArtifacts");
        return manifest;
    }

    private void writeFailedManifest(
            RunArtifactPublisher.RunDirectory run,
            SemanticPathRunPlan plan,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            List<ShardAudit> completed,
            ReconciliationAudit reconciliation,
            Throwable failure
    ) {
        try {
            ObjectNode manifest = manifest(
                    run, plan, "FAILED", provider, model, reasoningEffort,
                    retention, completed, reconciliation, failure);
            finishManifest(manifest, run.stagingDirectory(), retention, null);
            writeManifest(run, manifest);
        } catch (RuntimeException manifestFailure) {
            failure.addSuppressed(manifestFailure);
        }
    }

    private Path publishComplete(
            RunArtifactPublisher.RunDirectory run,
            ObjectNode manifest,
            ArtifactRetention retention
    ) {
        if (retention != ArtifactRetention.FINAL_ONLY) {
            finishManifest(manifest, run.stagingDirectory(), retention, Instant.now());
            writeManifest(run, manifest);
            return publisher.publish(run);
        }
        Path candidate = publisher.createPublishCandidate(run);
        try {
            copyRetained(run.stagingDirectory(), candidate);
            finishManifest(manifest, candidate, retention, Instant.now());
            writeManifestAt(candidate, manifest);
            Path published = publisher.publishCandidate(run, candidate);
            deleteRecursivelyBestEffort(run.stagingDirectory());
            return published;
        } catch (RuntimeException | Error failure) {
            deleteRecursivelyBestEffort(candidate);
            throw failure;
        }
    }

    private void finishManifest(
            ObjectNode manifest,
            Path artifactRoot,
            ArtifactRetention retention,
            Instant publishedAt
    ) {
        manifest.put("retention", retention.wireValue());
        if (publishedAt == null) manifest.putNull("publishedAt");
        else manifest.put("publishedAt", publishedAt.toString());
        ArrayNode artifacts = manifest.putArray("artifacts");
        for (Path file : regularFiles(artifactRoot)) {
            String relative = relative(artifactRoot, file);
            if ("run-manifest.json".equals(relative)) continue;
            artifacts.addObject()
                    .put("path", relative)
                    .put("size", size(file))
                    .put("sha256", sha256(file));
        }
    }

    private void copyRetained(Path source, Path target) {
        for (Path file : regularFiles(source)) {
            String relative = relative(source, file);
            if (!"semantic-extraction-result.json".equals(relative)
                    && !relative.startsWith("deterministic-kg/")) {
                continue;
            }
            Path destination = target.resolve(relative);
            try {
                Files.createDirectories(destination.getParent());
                Files.copy(file, destination);
            } catch (IOException failure) {
                throw new IllegalArgumentException(
                        "failed to build semantic final-only publish candidate", failure);
            }
        }
    }

    private void writeManifest(RunArtifactPublisher.RunDirectory run, ObjectNode manifest) {
        writeManifestAt(run.stagingDirectory(), manifest);
    }

    private void writeManifestAt(Path directory, ObjectNode manifest) {
        Path target = directory.resolve("run-manifest.json");
        Path temporary = directory.resolve("run-manifest.json.tmp");
        try {
            JSON.writeValue(temporary.toFile(), manifest);
            Files.move(
                    temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw new IllegalArgumentException("failed to update semantic run manifest", failure);
        }
    }

    private Consumer<Path> resolvedWriter(Consumer<Path> writer) {
        return writer == null ? NO_SHARED_ARTIFACTS : writer;
    }

    private void writeJson(Path path, Object value) {
        try {
            Files.createDirectories(path.getParent());
            JSON.writeValue(path.toFile(), value);
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to write semantic JSON artifact", failure);
        }
    }

    private void write(Path path, String value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, value == null ? "" : value);
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to write semantic text artifact", failure);
        }
    }

    private List<Path> regularFiles(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .toList();
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to inspect semantic run artifacts", failure);
        }
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException failure) {
            throw new IllegalArgumentException("failed to size semantic artifact", failure);
        }
    }

    private String sha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalArgumentException("failed to hash semantic artifact", failure);
        }
    }

    private void deleteRecursivelyBestEffort(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // A published final artifact remains valid; failed staging is intentionally retained.
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record ShardAudit(SemanticPathShard shard, SemanticExtractionResult result) {
    }

    private record ReconciliationAudit(
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result
    ) {
    }
}
