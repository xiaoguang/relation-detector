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
        int timeoutSeconds
) {
}
