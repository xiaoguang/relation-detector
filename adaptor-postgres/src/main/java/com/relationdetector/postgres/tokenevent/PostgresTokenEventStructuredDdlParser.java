package com.relationdetector.postgres.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.tokenevent.TokenEventStructuredDdlParser;

/**
 * PostgreSQL DDL token-event 入口。
 *
 * <p>CN: {@code ONLY}、{@code INCLUDE}、partial indexes、opclass、storage
 * parameters 等 PostgreSQL-only DDL option 由底层 PostgreSQL DDL event visitor 承接。
 *
 * <p>EN: PostgreSQL DDL token-event entry point. PostgreSQL-only DDL options
 * such as ONLY, INCLUDE, partial indexes, opclass, and storage parameters remain
 * owned by the PostgreSQL DDL event visitor below this parser boundary.
 */
public final class PostgresTokenEventStructuredDdlParser extends TokenEventStructuredDdlParser {
    public PostgresTokenEventStructuredDdlParser() {
        super(SqlDialect.POSTGRES);
    }
}
