package com.relationdetector.core.profile;

/**
 * CN: 承载一个 relationship candidate 的聚合、非敏感 live profiling metrics，不保留样本值或 SQL。
 * EN: Carries aggregate, non-sensitive live profiling metrics for one relationship candidate without sample values or SQL.
 */
public record DataProfileMetrics(
        String profileMode,
        long sourceNonNullRows,
        long sourceDistinctValues,
        long matchedDistinctSourceValues,
        long missingDistinctSourceValues,
        long targetDistinctValues,
        boolean queryTimedOut,
        boolean permissionDenied
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
                queryTimedOut, permissionDenied);
    }
}
