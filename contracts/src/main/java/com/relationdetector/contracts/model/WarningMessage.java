package com.relationdetector.contracts.model;

import java.util.Map;

import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.Enums.WarningType;

/** Non-fatal issue collected during scan. */
public record WarningMessage(
        WarningType type,
        WarningSeverity severity,
        String code,
        String message,
        String source,
        long line,
        Map<String, Object> attributes
) {
    public WarningMessage {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public WarningMessage(
            WarningType type,
            WarningSeverity severity,
            String code,
            String message,
            String source,
            long line
    ) {
        this(type, severity, code, message, source, line, Map.of());
    }

    public static WarningMessage warn(WarningType type, String code, String message, String source, long line) {
        return warn(type, code, message, source, line, Map.of());
    }

    public static WarningMessage warn(
            WarningType type,
            String code,
            String message,
            String source,
            long line,
            Map<String, Object> attributes
    ) {
        return new WarningMessage(type, WarningSeverity.WARN, code, message, source, line, attributes);
    }
}
