package com.relationdetector.mysql.fullgrammer;

/**
 * Normalizes MySQL client-side script delimiters before full-grammer parsing.
 *
 * <p>MySQL routine files commonly use client commands such as {@code DELIMITER
 * //} and terminate objects with {@code END//}. Those delimiter commands are
 * not part of the SQL statement sent to the server, so the full grammar should
 * not treat the trailing slash characters as SQL tokens.</p>
 */
public final class MySqlFullGrammerSqlNormalizer {
    private MySqlFullGrammerSqlNormalizer() {
    }

    public static String normalizeClientDelimiters(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        String withoutDelimiterCommands = sql.replaceAll("(?im)^\\s*DELIMITER\\s+\\S+\\s*$\\R?", "");
        return withoutDelimiterCommands
                .replaceAll("(?im)(\\bEND)\\s*//\\s*$", "$1;")
                .replaceAll("(?im)(\\bEND)\\s*\\$\\$\\s*$", "$1;")
                .replaceAll("(?i)(\\bEND)\\s*\\z", "$1;");
    }
}
