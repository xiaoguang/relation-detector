package com.relationdetector.postgres.tokenevent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

class PostgresTokenEventParserSelectionTest {
    @Test
    void postgresTokenEventParserUsesPostgresTypedVisitorOnly() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "postgres-token-event.sql",
                1,
                1,
                Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);

        assertEquals("POSTGRES", result.dialect());
        assertEquals("PostgresTokenEventParseTreeVisitor", result.attributes().get("eventBuilder"));
        assertFalse(result.attributes().containsKey("legacySupplementBuilder"));
        assertEquals(Boolean.TRUE, result.attributes().get("tokenEvent"));
        assertEquals(Boolean.TRUE, result.attributes().get("tokenEventPrimary"));
        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.table())));
    }

    @Test
    void postgresTokenEventHandlesPostgresOnlyRowsetDecoratorsWithoutLeakingFakeTables() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                WITH recent AS MATERIALIZED (
                    SELECT * FROM ONLY orders TABLESAMPLE system (10) o
                )
                SELECT *
                FROM ROWS FROM (generate_series(1, 3)) AS g(n)
                JOIN LATERAL UNNEST(ARRAY[1,2]) WITH ORDINALITY AS u(id, ord) ON true
                JOIN recent r ON r.user_id = u.id
                """, StatementSourceType.PLAIN_SQL, "postgres-token-event.sql", 1, 1, Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);

        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.CTE_DECLARATION
                        && "recent".equals(event.name())));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.IGNORED_ROWSET
                        && ("ROWS".equals(event.name())
                        || "UNNEST".equals(event.name()))));
        assertFalse(result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE)
                .anyMatch(event ->
                "system".equals(event.table())
                        || "generate_series".equals(event.table())
                        || "UNNEST".equals(event.table())
                        || "u".equals(event.table())));
    }

    @Test
    void postgresTokenEventDoesNotTreatMysqlOnlyTokensAsPhysicalTables() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM orders PARTITION (p202501) o FORCE INDEX (idx_user)
                JOIN JSON_TABLE(o.payload, '$[*]' COLUMNS (sku varchar(20) PATH '$.sku')) jt ON true
                """, StatementSourceType.PLAIN_SQL, "postgres-negative-token-event.sql", 1, 1, Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);

        assertFalse(result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE)
                .anyMatch(event ->
                "p202501".equals(event.table())
                        || "FORCE".equals(event.table())
                        || "idx_user".equals(event.table())
                        || "jt".equals(event.table())));
    }
}
