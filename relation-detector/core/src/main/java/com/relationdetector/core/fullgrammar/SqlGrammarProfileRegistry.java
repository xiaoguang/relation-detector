package com.relationdetector.core.fullgrammar;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.contracts.Enums.DatabaseType;

/**
 * full-grammar profile registry 与版本选择器。
 *
 * <p>CN: 这里的 regex 只解析配置/JDBC 返回的版本字符串，不解析 SQL/DDL 结构。
 * 选择规则是：显式 profile 优先，其次配置或 JDBC version；同 major 直接使用，高一
 * 个 major 可临时降级，超过范围则回退 token-event。
 *
 * <p>EN: Registry and selector for versioned full-grammar modules. The regex in
 * this class parses configuration/JDBC version strings only; it never parses
 * SQL/DDL structure. Selection prefers explicit profile, then configured/JDBC
 * version, with same-major match, one-major fallback, and token-event fallback
 * outside that range.
 */
public final class SqlGrammarProfileRegistry {
    private static final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?.*");

    private SqlGrammarProfileRegistry() {
    }

    public static SqlGrammarProfileSelection select(DatabaseType databaseType, String version) {
        return select(FullGrammarProfileRequest.builder()
                .databaseType(databaseType)
                .configuredVersion(version)
                .build());
    }

    public static SqlGrammarProfileSelection select(FullGrammarProfileRequest request) {
        return select(request, modules());
    }

    public static SqlGrammarProfileSelection select(
            FullGrammarProfileRequest request,
            Collection<FullGrammarDialectModule> modules
    ) {
        if (request == null || request.databaseType() == null) {
            return noProfile("", "UNKNOWN", "No database type available; using token-event parser");
        }

        if (!request.configuredProfile().isBlank()) {
            return selectConfiguredProfile(request.databaseType(), request.configuredProfile(), modules);
        }

        if (!request.configuredVersion().isBlank()) {
            return selectVersion(
                    request.databaseType(),
                    request.configuredVersion(),
                    request.configuredVersionSource(),
                    modules);
        }

        JdbcVersion jdbcVersion = jdbcVersion(request.jdbcConnection());
        if (jdbcVersion != null) {
            return selectVersion(request.databaseType(), jdbcVersion.version(), "JDBC", modules);
        }

        return noProfile("", "UNKNOWN", "No SQL grammar version available; using token-event parser");
    }

    public static List<SqlGrammarProfile> profiles() {
        return profiles(modules());
    }

    public static List<SqlGrammarProfile> profiles(Collection<FullGrammarDialectModule> modules) {
        return modules.stream().map(FullGrammarDialectModule::profile).toList();
    }

    static Optional<FullGrammarDialectModule> moduleFor(SqlGrammarProfile profile) {
        return moduleFor(profile, modules());
    }

    static Optional<FullGrammarDialectModule> moduleFor(
            SqlGrammarProfile profile,
            Collection<FullGrammarDialectModule> modules
    ) {
        if (profile == null) {
            return Optional.empty();
        }
        return modules.stream()
                .filter(module -> module.profile().id().equals(profile.id()))
                .findFirst();
    }

    private static SqlGrammarProfileSelection selectConfiguredProfile(
            DatabaseType databaseType,
            String configuredProfile,
            Collection<FullGrammarDialectModule> modules
    ) {
        String normalized = normalizeProfileId(databaseType, configuredProfile);
        Optional<FullGrammarDialectModule> module = modules.stream()
                .filter(candidate -> candidate.profile().databaseType() == databaseType)
                .filter(candidate -> candidate.profile().id().equals(normalized))
                .findFirst();
        if (module.isPresent()) {
            return new SqlGrammarProfileSelection(module.get().profile(), false, "", configuredProfile, "CONFIG");
        }
        return noProfile(configuredProfile, "CONFIG",
                "Configured SQL grammar profile '" + configuredProfile + "' is not registered; using token-event parser");
    }

    private static SqlGrammarProfileSelection selectVersion(
            DatabaseType databaseType,
            String version,
            String source,
            Collection<FullGrammarDialectModule> modules
    ) {
        Version requested = parseVersion(version);
        if (requested == null) {
            return noProfile(version, source, "Cannot parse SQL grammar version '" + version + "'; using token-event parser");
        }

        List<FullGrammarDialectModule> candidates = modules.stream()
                .filter(module -> module.profile().databaseType() == databaseType)
                .sorted(Comparator
                        .comparingInt((FullGrammarDialectModule module) -> module.profile().majorVersion())
                        .thenComparingInt(module -> module.profile().minorVersion()))
                .toList();
        if (candidates.isEmpty()) {
            return noProfile(version, source, "No SQL grammar profile registered for " + databaseType
                    + "; using token-event parser");
        }

        Optional<FullGrammarDialectModule> sameMajor = candidates.stream()
                .filter(module -> module.profile().majorVersion() == requested.major)
                .reduce((first, second) -> second);
        if (sameMajor.isPresent()) {
            return new SqlGrammarProfileSelection(sameMajor.get().profile(), false, "", version, source);
        }

        Optional<FullGrammarDialectModule> oneMajorBehind = candidates.stream()
                .filter(module -> module.profile().majorVersion() == requested.major - 1)
                .reduce((first, second) -> second);
        if (oneMajorBehind.isPresent()) {
            SqlGrammarProfile profile = oneMajorBehind.get().profile();
            String diagnostic = "Requested " + databaseType + " SQL grammar version '" + version
                    + "' is one major newer than registered profiles; using " + profile.id();
            return new SqlGrammarProfileSelection(profile, true, diagnostic, version, source);
        }

        return noProfile(version, source, "Requested " + databaseType + " SQL grammar version '" + version
                + "' is not within one major version of a registered profile; using token-event parser");
    }

    private static SqlGrammarProfileSelection noProfile(String requestedVersion, String source, String diagnostic) {
        return new SqlGrammarProfileSelection(null, true, diagnostic, requestedVersion, source);
    }

    private static String normalizeProfileId(DatabaseType databaseType, String configuredProfile) {
        String normalized = configuredProfile.trim().toLowerCase(Locale.ROOT)
                .replace('\\', '/')
                .replace('_', '.');
        if (normalized.contains("/")) {
            String[] parts = normalized.split("/");
            if (parts.length >= 2) {
                String version = parts[1].startsWith("v") ? parts[1].substring(1) : parts[1];
                return switch (databaseType) {
                    case MYSQL -> "mysql-" + version.replace('_', '.');
                    case POSTGRESQL -> "postgresql-" + version;
                    default -> normalized.replace('/', '-');
                };
            }
        }
        return normalized;
    }

    private static JdbcVersion jdbcVersion(Connection connection) {
        if (connection == null) {
            return null;
        }
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            int major = metaData.getDatabaseMajorVersion();
            int minor = metaData.getDatabaseMinorVersion();
            String version = major + "." + minor;
            return new JdbcVersion(version);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Version parseVersion(String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        Matcher matcher = VERSION.matcher(version.trim());
        if (!matcher.matches()) {
            return null;
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return new Version(major, minor);
    }

    private record Version(int major, int minor) {
    }

    private record JdbcVersion(String version) {
    }

    public static List<FullGrammarDialectModule> modules() {
        return ServiceLoader.load(FullGrammarDialectModule.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
    }
}
