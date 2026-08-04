package com.relationdetector.semantic.extraction.artifact;

import com.relationdetector.semantic.extraction.shard.SemanticShardDescriptor;

import com.relationdetector.semantic.extraction.shard.SemanticRunPlan;

import com.relationdetector.semantic.extraction.runtime.SemanticModelCallResult;

import com.relationdetector.semantic.extraction.prompt.SemanticPromptBudgetEstimator;

import com.relationdetector.semantic.extraction.prompt.SemanticExtractionPrompt;

import com.relationdetector.semantic.extraction.config.ArtifactRetention;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 从path plan、分片审计和reconciliation审计构造独立run manifest；输入只含结构元数据和安全异常
 * 类型，输出供artifact事务原子写入。本类不读取模型业务内容、不索引文件，也不发布目录。
 * EN: Builds detached run manifests from a path plan, shard audits, and reconciliation audit. It consumes only
 * structural metadata and a safe failure type for the artifact transaction; it never reads model business content,
 * indexes files, or publishes directories.
 */
public final class SemanticRunManifestFactory {
    private static final ObjectMapper JSON = new ObjectMapper();

    public ObjectNode create(
            RunArtifactPublisher.RunDirectory run,
            SemanticRunPlan plan,
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
        manifest.put("fullBundleHash", plan.fullBundle().sha256());
        manifest.put("maxInputTokens", plan.maxInputTokens());
        manifest.put("shardMaxOutputTokens", plan.shardMaxOutputTokens());
        manifest.put("reconciliationMaxOutputTokens", plan.reconciliationMaxOutputTokens());
        manifest.put("shardCount", plan.shards().size());
        manifest.put("reconcile", plan.reconcile());
        manifest.put("ownedFactCount", plan.ownedFactCount());
        manifest.put("ownedCandidateCount", plan.ownedCandidateCount());
        manifest.put("finalRefClosed", "COMPLETE".equals(status));
        if (failure != null) {
            manifest.put("failureType", failure.getClass().getSimpleName());
        }
        appendShards(manifest.putArray("shards"), plan, completed);
        appendUsage(manifest, completed, reconciliation);
        appendReconciliation(manifest, plan, reconciliation);
        manifest.putNull("publishedAt");
        manifest.putArray("artifacts");
        manifest.putArray("prunedArtifacts");
        return manifest;
    }

    private void appendShards(
            ArrayNode target,
            SemanticRunPlan plan,
            List<ShardAudit> completed
    ) {
        for (SemanticShardDescriptor shard : plan.shards()) {
            ShardAudit audit = completed.stream()
                    .filter(value -> value.shard().id().equals(shard.id()))
                    .findFirst()
                    .orElse(null);
            ObjectNode item = target.addObject();
            item.put("id", shard.id());
            item.put("ownerKey", shard.ownerKey());
            item.put("estimatedInputTokens", shard.estimatedInputTokens());
            item.put("status", audit == null ? "PENDING" : "COMPLETE");
            item.put("actualInputTokens", audit == null ? 0 : audit.result().inputTokens());
            item.put("actualOutputTokens", audit == null ? 0 : audit.result().outputTokens());
            item.put("transportAttempts", audit == null ? 0 : audit.result().transportAttempts());
        }
    }

    private void appendUsage(
            ObjectNode manifest,
            List<ShardAudit> completed,
            ReconciliationAudit reconciliation
    ) {
        int inputTokens = completed.stream().mapToInt(value -> value.result().inputTokens()).sum();
        int outputTokens = completed.stream().mapToInt(value -> value.result().outputTokens()).sum();
        int attempts = completed.stream().mapToInt(value -> value.result().transportAttempts()).sum();
        if (reconciliation != null) {
            inputTokens += reconciliation.result().inputTokens();
            outputTokens += reconciliation.result().outputTokens();
            attempts += reconciliation.result().transportAttempts();
        }
        manifest.putObject("usage")
                .put("inputTokens", inputTokens)
                .put("outputTokens", outputTokens)
                .put("transportAttempts", attempts);
    }

    private void appendReconciliation(
            ObjectNode manifest,
            SemanticRunPlan plan,
            ReconciliationAudit reconciliation
    ) {
        ObjectNode node = manifest.putObject("reconciliation");
        boolean required = plan.shards().size() > 1 && plan.reconcile();
        node.put("required", required);
        node.put("maxInputTokens", plan.maxInputTokens());
        node.put("status", !required ? "NOT_REQUIRED"
                : reconciliation == null ? "PENDING" : "COMPLETE");
        if (reconciliation != null) {
            node.put("estimatedInputTokens",
                    new SemanticPromptBudgetEstimator().estimate(reconciliation.prompt()));
            node.put("tokenEstimateExact", false);
            node.put("inputTokens", reconciliation.result().inputTokens());
            node.put("outputTokens", reconciliation.result().outputTokens());
        }
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    public record ShardAudit(SemanticShardDescriptor shard, SemanticModelCallResult result) {
    }

    public record ReconciliationAudit(
            SemanticExtractionPrompt prompt,
            SemanticModelCallResult result
    ) {
    }
}
