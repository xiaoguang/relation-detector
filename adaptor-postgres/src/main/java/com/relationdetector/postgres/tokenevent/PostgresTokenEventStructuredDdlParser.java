package com.relationdetector.postgres.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

/**
 * PostgreSQL DDL token-event entry point.
 *
 * <p>PostgreSQL-only DDL options such as {@code ONLY}, {@code INCLUDE},
 * partial indexes, opclass, and storage parameters remain owned by the
 * PostgreSQL DDL event visitor selected underneath this token-event parser boundary.
 */
public final class PostgresTokenEventStructuredDdlParser extends TokenEventStructuredDdlParser {
    public PostgresTokenEventStructuredDdlParser() {
        super(SqlDialect.POSTGRES);
    }
}
