package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.model.RelationshipCandidate;

/**
 * CN: adaptor profiler 接收的单个候选及其 live JDBC 运行参数。
 *
 * <p>EN: One relationship candidate and its live JDBC runtime options passed
 * to an adaptor profiler. Core owns candidate selection and bounded execution.
 */
public record ProfileRequest(
        RelationshipCandidate candidate,
        DataProfileOptions options
) {
    public ProfileRequest {
        if (candidate == null) {
            throw new IllegalArgumentException("profile candidate is required");
        }
        options = options == null ? DataProfileOptions.defaults() : options;
    }
}
