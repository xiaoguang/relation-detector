package com.relationdetector.core.scan;

/** Immutable parser selection and discovered database-version configuration. */
public record ParserConfig(
        String mode,
        String grammarProfile,
        String databaseVersion,
        String databaseVersionSource
) {
    public ParserConfig {
        mode = blankTo(mode, "auto");
        grammarProfile = blankTo(grammarProfile, "");
        databaseVersion = blankTo(databaseVersion, "");
        databaseVersionSource = blankTo(databaseVersionSource, "UNKNOWN");
    }

    public ParserConfig withJdbcVersion(String version) {
        if (!databaseVersion.isBlank() || version == null || version.isBlank()) {
            return this;
        }
        return new ParserConfig(mode, grammarProfile, version, "JDBC");
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
