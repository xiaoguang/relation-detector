package com.relationdetector.core;

import java.util.regex.Pattern;

/**
 * MySQL-specific DDL event extractor for the Token/Event DDL path.
 *
 * <p>The shared {@link DdlStructuredEventVisitor} keeps only conservative
 * cross-dialect DDL. MySQL-only index spelling, inline {@code KEY}/{@code INDEX}
 * definitions, visibility flags, and index type placement live here so they do
 * not make PostgreSQL DDL look valid by accident.
 */
public final class MySqlDdlStructuredEventVisitor extends DdlStructuredEventVisitor {
    private static final Pattern MYSQL_CREATE_INDEX = Pattern.compile(
            "(?is)\\bcreate\\s+(unique\\s+)?index\\s+"
                    + "(?:if\\s+not\\s+exists\\s+)?"
                    + INDEX_NAME
                    + "\\s+(?:using\\s+\\w+\\s+)?on\\s+("
                    + IDENTIFIER + ")(?:\\s+using\\s+\\w+)?\\s*\\((.*?)\\)\\s*"
                    + "(?:\\s+(?:visible|invisible|comment\\s+'[^']*'|key_block_size\\s*=\\s*\\d+|algorithm\\s*=\\s*\\w+|lock\\s*=\\s*\\w+))*\\s*"
                    + "(where\\b.*)?$");

    @Override
    protected Pattern createIndexPattern() {
        return MYSQL_CREATE_INDEX;
    }

    @Override
    protected boolean acceptCreateIndexStatement(String statement) {
        String lower = statement == null ? "" : statement.toLowerCase(java.util.Locale.ROOT);
        return !lower.contains(" concurrently ")
                && !Pattern.compile("(?is)\\bon\\s+only\\b").matcher(lower).find()
                && !Pattern.compile("(?is)\\binclude\\s*\\(").matcher(lower).find();
    }

    @Override
    protected boolean isTableIndexDefinition(String lower) {
        return lower.startsWith("index ") || lower.startsWith("key ");
    }
}
