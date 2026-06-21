package com.relationdetector.core.ddl;

import java.util.regex.Pattern;

/**
 * MySQL token-event DDL 方言 visitor。
 *
 * <p>CN: 公共 DDL visitor 只保留保守跨库 DDL；MySQL-only index 拼写、inline
 * {@code KEY}/{@code INDEX}、visibility flag 和 index type 位置放在这里，避免污染
 * PostgreSQL DDL。
 *
 * <p>EN: MySQL-specific DDL event extractor for the token-event DDL path. The
 * shared visitor stays conservative; MySQL-only index spelling, inline
 * KEY/INDEX definitions, visibility flags, and index type placement live here
 * so PostgreSQL DDL does not accidentally accept them.
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
