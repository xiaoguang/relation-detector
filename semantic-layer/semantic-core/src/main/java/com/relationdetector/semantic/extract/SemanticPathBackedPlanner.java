package com.relationdetector.semantic.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relationdetector.semantic.reader.ScanResultContractException;
import com.relationdetector.semantic.reader.SemanticEvidenceStore;
import com.relationdetector.semantic.reader.SemanticMetadataInventoryEnvelope;

/**
 * CN: 从磁盘evidence store逐个读取bounded component并生成path-backed shard plan；AUTO只在目标预算内合并当前
 * component，超预算交给现有typed planner拆分，禁止把完整bundle或全部prompt装入内存。
 * EN: Reads bounded components one at a time from the disk evidence store and creates a path-backed shard plan.
 * AUTO packs only the current token-bounded batch and delegates oversized units to the typed planner, never loading
 * the complete bundle or every prompt.
 */
public final class SemanticPathBackedPlanner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ARRAY_SECTIONS = List.of(
            "tables", "evidence", "metadataTables", "metadataColumns", "metadataConstraints", "metadataIndexes",
            "relationships", "lineage", "eventCandidates", "derivedRelationships", "derivedLineage",
            "namingEvidence", "reviewItemCandidates", "tripletCandidates", "diagnostics");
    private final SemanticExtractionService boundedPlanner = new SemanticExtractionService();
    private final SemanticExtractionPromptBuilder promptBuilder = new SemanticExtractionPromptBuilder();
    private final SemanticPromptBudgetEstimator estimator = new SemanticPromptBudgetEstimator();

    public SemanticPathRunPlan plan(
            SemanticEvidenceStore evidenceStore,
            Path workspace,
            SemanticShardingOptions options
    ) {
        if (evidenceStore == null || workspace == null) {
            throw new IllegalArgumentException("semantic evidence store and path-plan workspace are required");
        }
        SemanticShardingOptions resolved = options == null ? SemanticShardingOptions.defaults() : options;
        try {
            if (Files.exists(workspace)) {
                throw new SemanticShardingException("semantic path-plan workspace already exists");
            }
            Files.createDirectories(workspace);
            Path fullBundle = workspace.resolve("full-evidence-bundle.json");
            String fullHash = evidenceStore.writeBundleAndHash(fullBundle);
            List<SemanticPathShard> shards = resolved.mode() == SemanticShardMode.OFF
                    ? planUnsharded(fullBundle, workspace, resolved)
                    : planComponents(evidenceStore, fullBundle, workspace, resolved);
            if (shards.size() > resolved.maxShardCount()) {
                throw new SemanticShardingException("semantic shard count exceeds configured maximum");
            }
            return new SemanticPathRunPlan(
                    fullBundle, fullHash, shards, resolved.reconcile(), resolved.maxInputTokens());
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to create semantic path-backed plan", failure);
        }
    }

    private List<SemanticPathShard> planUnsharded(
            Path fullBundle,
            Path workspace,
            SemanticShardingOptions options
    ) throws IOException {
        long conservativeBytes = (long) options.maxInputTokens() * 8L;
        if (Files.size(fullBundle) > conservativeBytes) {
            throw new SemanticShardingException(
                    "semantic evidence bundle exceeds the configured input budget while sharding is off");
        }
        ObjectNode bundle = requireObject(JSON.readTree(fullBundle.toFile()));
        return persistBoundedPlan(bundle, workspace, options, 1);
    }

    private List<SemanticPathShard> planComponents(
            SemanticEvidenceStore evidenceStore,
            Path fullBundle,
            Path workspace,
            SemanticShardingOptions options
    ) {
        List<SemanticPathShard> result = new ArrayList<>();
        ObjectNode[] current = {null};
        int[] nextSequence = {1};
        evidenceStore.forEachComponent(component -> {
            ObjectNode value = readBundle(component.path());
            value.set(
                    "metadataInventory",
                    SemanticMetadataInventoryEnvelope.from(evidenceStore.descriptor().inventory()));
            if (options.mode() == SemanticShardMode.FORCE) {
                result.addAll(persistBoundedPlan(value, workspace, options, nextSequence[0]));
                nextSequence[0] = result.size() + 1;
                return;
            }
            if (current[0] == null) {
                current[0] = value;
                return;
            }
            ObjectNode candidate = merge(current[0], value);
            if (estimate(candidate) <= options.targetInputTokens()) {
                current[0] = candidate;
                return;
            }
            result.addAll(persistBoundedPlan(current[0], workspace, options, nextSequence[0]));
            nextSequence[0] = result.size() + 1;
            current[0] = value;
        });
        if (current[0] != null) {
            result.addAll(persistBoundedPlan(current[0], workspace, options, nextSequence[0]));
        }
        if (result.isEmpty()) {
            try {
                return planUnsharded(fullBundle, workspace, options);
            } catch (IOException failure) {
                throw new ScanResultContractException(
                        "failed to plan an empty semantic evidence bundle", failure);
            }
        }
        return List.copyOf(result);
    }

    private List<SemanticPathShard> persistBoundedPlan(
            ObjectNode bundle,
            Path workspace,
            SemanticShardingOptions options,
            int firstSequence
    ) {
        SemanticShardingOptions boundedOptions = new SemanticShardingOptions(
                options.mode() == SemanticShardMode.FORCE ? SemanticShardMode.FORCE : SemanticShardMode.AUTO,
                options.targetInputTokens(),
                options.maxInputTokens(),
                options.maxShardCount(),
                options.reconcile());
        SemanticExtractionRunPlan inMemory = boundedPlanner.plan(bundle, boundedOptions);
        List<SemanticPathShard> result = new ArrayList<>();
        int sequence = firstSequence;
        for (SemanticShardRequest request : inMemory.shardRequests()) {
            String id = "shard-%04d".formatted(sequence++);
            ObjectNode persisted = request.shard().bundle();
            ObjectNode context = (ObjectNode) persisted.path("shardContext");
            context.put("shardId", id);
            Path directory = workspace.resolve("shards").resolve(id);
            Path path = directory.resolve("evidence-bundle.json");
            try {
                Files.createDirectories(directory);
                JSON.writeValue(path.toFile(), persisted);
            } catch (IOException failure) {
                throw new ScanResultContractException("failed to persist semantic shard bundle", failure);
            }
            result.add(new SemanticPathShard(
                    id,
                    request.shard().ownerKey(),
                    path,
                    estimate(persisted),
                    context.path("ownedFactRefs").size(),
                    context.path("ownedCandidateRefs").size()));
        }
        return result;
    }

    private int estimate(ObjectNode bundle) {
        return estimator.estimate(promptBuilder.build(bundle));
    }

    private ObjectNode merge(ObjectNode left, ObjectNode right) {
        ObjectNode result = left.deepCopy();
        for (String section : ARRAY_SECTIONS) {
            ArrayNode output = result.withArray(section);
            JsonNode values = right.path(section);
            if (!values.isArray()) {
                throw new SemanticShardingException("semantic component section must be an array: " + section);
            }
            values.forEach(value -> output.add(value.deepCopy()));
        }
        return result;
    }

    private ObjectNode readBundle(Path path) {
        try {
            return requireObject(JSON.readTree(path.toFile()));
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to read bounded semantic component bundle", failure);
        }
    }

    private ObjectNode requireObject(JsonNode value) {
        if (value == null || !value.isObject()) {
            throw new SemanticShardingException("semantic evidence bundle must be an object");
        }
        return (ObjectNode) value;
    }
}
