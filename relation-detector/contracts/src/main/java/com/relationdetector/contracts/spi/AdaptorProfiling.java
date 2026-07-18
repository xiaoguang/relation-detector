package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;

/**
 * CN: 把 adaptor 的 live data profiler 与 evidence weight adjuster 组装为 SPI v6 profiling 能力组。
 * EN: Groups an adaptor's live data profiler and evidence weight adjuster as the SPI v6 profiling capability.
 */
public record AdaptorProfiling(
        Optional<DataProfiler> dataProfiler,
        EvidenceWeightAdjuster evidenceWeightAdjuster
) {
}
