package com.relationdetector.core.scan;

import com.relationdetector.contracts.Enums.OutputFormat;

/** Immutable output and final confidence-filter configuration. */
public record OutputConfig(
        OutputFormat format,
        double minConfidence,
        boolean includeEvidence,
        boolean includeWarnings,
        boolean includeObservationCounts
) {
    public OutputConfig {
        format = format == null ? OutputFormat.JSON : format;
    }
}
