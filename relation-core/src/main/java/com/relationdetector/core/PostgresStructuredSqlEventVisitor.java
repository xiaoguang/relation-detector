package com.relationdetector.core;

import java.util.Set;

import com.relationdetector.core.antlr.postgres.PostgresRelationSqlLexer;

/**
 * PostgreSQL structural event visitor for ANTLR shadow mode.
 *
 * <p>It treats double-quoted PostgreSQL identifiers as identifiers and leaves
 * MySQL backticks alone. That prevents a PostgreSQL parser from accidentally
 * accepting MySQL-only quoted table references during shadow comparison.
 */
public final class PostgresStructuredSqlEventVisitor extends StructuredSqlEventVisitor {
    public PostgresStructuredSqlEventVisitor() {
        super("PostgresStructuredSqlEventVisitor",
                Set.of(PostgresRelationSqlLexer.IDENTIFIER, PostgresRelationSqlLexer.QUOTED_IDENTIFIER));
    }

    @Override
    protected String cleanIdentifier(String value) {
        if (value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
