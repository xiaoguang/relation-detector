package com.relationdetector.contracts.spi;

import com.relationdetector.contracts.model.RelationshipCandidate;

/**
 * 单个 relationship 候选的数据画像请求。
 *
 * <p>CN: core 选择候选后把采样行数和超时配置交给 adaptor profiler。
 *
 * <p>EN: Data-profiling request for one relationship candidate. Core selects
 * candidates and passes sampling/timeout configuration to the adaptor profiler.
 */
public record ProfileRequest(
        RelationshipCandidate candidate,
        int sampleRows,
        int timeoutSeconds,
        DataProfileOptions options
) {
    public ProfileRequest(RelationshipCandidate candidate, int sampleRows, int timeoutSeconds) {
        this(candidate, sampleRows, timeoutSeconds,
                DataProfileOptions.defaults(sampleRows, timeoutSeconds, 1_000));
    }

    public ProfileRequest(RelationshipCandidate candidate, DataProfileOptions options) {
        this(candidate,
                options == null ? DataProfileOptions.defaults().sampleRows() : options.sampleRows(),
                options == null ? DataProfileOptions.defaults().timeoutSeconds() : options.timeoutSeconds(),
                options == null ? DataProfileOptions.defaults() : options);
    }
}
