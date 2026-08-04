package com.relationdetector.semantic.extraction.artifact;

/**
 * CN: 定义 request package 重建时由调用方提供的可信物化上限；包内声明只能收紧文件、记录和 token 预算，不能提高这些上限。
 * EN: Defines caller-trusted materialization limits for request-package reconstruction; package declarations may
 * tighten file, record, and token budgets but can never raise these limits.
 */
public record SemanticRequestPackageLimits(
        long maxIndexBytes,
        int maxShards,
        int maxEstimatedTokensPerShardOrRecord,
        long maxOwnerManifestBytes,
        long maxSidecarBytes,
        long maxCompressedEvidenceBytes,
        long maxReconstructedBytes,
        int maxLineBytes,
        int maxJsonDepth,
        int maxStringCodePoints
) {
    private static final long MIB = 1024L * 1024L;
    private static final int TOKEN_ESTIMATE_FIXED_OVERHEAD = 64;

    public SemanticRequestPackageLimits {
        if (maxIndexBytes <= 0 || maxShards <= 0
                || maxEstimatedTokensPerShardOrRecord <= 0
                || maxOwnerManifestBytes <= 0 || maxSidecarBytes <= 0
                || maxCompressedEvidenceBytes <= 0 || maxReconstructedBytes <= 0
                || maxLineBytes <= 0 || maxJsonDepth <= 0 || maxStringCodePoints <= 0) {
            throw new IllegalArgumentException("semantic request package limits must be positive");
        }
    }

    public static SemanticRequestPackageLimits defaults() {
        return new SemanticRequestPackageLimits(
                64L * MIB,
                4096,
                8_000_000,
                256L * MIB,
                1024L * MIB,
                8L * 1024L * MIB,
                64L * 1024L * MIB,
                (int) MIB,
                128,
                (int) MIB);
    }

    /**
     * Exact inverse of the conservative minimum UTF-8 byte estimate:
     * {@code ceil((ceil(bytes/4)+64)*115/100)}.
     */
    public long maximumJsonBytesForEstimatedTokens(long estimatedTokens) {
        if (estimatedTokens <= 0) {
            throw new IllegalArgumentException("estimated token limit must be positive");
        }
        long maximumBase = Math.floorDiv(Math.multiplyExact(estimatedTokens, 100L), 115L);
        long byteUnits = maximumBase - TOKEN_ESTIMATE_FIXED_OVERHEAD;
        return byteUnits <= 0 ? 0 : Math.multiplyExact(byteUnits, 4L);
    }
}
