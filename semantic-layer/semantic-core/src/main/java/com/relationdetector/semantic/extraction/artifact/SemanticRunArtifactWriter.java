package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.runtime.SemanticModelClient;

import com.relationdetector.semantic.extraction.shard.SemanticShardingException;

import com.relationdetector.semantic.extraction.artifact.SemanticRequestBundlePackageWriter;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.artifact.SemanticExternalAuditReferences;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionValidationException;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionResult;

import com.relationdetector.semantic.extraction.normalization.SemanticExtractionDocumentNormalizer;

import com.relationdetector.semantic.extraction.normalization.SemanticEvidenceLookup;

import com.relationdetector.semantic.extraction.normalization.SemanticBoundedJsonReader;

import com.relationdetector.semantic.extraction.prompt.SemanticReconciliationPromptBuilder;

import com.relationdetector.semantic.extraction.prompt.SemanticPromptBudgetEstimator;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPromptBuilder;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import com.relationdetector.semantic.extraction.config.ArtifactRetention;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;
import com.relationdetector.semantic.extraction.artifact.SemanticRunManifestFactory.ReconciliationAudit;
import com.relationdetector.semantic.extraction.artifact.SemanticRunManifestFactory.ShardAudit;

/**
 * CN: 对path-backed semantic plan执行原子artifact事务；逐片加载、调用、归一化并立即落盘，最终通过外排
 * result store发布完整结果，失败保留已完成分片且不发布run目录。
 * EN: Owns the atomic artifact transaction for a path-backed semantic plan. It loads, calls, normalizes, and persists
 * one shard at a time, publishes only an externally merged complete result, and retains completed shard audits after
 * failure without publishing a run directory.
 */
public final class SemanticRunArtifactWriter {
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Consumer<Path> NO_SHARED_ARTIFACTS = ignored -> {
    };
    private final RunArtifactPublisher publisher = new RunArtifactPublisher();
    private final SemanticRequestArtifactWriter requestWriter = new SemanticRequestArtifactWriter();
    private final SemanticExtractionPromptBuilder promptBuilder = new SemanticExtractionPromptBuilder();
    private final SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();
    private final RunArtifactFileStore files = new RunArtifactFileStore(JSON);
    private final SemanticRunAuditArtifactWriter audits = new SemanticRunAuditArtifactWriter(files);
    private final SemanticRequestBundlePackageWriter requestPackages =
            new SemanticRequestBundlePackageWriter(files);
    private final SemanticRunManifestFactory manifests = new SemanticRunManifestFactory();

    public Path writeCodexSession(
            Path outputRoot,
            SemanticRunPlan plan,
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
            SemanticRunPlan plan,
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
            SemanticRunPlan plan,
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
        return executeTransaction(
                outputRoot, plan, SemanticEvidenceLookup.from(evidenceStore),
                provider, model, reasoningEffort, retention, sharedArtifactWriter,
                new ExecutionSource() {
                    @Override
                    public SemanticExtractionResult shard(
                            SemanticShardDescriptor ignored,
                            SemanticExtractionPrompt prompt
                    ) {
                        return shardClient.extract(prompt);
                    }

                    @Override
                    public SemanticExtractionResult reconciliation(SemanticExtractionPrompt prompt) {
                        if (reconciliationClient == null) {
                            throw new IllegalArgumentException("semantic reconciliation client is required");
                        }
                        return reconciliationClient.extract(prompt);
                    }
                });
    }

