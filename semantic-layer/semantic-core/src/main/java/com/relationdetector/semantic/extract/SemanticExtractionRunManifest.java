package com.relationdetector.semantic.extract;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 根据 in-memory extraction plan、已完成 shard 和 reconciliation audit 构造运行 manifest，并用
 * RunArtifactFileStore登记 retention 后的文件大小与 SHA-256。输入不包含异常正文，输出是独立 JSON；
 * 本类不执行模型、写业务 payload 或发布目录。
 * EN: Builds run manifests from an in-memory extraction plan, completed shards, and reconciliation audit, then
 * indexes retained artifacts through RunArtifactFileStore. It neither executes models nor writes domain payloads or
 * publishes directories, and it never records exception messages.
 */
final class SemanticExtractionRunManifest {
    private final ObjectMapper json;
    private final RunArtifactFileStore files;

    SemanticExtractionRunManifest(ObjectMapper json, RunArtifactFileStore files) {
        this.json = json;
        this.files = files;
    }

    ObjectNode create(
            SemanticExtractionRunPlan plan,
            String status,
            List<SemanticShardExecution> executions,
            SemanticExtractionRunResult run,
            ReconciliationAudit reconciliationAudit,
            String provider,
            String model,
            String reasoningEffort
    ) {
        ObjectNode manifest = json.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("status", status);
        manifest.put("provider", blankDefault(provider, "codex-session"));
        manifest.put("model", blankDefault(model, ""));
        manifest.put("reasoningEffort", blankDefault(reasoningEffort, ""));
        manifest.put("fullBundleHash", plan.shardPlan().fullBundleHash());
        manifest.put("maxInputTokens", plan.maxInputTokens());
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
        addUsage(manifest, executions, run, reconciliationAudit);
        addReconciliation(manifest, plan, run, reconciliationAudit);
        return manifest;
    }

    void finish(
            ObjectNode manifest,
            RunArtifactPublisher.RunDirectory runDirectory,
            ArtifactRetention retention,
            Instant publishedAt,
            List<RunArtifactFileStore.ArtifactEntry> pruned,
            Path artifactRoot
    ) {
        manifest.put("runId", runDirectory.runId());
        manifest.put("retention", retention.wireValue());
        if (publishedAt == null) {
            manifest.putNull("publishedAt");
        } else {
            manifest.put("publishedAt", publishedAt.toString());
        }
        files.writeArtifactEntries(
                manifest.putArray("artifacts"),
                files.artifactEntries(
                        artifactRoot,
                        relative -> !"run-manifest.json".equals(relative)));
        files.writeArtifactEntries(manifest.putArray("prunedArtifacts"), pruned);
    }

    List<RunArtifactFileStore.ArtifactEntry> prunedArtifactEntries(Path output) {
        return files.artifactEntries(
                output,
                relative -> !retainedFinalArtifact(relative));
    }

    void copyRetainedFinalArtifacts(Path source, Path target) {
        files.copyMatching(
                source,
                target,
                relative -> retainedFinalArtifact(relative)
                        && !"run-manifest.json".equals(relative),
                "failed to build semantic extraction publish candidate");
    }

    ReconciliationAudit reconciliationAudit(SemanticExtractionRunResult run) {
        if (run == null || run.reconciliationPrompt() == null || run.reconciliationResult() == null) {
            return null;
        }
        return new ReconciliationAudit(
                run.reconciliationPrompt(),
                run.reconciliationResult(),
                run.reconciliationPatch());
    }

    private boolean retainedFinalArtifact(String relative) {
        return "semantic-extraction-result.json".equals(relative)
                || "run-manifest.json".equals(relative)
                || relative.startsWith("deterministic-kg/");
    }

    private void addUsage(
            ObjectNode manifest,
            List<SemanticShardExecution> executions,
            SemanticExtractionRunResult run,
            ReconciliationAudit reconciliationAudit
    ) {
        int inputTokens = executions.stream().mapToInt(value -> value.result().inputTokens()).sum();
        int outputTokens = executions.stream().mapToInt(value -> value.result().outputTokens()).sum();
        int attempts = executions.stream().mapToInt(value -> value.result().transportAttempts()).sum();
        SemanticExtractionResult reconciliation = run != null
                ? run.reconciliationResult()
                : reconciliationAudit == null ? null : reconciliationAudit.result();
        if (reconciliation != null) {
            inputTokens += reconciliation.inputTokens();
            outputTokens += reconciliation.outputTokens();
            attempts += reconciliation.transportAttempts();
        }
        manifest.putObject("usage")
                .put("inputTokens", inputTokens)
                .put("outputTokens", outputTokens)
                .put("transportAttempts", attempts);
    }

    private void addReconciliation(
            ObjectNode manifest,
            SemanticExtractionRunPlan plan,
            SemanticExtractionRunResult run,
            ReconciliationAudit reconciliationAudit
    ) {
        ObjectNode reconciliation = manifest.putObject("reconciliation");
        boolean required = plan.shardRequests().size() > 1 && plan.reconcile();
        reconciliation.put("required", required);
        reconciliation.put("maxInputTokens", plan.maxInputTokens());
        if (!required) {
            reconciliation.put("status", "NOT_REQUIRED");
            return;
        }
        SemanticExtractionPrompt prompt = run != null
                ? run.reconciliationPrompt()
                : reconciliationAudit == null ? null : reconciliationAudit.prompt();
        SemanticExtractionResult result = run != null
                ? run.reconciliationResult()
                : reconciliationAudit == null ? null : reconciliationAudit.result();
        if (result == null) {
            reconciliation.put("status", "PENDING");
            return;
        }
        reconciliation.put("status", "COMPLETE");
        reconciliation.put("estimatedInputTokens",
                new SemanticPromptBudgetEstimator().estimate(prompt));
        reconciliation.put("tokenEstimateExact", false);
        reconciliation.put("inputTokens", result.inputTokens());
        reconciliation.put("outputTokens", result.outputTokens());
        reconciliation.put("transportAttempts", result.transportAttempts());
    }

    private String blankDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    record ReconciliationAudit(
            SemanticExtractionPrompt prompt,
            SemanticExtractionResult result,
            JsonNode patch
    ) {
    }
}
