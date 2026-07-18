package com.relationdetector.core.scan;

/**
 * CN: 承载 parser mode、grammar profile 与已配置/发现 database version 的不可变快照。
 * EN: Carries an immutable snapshot of parser mode, grammar profile, and configured or discovered database version.
 */
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
