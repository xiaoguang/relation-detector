package com.relationdetector.semantic.extract;

import java.nio.file.Path;

/**
 * CN: 描述一个已落盘且通过token门限的semantic shard；输入bundle由planner拥有，执行器一次只加载该路径，
 * 本descriptor不保存prompt、模型response或JSON树。
 * EN: Describes one persisted semantic shard that passed the token gate. Executors load only this path at a time;
 * the descriptor never retains a prompt, model response, or JSON tree.
 */
public record SemanticPathShard(
        String id,
        String ownerKey,
        Path bundlePath,
        int estimatedInputTokens,
        int ownedFactCount,
        int ownedCandidateCount
) {
    public SemanticPathShard {
        if (id == null || id.isBlank() || ownerKey == null || ownerKey.isBlank()
                || bundlePath == null || estimatedInputTokens <= 0
                || ownedFactCount < 0 || ownedCandidateCount < 0) {
            throw new IllegalArgumentException("semantic path shard descriptor is incomplete");
        }
        bundlePath = bundlePath.toAbsolutePath().normalize();
    }
}
