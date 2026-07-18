package com.relationdetector.core.scan;

import com.relationdetector.contracts.Enums.OutputFormat;

/**
 * CN: 承载不可变的输出格式、最终 confidence 过滤与 evidence/warning 展示开关。
 * EN: Carries immutable output format, final confidence filter, and evidence/warning presentation switches.
 */
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
