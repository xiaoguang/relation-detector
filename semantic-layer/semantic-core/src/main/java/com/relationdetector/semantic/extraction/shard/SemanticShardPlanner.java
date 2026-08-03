package com.relationdetector.semantic.extraction.shard;

import com.relationdetector.semantic.extraction.config.SemanticShardingOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.relationdetector.semantic.ingest.ScanResultContractException;
import com.relationdetector.semantic.evidence.SemanticEvidenceStore;

/**
 * CN: 将完整磁盘evidence store交给全局owner planner并生成path-backed shard plan；字节运输窗口不参与
 * semantic边界，执行链一次只加载一个token受限root或shard。
 * EN: Delegates the complete disk-backed evidence store to the global owner planner and produces a path-backed
 * shard plan. Byte transport windows do not participate in semantic boundaries, and execution loads only one
 * token-bounded root or shard at a time.
 */
public final class SemanticShardPlanner {
    public SemanticRunPlan plan(
            SemanticEvidenceStore evidenceStore,
            Path workspace,
            SemanticShardingOptions options,
            int shardMaxOutputTokens,
            int reconciliationMaxOutputTokens
    ) {
        if (evidenceStore == null || workspace == null
                || shardMaxOutputTokens <= 0 || reconciliationMaxOutputTokens <= 0) {
            throw new IllegalArgumentException(
                    "semantic evidence store, path-plan workspace and output budgets are required");
        }
        SemanticShardingOptions resolved = options == null ? SemanticShardingOptions.defaults() : options;
        try {
            if (Files.exists(workspace)) {
                throw new SemanticShardingException("semantic path-plan workspace already exists");
            }
            Files.createDirectories(workspace);
            Path fullBundle = workspace.resolve("full-evidence-bundle.json");
            String fullHash = evidenceStore.writeBundleAndHash(fullBundle);
            return new SemanticGlobalOwnerPlanner().plan(
                    evidenceStore, workspace, resolved, fullBundle, fullHash,
                    shardMaxOutputTokens, reconciliationMaxOutputTokens);
        } catch (IOException failure) {
            throw new ScanResultContractException("failed to create semantic path-backed plan", failure);
        }
    }

}
