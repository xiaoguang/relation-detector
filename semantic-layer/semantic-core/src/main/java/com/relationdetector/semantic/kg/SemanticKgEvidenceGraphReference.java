package com.relationdetector.semantic.kg;

/**
 * CN: 描述 KG wire v2 所引用的唯一 Evidence Graph artifact，保存相对路径、实际序列化摘要和闭包计数；
 * 上游 writer 从同一次序列化得到本值，下游跨文件校验使用它，本 record 不读取文件或解释 evidence。
 * EN: Describes the sole Evidence Graph artifact referenced by KG wire v2, including its relative path, digest,
 * and closure counts. Writers create it from the same serialization and downstream validation consumes it; this
 * record neither reads files nor interprets evidence.
 */
public record SemanticKgEvidenceGraphReference(
        String path,
        String sha256,
        long evidenceRefCount,
        long diagnosticCount
) {
    public SemanticKgEvidenceGraphReference {
        if (path == null || path.isBlank()
                || sha256 == null || !sha256.matches("[0-9a-f]{64}")
                || evidenceRefCount < 0 || diagnosticCount < 0) {
            throw new IllegalArgumentException("semantic evidence graph reference is invalid");
        }
    }
}
