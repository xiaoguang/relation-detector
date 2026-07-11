package com.relationdetector.mysql.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

class MySqlTokenEventParserSelectionTest {
    @Test
    void mysqlTokenEventParserUsesMySqlTypedVisitorOnly() {
        SqlStatementRecord statement = new SqlStatementRecord(
                "SELECT * FROM `orders` o JOIN `users` u ON o.user_id = u.id",
                StatementSourceType.PLAIN_SQL,
                "mysql-token-event.sql",
                1,
                1,
                Map.of());

        var result = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);

        assertEquals("MYSQL", result.dialect());
        assertEquals("MySqlTokenEventParseTreeVisitor", result.attributes().get("eventBuilder"));
        assertFalse(result.attributes().containsKey("legacySupplementBuilder"));
        assertEquals(Boolean.TRUE, result.attributes().get("tokenEvent"));
        assertEquals(Boolean.TRUE, result.attributes().get("tokenEventPrimary"));
        assertTrue(result.events().stream().anyMatch(event ->
                "orders".equals(event.table())));
    }

    @Test
    void mysqlTokenEventHandlesMysqlOnlyRowsetDecoratorsWithoutLeakingFakeTables() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM orders PARTITION (p202501) o FORCE INDEX FOR JOIN (idx_user)
                STRAIGHT_JOIN users u ON o.user_id = u.id
                JOIN JSON_TABLE(o.payload, '$[*]' COLUMNS (sku varchar(20) PATH '$.sku')) jt ON true
                """, StatementSourceType.PLAIN_SQL, "mysql-token-event.sql", 1, 1, Map.of());

        var result = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);

        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.ROWSET_REFERENCE
                        && "orders".equals(event.table())
                        && "o".equals(event.alias())));
        assertTrue(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.IGNORED_ROWSET
                        && "JSON_TABLE".equalsIgnoreCase(event.name())));
        var tables = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.ROWSET_REFERENCE)
                .map(event -> event.table())
                .filter(Objects::nonNull)
                .toList();
        assertFalse(tables.stream().anyMatch(table ->
                "p202501".equals(table)
                        || "FORCE".equals(table)
                        || "idx_user".equals(table)
                        || "jt".equals(table)), () -> "tables=" + tables);
    }

    @Test
    void mysqlTokenEventDoesNotTreatPostgresOnlyTokensAsPhysicalTables() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                SELECT *
                FROM ONLY orders TABLESAMPLE system (10) o
                JOIN ROWS FROM (generate_series(1, 3)) AS g(n) ON true
                JOIN UNNEST(ARRAY[1,2]) WITH ORDINALITY AS u(id, ord) ON true
                """, StatementSourceType.PLAIN_SQL, "mysql-negative-token-event.sql", 1, 1, Map.of());

        var result = new MySqlTokenEventStructuredSqlParser().parseSql(statement, null);

        assertFalse(result.events().stream().anyMatch(event ->
                "system".equals(event.table())
                        || "generate_series".equals(event.table())
                        || "UNNEST".equals(event.table())
                        || "u".equals(event.table())));
    }
}
