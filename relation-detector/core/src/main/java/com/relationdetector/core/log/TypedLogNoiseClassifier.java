package com.relationdetector.core.log;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.relationdetector.contracts.Enums.DatabaseType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.RowsetEvent;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.core.scan.ScanConfig;

/** Classifies native-log catalog noise from typed rowset events after parsing. */
public final class TypedLogNoiseClassifier {
    private TypedLogNoiseClassifier() {
    }

    public static boolean shouldSkip(
            ScanConfig config,
            SqlStatementRecord statement,
            StructuredParseResult structured
    ) {
        if (statement.sourceType() != StatementSourceType.NATIVE_LOG
                || config == null
                || !config.logsFilterSystemQueries
                || structured == null) {
            return false;
        }

        List<String> rowsets = structured.events().stream()
                .filter(RowsetEvent.class::isInstance)
                .map(RowsetEvent.class::cast)
                .filter(TypedLogNoiseClassifier::isPhysicalReference)
                .map(TypedLogNoiseClassifier::qualifiedName)
                .filter(value -> !value.isBlank())
                .toList();
        if (!rowsets.isEmpty()) {
            Set<String> systemSchemas = effectiveSystemSchemas(config);
            return rowsets.stream().allMatch(rowset -> systemSchemas.contains(schemaOf(rowset)));
        }

        // Explicit markers are operational input filters, not fact inference.
        // They are considered only when parsing found no physical rowset.
        return config.logMetadataQueryMarkers.stream()
                .filter(marker -> marker != null && !marker.isBlank())
                .anyMatch(marker -> containsIgnoreCase(statement.sql(), marker));
    }

    public static Set<String> effectiveSystemSchemas(ScanConfig config) {
        if (config != null && !config.logSystemSchemas.isEmpty()) {
            return normalize(config.logSystemSchemas);
        }
        return defaults(config == null ? null : config.databaseType);
    }

    private static boolean isPhysicalReference(RowsetEvent event) {
        return event.type() == StructuredParseEventType.ROWSET_REFERENCE
                || event.type() == StructuredParseEventType.TABLE_REFERENCE;
    }

    private static String qualifiedName(RowsetEvent event) {
        return event.qualifiedTable().isBlank() ? event.table() : event.qualifiedTable();
    }

    private static String schemaOf(String qualifiedName) {
        List<String> parts = identifierParts(qualifiedName);
        return parts.size() < 2 ? "" : normalizeOne(parts.get(parts.size() - 2));
    }

    private static List<String> identifierParts(String value) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quotedBy = 0;
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (quotedBy == 0 && (ch == '[' || ch == '`' || ch == '"')) {
                quotedBy = ch == '[' ? ']' : ch;
                current.append(ch);
            } else if (quotedBy != 0 && ch == quotedBy) {
                quotedBy = 0;
                current.append(ch);
            } else if (quotedBy == 0 && ch == '.') {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static Set<String> normalize(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        values.stream().map(TypedLogNoiseClassifier::normalizeOne)
                .filter(value -> !value.isBlank())
                .forEach(normalized::add);
        return Set.copyOf(normalized);
    }

    private static Set<String> defaults(DatabaseType databaseType) {
        if (databaseType == null || databaseType == DatabaseType.COMMON) {
            return normalize(List.of("information_schema", "performance_schema", "mysql", "sys",
                    "pg_catalog", "pg_toast"));
        }
        return switch (databaseType) {
            case MYSQL -> normalize(List.of("information_schema", "performance_schema", "mysql", "sys"));
            case POSTGRESQL -> normalize(List.of("pg_catalog", "information_schema", "pg_toast"));
            case SQLSERVER -> normalize(List.of("sys", "information_schema"));
            case ORACLE -> normalize(List.of("sys", "system"));
            case COMMON -> throw new IllegalStateException("handled above");
        };
    }

    private static String normalizeOne(String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() >= 2
                && ((clean.startsWith("[") && clean.endsWith("]"))
                || (clean.startsWith("`") && clean.endsWith("`"))
                || (clean.startsWith("\"") && clean.endsWith("\"")))) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.toLowerCase(Locale.ROOT);
    }

    private static boolean containsIgnoreCase(String value, String marker) {
        return value.toLowerCase(Locale.ROOT).contains(marker.toLowerCase(Locale.ROOT));
    }
}
