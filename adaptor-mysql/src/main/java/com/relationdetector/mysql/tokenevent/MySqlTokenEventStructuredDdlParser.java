package com.relationdetector.mysql.tokenevent;

import com.relationdetector.core.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

/**
 * MySQL DDL token-event entry point.
 *
 * <p>The parser keeps MySQL DDL semantics inside the MySQL DDL event visitor
 * selected by {@link TokenEventStructuredDdlParser}. Adaptors expose this
 * class so SQL and DDL both have explicit token-event parser objects.
 */
public final class MySqlTokenEventStructuredDdlParser extends TokenEventStructuredDdlParser {
    public MySqlTokenEventStructuredDdlParser() {
        super(SqlDialect.MYSQL);
    }
}
