package com.relationdetector.semantic.extraction.shard;

import com.relationdetector.semantic.extraction.artifact.SemanticArtifactRef;

/**
 * CN: 描述一个有强摘要 bundle 与 external-audit sidecar 引用的 bounded semantic shard。
 * EN: Describes one bounded semantic shard using strong references for both its bundle and external-audit sidecar.
 */
public record SemanticShardDescriptor(
        String id,
        String ownerKey,
        SemanticArtifactRef bundle,
        SemanticArtifactRef externalAuditSidecar,
        int estimatedInputTokens,
        int ownedFactCount,
        int ownedCandidateCount
) {
    public SemanticShardDescriptor {
        if (id == null || id.isBlank() || ownerKey == null || ownerKey.isBlank()
                || bundle == null || externalAuditSidecar == null || estimatedInputTokens <= 0
                || ownedFactCount < 0 || ownedCandidateCount < 0) {
            throw new IllegalArgumentException("semantic path shard descriptor is incomplete");
        }
    }
}
