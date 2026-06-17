package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.StructuredParseResult;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Verifies that PostgreSQL owns PostgreSQL-flavored ANTLR parser selection.
 */
class PostgresAntlrParserSelectionTest {
    @Test
    void adaptorExposesPostgresAntlrSqlAndDdlParsers() {
        PostgresDatabaseAdaptor adaptor = new PostgresDatabaseAdaptor();

        assertEquals(PostgresAntlrSqlParser.class, adaptor.structuredSqlParser().orElseThrow().getClass());
        assertEquals(PostgresAntlrDdlParser.class, adaptor.structuredDdlParser().orElseThrow().getClass());
    }

    @Test
    void postgresAntlrSqlParserAcceptsDoubleQuotedJoin() {
        StructuredParseResult result = new PostgresAntlrSqlParser().parseSql(new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.\"user_id\" = u.\"id\"",
                StatementSourceType.PLAIN_SQL,
                "postgres.sql",
                1,
                1,
                java.util.Map.of()), null);

        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.attributes().get("table"))
                        && "o".equals(event.attributes().get("alias"))));
    }
}
