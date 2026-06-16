package com.relationdetector.api;

import com.relationdetector.api.Enums.WarningSeverity;
import com.relationdetector.api.Enums.WarningType;

/** Non-fatal issue collected during scan. */
public record WarningMessage(
        WarningType type,
        WarningSeverity severity,
        String code,
        String message,
        String source,
        long line
) {
    public static WarningMessage warn(WarningType type, String code, String message, String source, long line) {
        return new WarningMessage(type, WarningSeverity.WARN, code, message, source, line);
    }
}
