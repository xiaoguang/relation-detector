package com.relationdetector.postgres.routine;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.relationdetector.contracts.parse.SqlStatementRecord;

/** Structured routine-scope attributes shared by PostgreSQL parser modes. */
public final class PostgresRoutineAttributes {
    public static final String NON_COLUMN_IDENTIFIERS = "routineNonColumnIdentifiers";
    public static final String EMBEDDED_SQL = "postgresRoutineEmbeddedSql";

    private PostgresRoutineAttributes() {
    }

    public static Set<String> nonColumnIdentifiers(Map<String, Object> attributes) {
        Object value = attributes == null ? null : attributes.get(NON_COLUMN_IDENTIFIERS);
        if (!(value instanceof Collection<?> values)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object item : values) {
            String normalized = normalize(item);
            if (!normalized.isBlank()) {
                result.add(normalized);
            }
        }
        return Set.copyOf(result);
    }

    public static List<String> merge(Map<String, Object> attributes, Collection<String> additions) {
        Set<String> result = new LinkedHashSet<>(nonColumnIdentifiers(attributes));
        if (additions != null) {
            for (String item : additions) {
                String normalized = normalize(item);
                if (!normalized.isBlank()) {
                    result.add(normalized);
                }
            }
        }
        return List.copyOf(result);
    }

    public static SqlStatementRecord withNonColumnIdentifiers(
            SqlStatementRecord statement,
            Collection<String> additions
    ) {
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(statement.attributes());
        attributes.put(NON_COLUMN_IDENTIFIERS, merge(attributes, additions));
        return new SqlStatementRecord(statement.sql(), statement.sourceType(), statement.sourceName(),
                statement.startLine(), statement.endLine(), attributes);
    }

    public static boolean isRoutineSql(SqlStatementRecord statement) {
        if (Boolean.TRUE.equals(statement.attributes().get(EMBEDDED_SQL))) {
            return true;
        }
        return switch (statement.sourceType()) {
            case PROCEDURE, FUNCTION, TRIGGER -> true;
            default -> false;
        };
    }

    public static List<String> triggerPseudoIdentifiers(SqlStatementRecord statement) {
        return statement.sourceType() == com.relationdetector.contracts.Enums.StatementSourceType.TRIGGER
                ? List.of("new", "old") : List.of();
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).strip().toLowerCase(Locale.ROOT);
    }
}
