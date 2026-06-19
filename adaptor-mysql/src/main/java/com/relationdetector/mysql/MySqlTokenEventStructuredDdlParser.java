package com.relationdetector.mysql;

import com.relationdetector.core.SqlDialect;
import com.relationdetector.core.TokenEventStructuredDdlParser;

/**
 * MySQL DDL Token/Event entry point.
 *
 * <p>The parser keeps MySQL DDL semantics inside the MySQL DDL event visitor
 * selected by {@link TokenEventStructuredDdlParser}. Adaptors expose this
 * class so SQL and DDL both have explicit Token/Event parser objects.
 */
public final class MySqlTokenEventStructuredDdlParser extends TokenEventStructuredDdlParser {
    public MySqlTokenEventStructuredDdlParser() {
        super(SqlDialect.MYSQL);
    }
}
