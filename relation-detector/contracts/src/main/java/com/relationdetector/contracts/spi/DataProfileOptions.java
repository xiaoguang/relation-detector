package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.Enums.OfflineSampleCompleteness;

/**
 * Bounded data profiling options resolved from runtime configuration.
 */
public record DataProfileOptions(
        int sampleRows,
        int timeoutSeconds,
        int maxCandidatePairs,
        int maxDistinctValues,
        int maxTargetsPerSourceColumn,
        double minContainmentRatio,
        double minOverlapRatio,
        double maxMismatchRatio,
        int minDistinctValues,
        int minRowsForNegative,
        boolean verifyDeclaredForeignKeys,
        boolean discoverFromNamingEvidence,
        boolean useOfflineInsertSamples,
        OfflineSampleCompleteness offlineSampleCompleteness,
        boolean skipUnindexedLargeTargets
) {
    public DataProfileOptions {
        if (sampleRows <= 0 || timeoutSeconds <= 0 || maxCandidatePairs <= 0
                || maxDistinctValues <= 0 || maxTargetsPerSourceColumn <= 0
                || minDistinctValues <= 0 || minRowsForNegative <= 0) {
            throw new IllegalArgumentException("data profile limits must be positive");
        }
        validateRatio(minContainmentRatio, "minContainmentRatio");
        validateRatio(minOverlapRatio, "minOverlapRatio");
        validateRatio(maxMismatchRatio, "maxMismatchRatio");
        if (offlineSampleCompleteness == null) {
            offlineSampleCompleteness = OfflineSampleCompleteness.PARTIAL;
        }
    }

    public static DataProfileOptions defaults() {
        return new DataProfileOptions(
                10_000,
                30,
                1_000,
                5_000,
                3,
                0.98d,
                0.80d,
                0.50d,
                20,
                100,
                false,
                false,
                true,
                OfflineSampleCompleteness.PARTIAL,
                true);
    }

    public static DataProfileOptions defaults(int sampleRows, int timeoutSeconds, int maxCandidatePairs) {
        return defaults()
                .withSampleRows(sampleRows)
                .withTimeoutSeconds(timeoutSeconds)
                .withMaxCandidatePairs(maxCandidatePairs);
    }

    public DataProfileOptions withSampleRows(int value) {
        return copy(value, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withTimeoutSeconds(int value) {
        return copy(sampleRows, value, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxCandidatePairs(int value) {
        return copy(sampleRows, timeoutSeconds, value, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxDistinctValues(int value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, value, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxTargetsPerSourceColumn(int value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, value,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinContainmentRatio(double value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                value, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinOverlapRatio(double value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, value, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMaxMismatchRatio(double value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, value, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinDistinctValues(int value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, value, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withMinRowsForNegative(int value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, value,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withVerifyDeclaredForeignKeys(boolean value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                value, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withDiscoverFromNamingEvidence(boolean value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, value, useOfflineInsertSamples,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withUseOfflineInsertSamples(boolean value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, value,
                offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withOfflineSampleCompleteness(OfflineSampleCompleteness value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                value, skipUnindexedLargeTargets);
    }

    public DataProfileOptions withSkipUnindexedLargeTargets(boolean value) {
        return copy(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues, maxTargetsPerSourceColumn,
                minContainmentRatio, minOverlapRatio, maxMismatchRatio, minDistinctValues, minRowsForNegative,
                verifyDeclaredForeignKeys, discoverFromNamingEvidence, useOfflineInsertSamples,
                offlineSampleCompleteness, value);
    }

    private static DataProfileOptions copy(
            int sampleRows,
            int timeoutSeconds,
            int maxCandidatePairs,
            int maxDistinctValues,
            int maxTargetsPerSourceColumn,
            double minContainmentRatio,
            double minOverlapRatio,
            double maxMismatchRatio,
            int minDistinctValues,
            int minRowsForNegative,
            boolean verifyDeclaredForeignKeys,
            boolean discoverFromNamingEvidence,
            boolean useOfflineInsertSamples,
            OfflineSampleCompleteness offlineSampleCompleteness,
            boolean skipUnindexedLargeTargets
    ) {
        return new DataProfileOptions(sampleRows, timeoutSeconds, maxCandidatePairs, maxDistinctValues,
                maxTargetsPerSourceColumn, minContainmentRatio, minOverlapRatio, maxMismatchRatio,
                minDistinctValues, minRowsForNegative, verifyDeclaredForeignKeys, discoverFromNamingEvidence,
                useOfflineInsertSamples, offlineSampleCompleteness, skipUnindexedLargeTargets);
    }

    private static void validateRatio(double value, String name) {
        if (Double.isNaN(value) || value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
