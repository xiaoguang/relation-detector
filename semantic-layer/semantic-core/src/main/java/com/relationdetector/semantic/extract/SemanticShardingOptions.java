package com.relationdetector.semantic.extract;

/**
 * CN: 保存一次 semantic extraction 的输入预算、硬上限、分片数量和协调开关；上游是配置加载器，下游是 planner，禁止承担 tokenization 或模型调用。
 * EN: Holds input budgets, hard limits, shard count, and reconciliation policy for one extraction run. It is consumed by the planner and performs neither tokenization nor model calls.
 */
public record SemanticShardingOptions(
        SemanticShardMode mode,
        int targetInputTokens,
        int maxInputTokens,
        int maxShardCount,
        boolean reconcile
) {
    public SemanticShardingOptions {
        mode = mode == null ? SemanticShardMode.AUTO : mode;
        if (targetInputTokens <= 0) {
            throw new IllegalArgumentException("targetInputTokens must be positive");
        }
        if (maxInputTokens < targetInputTokens) {
            throw new IllegalArgumentException("maxInputTokens must be at least targetInputTokens");
        }
        if (maxShardCount <= 0) {
            throw new IllegalArgumentException("maxShardCount must be positive");
        }
    }

    public static SemanticShardingOptions defaults() {
        return new SemanticShardingOptions(SemanticShardMode.AUTO, 240_000, 800_000, 128, true);
    }
}
