package com.relationdetector.postgres;

import com.relationdetector.core.AntlrStructuredSqlParser;
import com.relationdetector.core.SqlDialect;

/**
 * PostgreSQL dialect selection for the ANTLR structured SQL parser.
 *
 * <p>Keeping this class in the adaptor prevents core from guessing PostgreSQL
 * syntax details. Future support for PostgreSQL grammar transforms, dollar
 * quoted function bodies, and server-version-specific behavior belongs here.
 */
public final class PostgresAntlrSqlParser extends AntlrStructuredSqlParser {
    public PostgresAntlrSqlParser() {
        super(SqlDialect.POSTGRES);
    }
}
