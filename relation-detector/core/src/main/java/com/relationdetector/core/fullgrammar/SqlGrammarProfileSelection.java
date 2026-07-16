package com.relationdetector.core.fullgrammar;

/**
 *
 * Result of selecting a versioned SQL grammar profile.
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
