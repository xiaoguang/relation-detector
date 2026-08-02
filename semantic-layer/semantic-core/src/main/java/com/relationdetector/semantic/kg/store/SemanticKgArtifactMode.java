package com.relationdetector.semantic.kg.store;

/**
 * CN: 控制KG验证artifact是完整落盘还是仅执行同等序列化并保存摘要；输入来自semantic CLI/config，
 * 输出只影响artifact持久化，不改变图构建、闭包校验或逻辑JSON字节。
 * EN: Selects full KG artifact persistence or digest-only verification. The mode changes persistence only;
 * graph construction, reference closure, and logical JSON bytes remain identical.
 */
public enum SemanticKgArtifactMode {
    FULL("full"),
    DIGEST_ONLY("digest-only");

    private final String wireValue;

    SemanticKgArtifactMode(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static SemanticKgArtifactMode parse(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "", "full" -> FULL;
            case "digest-only" -> DIGEST_ONLY;
            default -> throw new IllegalArgumentException("unknown semantic KG artifact mode");
        };
    }
}
