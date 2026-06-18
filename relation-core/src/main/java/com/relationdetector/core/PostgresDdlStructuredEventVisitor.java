package com.relationdetector.core;

import java.util.regex.Pattern;

/**
 * PostgreSQL-specific DDL event extractor for the ANTLR DDL path.
 *
 * <p>PostgreSQL has DDL modifiers that look like ordinary identifiers to a
 * generic scanner, such as {@code ONLY}, {@code CONCURRENTLY},
 * {@code INCLUDE}, partial-index {@code WHERE}, and opclass/collation tokens.
 * Keeping those forms here prevents MySQL DDL parsing from inheriting
 * PostgreSQL-only grammar by accident.
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
                    + "(?:include\\s*\\([^)]*\\)\\s*)?(where\\b.*)?$");

    @Override
    protected Pattern alterTablePattern() {
        return POSTGRES_ALTER_TABLE;
    }

    @Override
    protected Pattern createIndexPattern() {
        return POSTGRES_CREATE_INDEX;
    }
}
