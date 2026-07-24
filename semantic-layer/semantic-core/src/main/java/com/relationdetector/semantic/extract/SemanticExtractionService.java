package com.relationdetector.semantic.extract;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 编排完整 bundle 的 shard planning、逐片模型调用、片内归一化、deterministic merge、全局协调和最终闭包校验；输入不修改，任一失败不返回部分结果。
 * EN: Orchestrates sharding, per-shard model calls, shard normalization, deterministic merge, global reconciliation, and final closure validation. Any failure returns no partial result.
 */
public final class SemanticExtractionService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final SemanticShardPlanner shardPlanner = new SemanticShardPlanner();
    private final SemanticExtractionPromptBuilder promptBuilder = new SemanticExtractionPromptBuilder();
    private final SemanticExtractionDocumentNormalizer normalizer = new SemanticExtractionDocumentNormalizer();
    private final SemanticShardOutputOwnershipValidator ownershipValidator =
            new SemanticShardOutputOwnershipValidator();
    private final SemanticShardResultMerger merger = new SemanticShardResultMerger();
    private final SemanticReconciliationPromptBuilder reconciliationPromptBuilder =
            new SemanticReconciliationPromptBuilder();
    private final SemanticReconciliationPatchValidator patchValidator =
            new SemanticReconciliationPatchValidator();

    public SemanticExtractionRunPlan plan(ObjectNode fullBundle, SemanticShardingOptions options) {
        SemanticShardingOptions resolved = options == null ? SemanticShardingOptions.defaults() : options;
        SemanticShardPlan shardPlan = shardPlanner.plan(fullBundle, resolved);
        List<SemanticShardRequest> requests = shardPlan.shards().stream()
                .map(shard -> new SemanticShardRequest(shard, promptBuilder.build(shard.trustedBundle())))
                .toList();
        return new SemanticExtractionRunPlan(fullBundle, shardPlan, requests, resolved.reconcile());
    }

    /**
     * CN: 顺序执行所有 shard，完整成功后才 merge/reconcile/final-normalize；输入效果是模型调用，输出完整 run result，模型、JSON或闭包失败均原子抛出。
     * EN: Executes shards sequentially and merges, reconciles, and globally normalizes only after all succeed. Model, JSON, or closure failure aborts atomically.
     */
    public SemanticExtractionRunResult execute(
            SemanticExtractionRunPlan plan,
            SemanticModelClient shardClient,
            SemanticModelClient reconciliationClient
    ) {
        if (plan == null || shardClient == null) {
            throw new IllegalArgumentException("semantic extraction plan and shard client are required");
        }
        List<SemanticShardExecution> executions = new ArrayList<>();
        for (SemanticShardRequest request : plan.shardRequests()) {
            SemanticExtractionResult result = shardClient.extract(request.prompt());
            ObjectNode normalized = normalize(result.outputText(), request.shard());
            executions.add(new SemanticShardExecution(request, result, normalized));
        }
        SemanticShardMergeResult merge = merger.merge(executions.stream()
                .map(execution -> new SemanticShardNormalizedResult(
                        execution.request().shard().id(), execution.trustedNormalizedDocument()))
                .toList(), plan.shardPlan());

        SemanticExtractionPrompt reconciliationPrompt = null;
        SemanticExtractionResult reconciliationResult = null;
        JsonNode reconciliationPatch = null;
        ObjectNode mergedDraft;
        if (executions.size() > 1 && plan.reconcile()) {
            if (reconciliationClient == null) {
                throw new IllegalArgumentException("semantic reconciliation client is required");
            }
            reconciliationPrompt = reconciliationPromptBuilder.build(merge, plan.shardPlan());
            reconciliationResult = reconciliationClient.extract(reconciliationPrompt);
            reconciliationPatch = parse(reconciliationResult.outputText(), "semantic reconciliation patch");
            mergedDraft = patchValidator.apply(merge, reconciliationPatch, plan.trustedFullBundle());
        } else {
            merge.requireConflictFree();
            mergedDraft = merge.trustedMergedDocument().deepCopy();
        }
        ObjectNode finalDocument = normalizer.normalize(mergedDraft, plan.trustedFullBundle());
        return new SemanticExtractionRunResult(plan, executions, merge, reconciliationPrompt,
                reconciliationResult, reconciliationPatch, mergedDraft, finalDocument);
    }

    private ObjectNode normalize(String output, SemanticShard shard) {
        JsonNode raw = parse(output, "semantic shard result");
        ownershipValidator.validate(raw, shard);
        return normalizer.normalize(raw, shard.trustedBundle());
    }

    private JsonNode parse(String output, String label) {
        try {
            JsonNode value = JSON.readTree(output);
            if (value == null || !value.isObject()) {
                throw new SemanticExtractionValidationException(label + " must be a JSON object");
            }
            return value;
        } catch (SemanticExtractionValidationException error) {
            throw error;
        } catch (Exception error) {
            throw new SemanticExtractionValidationException(label + " must be valid JSON");
        }
    }
}
