package com.relationdetector.postgres;

import com.relationdetector.core.SqlDialect;
import com.relationdetector.core.TokenEventStructuredDdlParser;

/**
 * PostgreSQL DDL Token/Event entry point.
 *
 * <p>PostgreSQL-only DDL options such as {@code ONLY}, {@code INCLUDE},
 * partial indexes, opclass, and storage parameters remain owned by the
 * PostgreSQL DDL event visitor selected underneath this Token/Event parser boundary.
 */
public final class PostgresTokenEventStructuredDdlParser extends TokenEventStructuredDdlParser {
    public PostgresTokenEventStructuredDdlParser() {
        super(SqlDialect.POSTGRES);
    }
}
