package com.relationdetector.semantic.extract;

import java.util.Set;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * CN: 保存一个 evidence-closed shard、稳定 owner、归属 refs、overlap refs 和预算估算；由 planner 产生，供 prompt/model runner 消费，不重新生成事实 ID。
 * EN: Carries one evidence-closed shard with stable ownership, overlap references, and its token estimate. It is produced by the planner and never regenerates fact IDs.
 */
public record SemanticShard(
        String id,
        String ownerKey,
        ObjectNode bundle,
        Set<String> ownedFactRefs,
        Set<String> ownedCandidateRefs,
        Set<String> overlapRefs,
        int estimatedInputTokens
) {
    public SemanticShard {
        if (id == null || id.isBlank() || ownerKey == null || ownerKey.isBlank()) {
            throw new IllegalArgumentException("semantic shard identity is required");
        }
        if (bundle == null) {
            throw new IllegalArgumentException("semantic shard bundle is required");
        }
        bundle = bundle.deepCopy();
        ownedFactRefs = Set.copyOf(ownedFactRefs == null ? Set.of() : ownedFactRefs);
        ownedCandidateRefs = Set.copyOf(ownedCandidateRefs == null ? Set.of() : ownedCandidateRefs);
        overlapRefs = Set.copyOf(overlapRefs == null ? Set.of() : overlapRefs);
    }

    @Override
    public ObjectNode bundle() {
        return bundle.deepCopy();
    }

    ObjectNode trustedBundle() {
        return bundle;
    }
}
