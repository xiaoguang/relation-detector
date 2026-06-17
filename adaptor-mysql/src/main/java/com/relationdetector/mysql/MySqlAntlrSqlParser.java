package com.relationdetector.mysql;

import com.relationdetector.core.AntlrStructuredSqlParser;
import com.relationdetector.core.SqlDialect;

/**
 * MySQL dialect selection for the ANTLR structured SQL parser.
 *
 * <p>This class is intentionally tiny but important architecturally: MySQL owns
 * parser selection for MySQL SQL text, so future MySQL-only grammar generation,
 * version flags, and token normalization can be added here without changing
 * core or PostgreSQL.
 */
public final class MySqlAntlrSqlParser extends AntlrStructuredSqlParser {
    public MySqlAntlrSqlParser() {
        super(SqlDialect.MYSQL);
    }
}
