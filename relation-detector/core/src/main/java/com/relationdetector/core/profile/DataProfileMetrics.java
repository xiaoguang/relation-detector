package com.relationdetector.core.profile;

/**
 * Aggregated, non-sensitive profiling metrics for one relationship candidate.
 */
public record DataProfileMetrics(
        String profileMode,
        long sourceNonNullRowsSampled,
        long sourceDistinctValuesSampled,
        long matchedDistinctSourceValues,
        long missingDistinctSourceValues,
        long targetDistinctValuesSampled,
        boolean queryTimedOut,
        boolean permissionDenied,
        boolean partialSample
) {
    public DataProfileMetrics {
        profileMode = profileMode == null || profileMode.isBlank() ? "LIVE_DATABASE" : profileMode;
        sourceNonNullRowsSampled = Math.max(0, sourceNonNullRowsSampled);
        sourceDistinctValuesSampled = Math.max(0, sourceDistinctValuesSampled);
        matchedDistinctSourceValues = Math.max(0, matchedDistinctSourceValues);
        missingDistinctSourceValues = Math.max(0, missingDistinctSourceValues);
        targetDistinctValuesSampled = Math.max(0, targetDistinctValuesSampled);
    }

    public static DataProfileMetrics live(
            long sourceNonNullRowsSampled,
            long sourceDistinctValuesSampled,
            long matchedDistinctSourceValues,
            long missingDistinctSourceValues,
            long targetDistinctValuesSampled,
            boolean queryTimedOut,
            boolean permissionDenied
    ) {
        return new DataProfileMetrics("LIVE_DATABASE", sourceNonNullRowsSampled, sourceDistinctValuesSampled,
                matchedDistinctSourceValues, missingDistinctSourceValues, targetDistinctValuesSampled,
                queryTimedOut, permissionDenied, false);
    }

    public static DataProfileMetrics offlinePartial(
            long sourceNonNullRowsSampled,
            long sourceDistinctValuesSampled,
            long matchedDistinctSourceValues,
            long missingDistinctSourceValues,
            long targetDistinctValuesSampled
    ) {
        return new DataProfileMetrics("OFFLINE_INSERT_SAMPLE", sourceNonNullRowsSampled, sourceDistinctValuesSampled,
                matchedDistinctSourceValues, missingDistinctSourceValues, targetDistinctValuesSampled,
                false, false, true);
    }
}
