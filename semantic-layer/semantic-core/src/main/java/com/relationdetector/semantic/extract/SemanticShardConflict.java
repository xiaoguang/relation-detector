package com.relationdetector.semantic.extract;

import java.util.List;

/**
 * CN: 描述同 section/stable ID 的不一致 shard 变体；由 merger 输出给 reconciliation，禁止隐式选择最后写入值。
 * EN: Describes inconsistent shard variants for one section and stable id. It is emitted for reconciliation and never selects the last value implicitly.
 */
public record SemanticShardConflict(String section, String id, List<SemanticShardVariant> variants) {
    public SemanticShardConflict {
        variants = List.copyOf(variants == null ? List.of() : variants);
    }
}
