package com.relationdetector.postgres;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.api.Enums.StructuredParseEventType;

class PostgresTokenEventParserSelectionTest {
    @Test
    void postgresTokenEventParserUsesPostgresDialectBuilder() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM \"orders\" o JOIN \"users\" u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "postgres-token-event.sql",
                1,
                1,
                Map.of());

        var result = new PostgresTokenEventStructuredSqlParser().parseSql(statement, null);

        assertEquals("POSTGRES", result.dialect());
        assertEquals("PostgresTokenEventSqlEventBuilder", result.attributes().get("eventBuilder"));
        assertEquals(Boolean.TRUE, result.attributes().get("tokenEvent"));
        assertEquals(Boolean.TRUE, result.attributes().get("tokenEventPrimary"));
        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.attributes().get("table"))));
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
                        && "recent".equals(event.attributes().get("name"))));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.IGNORED_ROWSET
                        && ("ROWS".equals(event.attributes().get("name"))
                        || "UNNEST".equals(event.attributes().get("name")))));
        assertFalse(result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE)
                .anyMatch(event ->
                "system".equals(event.attributes().get("table"))
                        || "generate_series".equals(event.attributes().get("table"))
                        || "UNNEST".equals(event.attributes().get("table"))
                        || "u".equals(event.attributes().get("table"))));
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
                "p202501".equals(event.attributes().get("table"))
                        || "FORCE".equals(event.attributes().get("table"))
                        || "idx_user".equals(event.attributes().get("table"))
                        || "jt".equals(event.attributes().get("table"))));
    }
}
