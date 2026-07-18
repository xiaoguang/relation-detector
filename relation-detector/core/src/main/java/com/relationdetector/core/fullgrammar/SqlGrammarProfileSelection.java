package com.relationdetector.core.fullgrammar;

/**
 * CN: 承载 versioned SQL grammar profile 选择结果、fallback 标记和安全诊断上下文。
 * EN: Carries a versioned SQL grammar selection, fallback flag, and safe diagnostic context.
 */
public record SqlGrammarProfileSelection(
        SqlGrammarProfile profile,
        boolean usedFallback,
        String diagnostic,
        String requestedDatabaseVersion,
        String versionSource
) {
    public SqlGrammarProfileSelection {
        diagnostic = diagnostic == null ? "" : diagnostic;
        requestedDatabaseVersion = requestedDatabaseVersion == null ? "" : requestedDatabaseVersion;
        versionSource = versionSource == null || versionSource.isBlank() ? "UNKNOWN" : versionSource;
    }
}
