package com.relationdetector.core;

import java.util.Locale;

/**
 * Runtime SQL relation parser selection.
 *
 * <p>Design mapping: SQL Parser Primary 切换设计计划. The mode is intentionally
 * core-owned, not adaptor-owned: adaptors expose parser capabilities, while the
 * scan engine decides whether a run should use the legacy parser only, shadow
 * ANTLR for comparison, or attempt ANTLR as primary with a safe fallback.
 */
public enum SqlParserMode {
    /** Run only SimpleSqlRelationParser behavior. No ANTLR diagnostics. */
    SIMPLE,
    /** Return Simple output, but also run ANTLR extraction and compare. */
    ANTLR_SHADOW,
    /** Return ANTLR output when parity is safe; fallback is controlled by config. */
    ANTLR_PRIMARY;

    public static SqlParserMode fromConfig(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return SqlParserMode.valueOf(normalized);
    }
}
