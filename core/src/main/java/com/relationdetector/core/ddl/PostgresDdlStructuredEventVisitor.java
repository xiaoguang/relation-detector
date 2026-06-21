package com.relationdetector.core.ddl;

import java.util.regex.Pattern;

/**
 * PostgreSQL token-event DDL 方言 visitor。
 *
 * <p>CN: PostgreSQL 的 {@code ONLY}、{@code CONCURRENTLY}、{@code INCLUDE}、
 * partial-index {@code WHERE}、opclass/collation 等 DDL modifier 对通用 scanner
 * 很像普通 identifier，因此隔离在这里，避免 MySQL DDL 继承 PostgreSQL-only grammar。
 *
 * <p>EN: PostgreSQL-specific DDL event extractor for the token-event DDL path.
 * PostgreSQL DDL modifiers such as ONLY, CONCURRENTLY, INCLUDE, partial-index
 * WHERE, and opclass/collation tokens are isolated here so MySQL DDL parsing
 * does not inherit PostgreSQL-only grammar by accident.
 */
public final class PostgresDdlStructuredEventVisitor extends DdlStructuredEventVisitor {
    private static final Pattern POSTGRES_ALTER_TABLE = Pattern.compile(
            "(?is)\\balter\\s+table\\s+(?:if\\s+exists\\s+)?(?:only\\s+)?("
                    + IDENTIFIER + ")\\s+(.+)");
    private static final Pattern POSTGRES_CREATE_INDEX = Pattern.compile(
            "(?is)\\bcreate\\s+(unique\\s+)?index\\s+"
                    + "(?:concurrently\\s+)?(?:if\\s+not\\s+exists\\s+)?"
                    + INDEX_NAME
                    + "\\s+on\\s+(?:only\\s+)?("
                    + IDENTIFIER + ")(?:\\s+using\\s+\\w+)?\\s*\\((.*?)\\)\\s*"
                    + "(?:include\\s*\\([^)]*\\)\\s*)?"
                    + "(?:with\\s*\\([^)]*\\)\\s*)?"
                    + "(?:tablespace\\s+\\w+\\s*)?"
                    + "(where\\b.*)?$");

    @Override
    protected Pattern alterTablePattern() {
        return POSTGRES_ALTER_TABLE;
    }

    @Override
    protected Pattern createIndexPattern() {
        return POSTGRES_CREATE_INDEX;
    }
}