    /**
     * CN: 消费独立response目录中的Codex分片结果，并复用既有owner、normalization、merge、reconciliation
     * 和closure边界完成原子发布。输入请求plan保持只读；缺失响应由上游completion service处理，本方法只接受
     * 完整响应集合，任何越界或冲突都会保留FAILED staging且不发布正式run。
     * EN: Consumes Codex shard results from a separate response directory and reuses the existing ownership,
     * normalization, merge, reconciliation, and closure boundaries for atomic publication. The request plan remains
     * read-only; the completion service handles missing responses, while this method accepts only a complete response
     * set and leaves an unpublished FAILED staging after any ownership or conflict violation.
     */
    public Path completeCodexSession(
            Path outputRoot,
            SemanticRunPlan plan,
            SemanticEvidenceLookup evidenceLookup,
            Path responses,
            String model,
            String reasoningEffort,
            ArtifactRetention retention
    ) {
        if (plan == null || evidenceLookup == null || responses == null) {
            throw new IllegalArgumentException(
                    "semantic Codex completion plan, evidence lookup and responses are required");
        }
        SemanticBoundedJsonReader bounded = new SemanticBoundedJsonReader();
        return executeTransaction(
                outputRoot, plan, evidenceLookup, "codex-session", model, reasoningEffort,
                retention, NO_SHARED_ARTIFACTS, new ExecutionSource() {
                    @Override
                    public SemanticExtractionResult shard(
                            SemanticShardDescriptor shard,
                            SemanticExtractionPrompt ignored
                    ) {
                        ObjectNode raw = bounded.readObject(
                                responses.resolve("shards").resolve(shard.id())
                                        .resolve("semantic-extraction-result.json"),
                                plan.shardMaxOutputTokens(),
                                "semantic Codex shard result");
                        return codexResult(raw);
                    }

                    @Override
                    public SemanticExtractionResult reconciliation(SemanticExtractionPrompt ignored) {
                        ObjectNode patch = bounded.readObject(
                                responses.resolve("reconciliation")
                                        .resolve("semantic-reconciliation-result.json"),
                                plan.reconciliationMaxOutputTokens(),
                                "semantic Codex reconciliation result");
                        return codexResult(patch);
                    }
                });
    }

