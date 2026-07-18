package com.relationdetector.contracts.spi;

/**
 * CN: 列举一次有界 live data-profile query 的可审计结果状态。
 * EN: Enumerates auditable outcome states for one bounded live data-profile query.
 */
public enum ProfileStatus {
    SUCCESS,
    NO_EVIDENCE,
    SKIPPED_INVALID_ENDPOINT,
    PERMISSION_DENIED,
    TIMEOUT,
    QUERY_FAILED
}
