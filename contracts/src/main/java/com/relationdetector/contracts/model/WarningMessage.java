package com.relationdetector.contracts.model;

import java.util.Map;

import com.relationdetector.contracts.Enums.WarningSeverity;
import com.relationdetector.contracts.Enums.WarningType;

/**
 * 扫描过程中的非致命问题。
 *
 * <p>CN: warning 会进入 ScanResult，帮助用户理解 parser fallback、权限问题、日志拆分失败等情况。
 *
 * <p>EN: Non-fatal issue collected during a scan. Warnings enter ScanResult so
 * users can understand parser fallback, permission issues, log extraction
 * failures, and similar recoverable problems.
 */
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