    /**
     * CN: 统一执行API与Codex来源的顺序分片事务；输入是已验证plan、只读evidence lookup和响应source，
     * 输出仅在全部owner、normalization、merge与closure通过后发布。任何阶段失败都会写安全FAILED manifest，
     * 且不会提交部分正式结果。
     * EN: Runs the shared sequential shard transaction for API and Codex response sources. Given a validated plan,
     * read-only evidence lookup, and response source, it publishes only after ownership, normalization, merge, and
     * closure all pass. Any failure writes a safe FAILED manifest without committing a partial formal result.
     */
    private Path executeTransaction(
            Path outputRoot,
            SemanticRunPlan plan,
            SemanticEvidenceLookup evidenceLookup,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            Consumer<Path> sharedArtifactWriter,
            ExecutionSource source
    ) {
        ArtifactRetention resolved = retention == null ? ArtifactRetention.FULL : retention;
        RunArtifactPublisher.RunDirectory run = publisher.begin(outputRoot);
        List<ShardAudit> completed = new ArrayList<>();
        ReconciliationAudit[] reconciliationAudit = new ReconciliationAudit[1];
        try {
            writeManifest(run, manifests.create(
                    run, plan, "IN_PROGRESS", provider, model, reasoningEffort,
                    resolved, completed, null, null));
            prepareFullBundle(run.stagingDirectory(), plan);
            resolvedWriter(sharedArtifactWriter).accept(run.stagingDirectory());
            try (SemanticResultStore results = new SemanticResultStore(
                    run.stagingDirectory().resolve(".result-work"), evidenceLookup, plan)) {
                for (SemanticShardDescriptor shard : plan.shards()) {
                    ObjectNode bundle = readObject(shard.bundlePath(), "semantic shard bundle");
                    SemanticExtractionPrompt prompt = promptBuilder.build(bundle);
                    requireBudget(prompt, plan.maxInputTokens());
                    SemanticExtractionResult response = source.shard(shard, prompt);
                    ObjectNode normalized = normalize(response.outputText(), bundle);
                    results.append(shard, bundle, normalized);
                    audits.writeShard(
                            run.stagingDirectory(), shard.id(),
                            SemanticExternalAuditReferences.sidecar(shard.bundlePath()),
                            prompt, response, normalized);
                    completed.add(new ShardAudit(shard, response));
                }
                results.finish();
                if (plan.shards().size() > 1 && plan.reconcile()) {
                    SemanticExtractionPrompt prompt = results.reconciliationPrompt(
                            plan, plan.maxInputTokens());
                    SemanticExtractionResult response = source.reconciliation(prompt);
                    JsonNode patch = parseObject(
                            response.outputText(), "semantic reconciliation patch");
                    results.applyReconciliationPatch(patch);
                    audits.writeReconciliationPatch(
                            run.stagingDirectory(), prompt, response, patch);
                    reconciliationAudit[0] = new ReconciliationAudit(prompt, response);
                } else {
                    results.requireConflictFree();
                }
                results.writeMergedDraft(run.stagingDirectory().resolve("merged-draft.json"));
                results.writeFinalDocument(
                        run.stagingDirectory().resolve("semantic-extraction-result.json"));
            }
            ObjectNode complete = manifests.create(
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

    private SemanticExtractionResult codexResult(ObjectNode raw) {
        String output = raw.toString();
        ObjectNode audit = JSON.createObjectNode();
        audit.put("provider", "codex-session");
        audit.putObject("usage").put("input_tokens", 0).put("output_tokens", 0);
        return new SemanticExtractionResult(
                "{\"provider\":\"codex-session\"}", output, output, audit, 1);
    }

    /**
     * CN: 为每个有界 shard 顺序生成 request-only 或 Codex request artifact；输入是 path plan 和 renderer，
     * 输出原子发布的请求 run，预算、写盘或 manifest 失败时不发布半成品。
     * EN: Sequentially renders request-only or Codex artifacts for each bounded shard. It returns an atomically
     * published request run and publishes nothing when budgeting, writing, or manifest creation fails.
     */
    private Path writeRequests(
            Path outputRoot,
            SemanticRunPlan plan,
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
            writeManifest(run, manifests.create(
                    run, plan, "IN_PROGRESS", codex ? "codex-session" : "openai-api",
                    model, reasoningEffort, resolved, List.of(), null, null));
            resolvedWriter(sharedArtifactWriter).accept(run.stagingDirectory());
            for (SemanticShardDescriptor shard : plan.shards()) {
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
                SemanticExtractionPrompt template = new SemanticReconciliationPromptBuilder().template(plan);
                Path directory = run.stagingDirectory().resolve("reconciliation").resolve("template");
                if (codex) {
                    requestWriter.writeCodexSessionReconciliationRequest(
                            directory, template, "semantic-reconciliation-result.json");
                } else if (reconciliationRenderer != null) {
                    requestWriter.writeRequestOnly(
                            directory, template, reconciliationRenderer.apply(template));
                }
            }
            requestPackages.write(run.stagingDirectory(), plan);
            ObjectNode ready = manifests.create(
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

    private void prepareFullBundle(Path staging, SemanticRunPlan plan) {
        files.copyFile(
                plan.fullBundlePath(),
                staging.resolve("full-evidence-bundle.json"),
                "failed to persist semantic evidence bundle");
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

    private void writeFailedManifest(
            RunArtifactPublisher.RunDirectory run,
            SemanticRunPlan plan,
            String provider,
            String model,
            String reasoningEffort,
            ArtifactRetention retention,
            List<ShardAudit> completed,
            ReconciliationAudit reconciliation,
            Throwable failure
    ) {
        try {
            ObjectNode manifest = manifests.create(
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
            files.deleteRecursivelyBestEffort(run.stagingDirectory());
            return published;
        } catch (RuntimeException | Error failure) {
            files.deleteRecursivelyBestEffort(candidate);
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
        files.writeArtifactEntries(
                artifacts,
                files.artifactEntries(
                        artifactRoot,
                        relative -> !"run-manifest.json".equals(relative)));
    }

    private void copyRetained(Path source, Path target) {
        files.copyMatching(
                source,
                target,
                relative -> "semantic-extraction-result.json".equals(relative)
                        || relative.startsWith("deterministic-kg/"),
                "failed to build semantic final-only publish candidate");
    }

    private void writeManifest(RunArtifactPublisher.RunDirectory run, ObjectNode manifest) {
        writeManifestAt(run.stagingDirectory(), manifest);
    }

    private void writeManifestAt(Path directory, ObjectNode manifest) {
        files.writeManifest(directory, manifest);
    }

    private Consumer<Path> resolvedWriter(Consumer<Path> writer) {
        return writer == null ? NO_SHARED_ARTIFACTS : writer;
    }

    private interface ExecutionSource {
        SemanticExtractionResult shard(
                SemanticShardDescriptor shard,
                SemanticExtractionPrompt prompt
        );

        SemanticExtractionResult reconciliation(SemanticExtractionPrompt prompt);
    }

}
