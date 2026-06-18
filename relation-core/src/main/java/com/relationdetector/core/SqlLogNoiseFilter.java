package com.relationdetector.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.DatabaseType;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Filters SQL log statements that are metadata noise rather than business SQL.
 *
 * <p>This class is intentionally separate from metadata collectors. Collectors
 * read authoritative catalog facts; this filter handles SQL text that appeared
 * in native logs because tools or drivers queried catalog tables.
 */
public final class SqlLogNoiseFilter {
    private static final Pattern ROWSET_REFERENCE = Pattern.compile(
            "(?is)\\b(?:from|join|update|into)\\s+([`\"\\w]+(?:\\s*\\.\\s*[`\"\\w]+){0,2})");
    private static final Pattern DELETE_USING_REFERENCE = Pattern.compile(
            "(?is)\\bdelete\\s+from\\s+[`\"\\w]+(?:\\s+(?:as\\s+)?[`\"\\w]+)?\\s+using\\s+([`\"\\w]+(?:\\s*\\.\\s*[`\"\\w]+){0,2})");
    private static final Pattern MERGE_USING_REFERENCE = Pattern.compile(
            "(?is)\\bmerge\\s+into\\s+[`\"\\w.]+(?:\\s+(?:as\\s+)?[`\"\\w]+)?\\s+using\\s+([`\"\\w]+(?:\\s*\\.\\s*[`\"\\w]+){0,2})");
    private static final List<String> DEFAULT_METADATA_MARKERS = List.of(
            "ApplicationName=DBeaver",
            "DatabaseMetaData");

    private SqlLogNoiseFilter() {
    }

    public static boolean shouldSkip(ScanConfig config, SqlStatementRecord statement) {
        if (statement.sourceType() != StatementSourceType.NATIVE_LOG) {
            return false;
        }
        if (config == null || !config.logsFilterSystemQueries) {
            return false;
        }
        String sql = statement.sql();
        String lower = sql.toLowerCase(Locale.ROOT);
        for (String marker : effectiveMetadataMarkers(config)) {
            if (!marker.isBlank() && lower.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        List<TableRef> rowsets = extractRowsetReferences(sql);
        if (rowsets.isEmpty()) {
            return false;
        }
        Set<String> systemSchemas = effectiveSystemSchemas(config);
        return rowsets.stream().allMatch(rowset -> isSystemRowset(rowset, systemSchemas));
    }

    static Set<String> effectiveSystemSchemas(ScanConfig config) {
        if (config != null && !config.logSystemSchemas.isEmpty()) {
            return normalizeSet(config.logSystemSchemas);
        }
        return defaultSystemSchemas(config == null ? null : config.databaseType);
    }

    static Set<String> defaultSystemSchemas(DatabaseType databaseType) {
        if (databaseType == null) {
            return normalizeSet(List.of("information_schema", "performance_schema", "mysql", "sys", "pg_catalog", "pg_toast"));
        }
        return switch (databaseType) {
            case MYSQL -> normalizeSet(List.of("information_schema", "performance_schema", "mysql", "sys"));
            case POSTGRESQL -> normalizeSet(List.of("pg_catalog", "information_schema", "pg_toast"));
            case SQLSERVER -> normalizeSet(List.of("sys", "information_schema"));
            case ORACLE -> normalizeSet(List.of("sys", "system"));
        };
    }

    static Set<String> defaultSystemSchemasForDialect(String dialect) {
        if (dialect == null) {
            return defaultSystemSchemas(null);
        }
        return switch (dialect.toUpperCase(Locale.ROOT)) {
            case "MYSQL" -> defaultSystemSchemas(DatabaseType.MYSQL);
            case "POSTGRESQL", "POSTGRES" -> defaultSystemSchemas(DatabaseType.POSTGRESQL);
            default -> defaultSystemSchemas(null);
        };
    }

    static boolean isSystemRowset(String qualifiedName, Set<String> systemSchemas) {
        return isSystemRowset(TableRef.parse(qualifiedName), systemSchemas);
    }

    static boolean isTruncatedToken(String sql, String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return true;
        }
        String clean = cleanIdentifier(tableName);
        if (clean.contains("...") || clean.contains("…")) {
            return true;
        }
        return Pattern.compile("(?i)\\b" + Pattern.quote(clean) + "\\s*(?:\\.\\.\\.|…)").matcher(sql).find();
    }

    static boolean isValidIdentifierToken(String identifier) {
        String clean = cleanIdentifier(identifier);
        return !clean.isBlank() && clean.matches("[A-Za-z_][A-Za-z0-9_$]*");
    }

    static List<TableRef> extractRowsetReferences(String sql) {
        List<TableRef> refs = new ArrayList<>();
        collect(sql, ROWSET_REFERENCE, refs);
        collect(sql, DELETE_USING_REFERENCE, refs);
        collect(sql, MERGE_USING_REFERENCE, refs);
        return refs;
    }

    @SuppressWarnings("unchecked")
    static Set<String> systemSchemasFromStatementOrDialect(SqlStatementRecord statement, String dialect) {
        Object configured = statement.attributes().get("logSystemSchemas");
        if (configured instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object value : iterable) {
                values.add(String.valueOf(value));
            }
            if (!values.isEmpty()) {
                return normalizeSet(values);
            }
        }
        if (configured instanceof String value && !value.isBlank()) {
            return normalizeSet(List.of(value.split(",")));
        }
        Object mapValue = statement.attributes().get("systemSchemas");
        if (mapValue instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object value : iterable) {
                values.add(String.valueOf(value));
            }
            if (!values.isEmpty()) {
                return normalizeSet(values);
            }
        }
        if (statement.attributes() instanceof Map<?, ?>) {
            return defaultSystemSchemasForDialect(dialect);
        }
        return defaultSystemSchemasForDialect(dialect);
    }

    private static List<String> effectiveMetadataMarkers(ScanConfig config) {
        return config.logMetadataQueryMarkers.isEmpty() ? DEFAULT_METADATA_MARKERS : config.logMetadataQueryMarkers;
    }

    private static boolean isSystemRowset(TableRef rowset, Set<String> systemSchemas) {
        return rowset.schema() != null && systemSchemas.contains(rowset.schema().toLowerCase(Locale.ROOT));
    }

    private static void collect(String sql, Pattern pattern, List<TableRef> refs) {
        Matcher matcher = pattern.matcher(sql);
        while (matcher.find()) {
            refs.add(TableRef.parse(matcher.group(1)));
        }
    }

    private static Set<String> normalizeSet(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String clean = cleanIdentifier(value).trim();
            if (!clean.isBlank()) {
                normalized.add(clean.toLowerCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private static String cleanIdentifier(String value) {
        String clean = value == null ? "" : value.trim();
        if ((clean.startsWith("`") && clean.endsWith("`")) || (clean.startsWith("\"") && clean.endsWith("\""))) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean;
    }

    record TableRef(String schema, String table) {
        static TableRef parse(String qualifiedName) {
            String[] parts = qualifiedName.replace("`", "").replace("\"", "").replaceAll("\\s+", "").split("\\.");
            if (parts.length >= 2) {
                return new TableRef(parts[parts.length - 2], parts[parts.length - 1]);
            }
            return new TableRef(null, parts.length == 0 ? "" : parts[0]);
        }
    }
}
