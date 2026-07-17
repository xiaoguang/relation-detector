package com.relationdetector.contracts.spi;

/**
 * CN: live JDBC 数据画像的有界运行参数；不包含离线样本配置。
 *
 * <p>EN: Bounded runtime options for live JDBC profiling. Offline sample
 * options are intentionally outside the SPI because no offline producer exists.
 */
public record DataProfileOptions(
        int timeoutSeconds,
        int maxCandidatePairs,
        int maxTargetsPerSourceColumn,
        double minContainmentRatio,
        double minOverlapRatio,
        double maxMismatchRatio,
        int minDistinctValues,
        int minRowsForNegative,
        boolean verifyDeclaredForeignKeys,
        boolean discoverFromNamingEvidence,
        boolean skipUnindexedLargeTargets
) {
    public DataProfileOptions {
        if (timeoutSeconds <= 0 || maxCandidatePairs <= 0 || maxTargetsPerSourceColumn <= 0
                || minDistinctValues <= 0 || minRowsForNegative <= 0) {
            throw new IllegalArgumentException("data profile limits must be positive");
        }
        validateRatio(minContainmentRatio, "minContainmentRatio");
        validateRatio(minOverlapRatio, "minOverlapRatio");
        validateRatio(maxMismatchRatio, "maxMismatchRatio");
    }

    public static DataProfileOptions defaults() {
        return new DataProfileOptions(30, 1_000, 3, 0.98d, 0.80d, 0.50d,
                20, 100, false, false, true);
    }

    public DataProfileOptions withTimeoutSeconds(int value) {
        return copy(value, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxCandidatePairs(int value) {
        return copy(timeoutSeconds, value, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxTargetsPerSourceColumn(int value) {
        return copy(timeoutSeconds, maxCandidatePairs, value, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinContainmentRatio(double value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, value,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinOverlapRatio(double value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                value, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxMismatchRatio(double value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, value, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinDistinctValues(int value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, value, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinRowsForNegative(int value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, value,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withVerifyDeclaredForeignKeys(boolean value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                value, discoverFromNamingEvidence, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withDiscoverFromNamingEvidence(boolean value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, value, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withSkipUnindexedLargeTargets(boolean value) {
        return copy(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn, minContainmentRatio,
                minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, value);
    }

    private static DataProfileOptions copy(
            int timeoutSeconds,
            int maxCandidatePairs,
            int maxTargetsPerSourceColumn,
            double minContainmentRatio,
            double minOverlapRatio,
            double maxMismatchRatio,
            int minDistinctValues,
            int minRowsForNegative,
            boolean verifyDeclaredForeignKeys,
            boolean discoverFromNamingEvidence,
            boolean skipUnindexedLargeTargets
    ) {
        return new DataProfileOptions(timeoutSeconds, maxCandidatePairs, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues,
                minRowsForNegative, verifyDeclaredForeignKeys, discoverFromNamingEvidence,
                skipUnindexedLargeTargets);
    }

    private static void validateRatio(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
