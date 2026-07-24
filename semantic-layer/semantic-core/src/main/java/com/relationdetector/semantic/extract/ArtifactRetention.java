package com.relationdetector.semantic.extract;

import java.util.Locale;

/**
 * CN: 定义成功 semantic extraction run 的产物保留策略；输入来自严格配置，输出供原子 artifact writer
 * 决定是否删除分片和协调 payload，不影响模型执行、语义合并或失败 staging。
 *
 * EN: Defines artifact retention for a successful semantic extraction run. The atomic artifact writer uses this
 * validated configuration only to prune shard and reconciliation payloads; it never changes model execution,
 * semantic merging, or failed staging state.
 */
public enum ArtifactRetention {
    FULL("full"),
    FINAL_ONLY("final-only");

    private final String wireValue;

    ArtifactRetention(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static ArtifactRetention parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "", "full" -> FULL;
            case "final-only" -> FINAL_ONLY;
            default -> throw new IllegalArgumentException("unknown semantic artifact retention");
        };
    }
}
