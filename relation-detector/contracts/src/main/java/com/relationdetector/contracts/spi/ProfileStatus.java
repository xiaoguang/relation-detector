package com.relationdetector.contracts.spi;

/**
 *
 * Outcome category for one bounded live data-profile query.
 */
public enum ProfileStatus {
    SUCCESS,
    NO_EVIDENCE,
    SKIPPED_INVALID_ENDPOINT,
    PERMISSION_DENIED,
    TIMEOUT,
    QUERY_FAILED
}
