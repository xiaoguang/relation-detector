package com.relationdetector.contracts.spi;

import java.util.Optional;

import com.relationdetector.contracts.spi.Collectors.DataProfiler;
import com.relationdetector.contracts.spi.Collectors.EvidenceWeightAdjuster;

/**
 *
 * Grouped profiling and evidence weighting capabilities exposed by an adaptor.
 */
public record AdaptorProfiling(
        Optional<DataProfiler> dataProfiler,
        EvidenceWeightAdjuster evidenceWeightAdjuster
) {
}
