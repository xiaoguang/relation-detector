package com.relationdetector.postgres;

import com.relationdetector.core.AntlrStructuredDdlParser;
import com.relationdetector.core.SqlDialect;

/**
 * PostgreSQL dialect selection for structured DDL shadow parsing.
 *
 * <p>Relationship-producing DDL parsing remains in {@link PostgresDdlParser};
 * this class opens the ANTLR diagnostics path for PostgreSQL DDL without
 * changing current relationship output.
 */
public final class PostgresAntlrDdlParser extends AntlrStructuredDdlParser {
    public PostgresAntlrDdlParser() {
        super(SqlDialect.POSTGRES);
    }
}
