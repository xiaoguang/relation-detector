package com.relationdetector.core.profile;

/**
 *
 * Aggregated, non-sensitive profiling metrics for one relationship candidate.
 */
public record DataProfileMetrics(
        String profileMode,
        long sourceNonNullRows,
        long sourceDistinctValues,
        long matchedDistinctSourceValues,
        long missingDistinctSourceValues,
        long targetDistinctValues,
        boolean queryTimedOut,
        boolean permissionDenied,
        boolean partialSample
) {
    public DataProfileMetrics {
        profileMode = profileMode == null || profileMode.isBlank() ? "LIVE_DATABASE" : profileMode;
        sourceNonNullRows = Math.max(0, sourceNonNullRows);
        sourceDistinctValues = Math.max(0, sourceDistinctValues);
        matchedDistinctSourceValues = Math.max(0, matchedDistinctSourceValues);
        missingDistinctSourceValues = Math.max(0, missingDistinctSourceValues);
        targetDistinctValues = Math.max(0, targetDistinctValues);
    }

    public static DataProfileMetrics live(
            long sourceNonNullRows,
            long sourceDistinctValues,
            long matchedDistinctSourceValues,
            long missingDistinctSourceValues,
            long targetDistinctValues,
            boolean queryTimedOut,
            boolean permissionDenied
    ) {
        return new DataProfileMetrics("LIVE_DATABASE", sourceNonNullRows, sourceDistinctValues,
                matchedDistinctSourceValues, missingDistinctSourceValues, targetDistinctValues,
                queryTimedOut, permissionDenied, false);
    }

    public static DataProfileMetrics offlinePartial(
            long sourceNonNullRows,
            long sourceDistinctValues,
            long matchedDistinctSourceValues,
            long missingDistinctSourceValues,
            long targetDistinctValues
    ) {
        return new DataProfileMetrics("OFFLINE_INSERT_SAMPLE", sourceNonNullRows, sourceDistinctValues,
                matchedDistinctSourceValues, missingDistinctSourceValues, targetDistinctValues,
                false, false, true);
    }
}
