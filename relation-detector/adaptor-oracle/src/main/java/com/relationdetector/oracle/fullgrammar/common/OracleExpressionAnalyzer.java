package com.relationdetector.oracle.fullgrammar.common;

import java.util.Locale;

/**
 * Shared Oracle expression helpers for versioned typed visitors.
 */
public final class OracleExpressionAnalyzer {
    private OracleExpressionAnalyzer() {
    }

    public static String normalizeIdentifier(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text.trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            return cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }
}
