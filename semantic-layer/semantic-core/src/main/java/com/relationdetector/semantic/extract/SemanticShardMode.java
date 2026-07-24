package com.relationdetector.semantic.extract;

/**
 * CN: 控制 evidence bundle 是否按确定性物理图切片；输入来自提取配置，输出供 shard planner 选择单请求或分片路径，禁止改变事实内容。
 * EN: Controls whether an evidence bundle is partitioned by the deterministic physical graph. It selects the single-request or sharded path and never changes fact content.
 */
public enum SemanticShardMode {
    AUTO,
    OFF,
    FORCE;

    public static SemanticShardMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "", "auto" -> AUTO;
            case "off" -> OFF;
            case "force" -> FORCE;
            default -> throw new IllegalArgumentException("unknown semantic shard mode");
        };
    }
}
