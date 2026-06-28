package com.relationdetector.core.tokenevent;

import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.parse.StructuredSqlEvent;
import com.relationdetector.contracts.Enums.RelationType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;

/**
 * Guards the most important token-event extraction boundary.
 *
 * <p>These tests deliberately give {@link TokenEventRelationExtractor} structured
 * events that do not match the raw SQL text. Relationship extraction must stay
 * inside the token-event pipeline; empty events must not trigger raw SQL
 * rescan or any removed parser.
 */
class TokenEventRelationExtractorIndependenceTest {
    @Test
    void extractsRelationsFromStructuredEventsWhenRawSqlHasNoJoin() {
        SqlStatementRecord statement = record("SELECT 1");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "orders", "o", 1),
                table("JOIN", "users", "u", 1),
                equality("o", "user_id", "u", "id", 1)
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected event-derived relation, got: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.FK_LIKE, relation.relationType());
        assertEquals("orders.user_id", relation.source().displayName());
        assertEquals("users.id", relation.target().displayName());
        assertTrue(relation.evidence().get(0).detail().contains("token-event"));
    }

    @Test
    void emptyStructuredEventsDoNotRescanRawSql() {
        SqlStatementRecord statement = record("SELECT * FROM orders o JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(), () -> "Empty token-event events must not parse raw SQL: " + relations);
    }

    @Test
    void statementScopedLocalTemporaryTablesAreNotRelationshipEndpoints() {
        SqlStatementRecord statement = record(
                "INSERT INTO order_facts SELECT tr.customer_id FROM tmp_rollup tr JOIN customers c ON tr.customer_id = c.id",
                Map.of("localTempTables", List.of("tmp_rollup")));
        StructuredParseResult structured = structured(List.of(
                table("FROM", "tmp_rollup", "tr", 1),
                table("JOIN", "customers", "c", 1),
                equality("tr", "customer_id", "c", "id", 1)
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "Procedure-local temporary tables must not become relationship endpoints: " + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnMysqlOnlyStraightJoinCompatibility() {
        SqlStatementRecord statement = record("SELECT * FROM orders o STRAIGHT_JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "MySQL-only STRAIGHT_JOIN rowset extraction belongs in MySqlTokenEventSqlEventBuilder: "
                        + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnPostgresOnlyOnlyCompatibility() {
        SqlStatementRecord statement = record("SELECT * FROM ONLY orders o JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "PostgreSQL-only ONLY rowset extraction belongs in PostgresTokenEventSqlEventBuilder: "
                        + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnMysqlOnlyOdbcIndexHintJsonTableOrPartitionCompatibility() {
        List<String> mysqlOnlySql = List.of(
                "SELECT * FROM { OJ orders o LEFT OUTER JOIN users u ON o.user_id = u.id }",
                "SELECT * FROM orders o FORCE INDEX FOR JOIN (idx_orders_user) JOIN users u ON o.user_id = u.id",
                "SELECT * FROM orders PARTITION (p202501) o JOIN users u ON o.user_id = u.id",
                """
                SELECT *
                FROM JSON_TABLE(payload, '$[*]' COLUMNS (user_id BIGINT PATH '$.user_id')) jt
                JOIN users u ON jt.user_id = u.id
                """
        );

        for (String sql : mysqlOnlySql) {
            List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(record(sql), structured(List.of()));

            assertTrue(relations.isEmpty(),
                    () -> "MySQL-only rowset extraction belongs in MySqlTokenEventSqlEventBuilder. SQL: " + sql
                            + " Actual: " + relations);
        }
    }

    private SqlStatementRecord record(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "independence.sql", 1, 1, Map.of());
    }

    private SqlStatementRecord record(String sql, Map<String, Object> attributes) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "independence.sql", 1, 1, attributes);
    }

    private StructuredParseResult structured(List<StructuredSqlEvent> events) {
        return new StructuredParseResult("ANTLR_TOKEN_EVENT", "MYSQL", "independence.sql", events, List.of(), Map.of());
    }

    private StructuredSqlEvent table(String keyword, String table, String alias, long line) {
        return new StructuredSqlEvent(StructuredParseEventType.ROWSET_REFERENCE, "independence.sql", line,
                Map.of("keyword", keyword, "qualifiedTable", table, "table", table, "alias", alias));
    }

    private StructuredSqlEvent equality(String leftAlias, String leftColumn, String rightAlias, String rightColumn, long line) {
        return new StructuredSqlEvent(StructuredParseEventType.PREDICATE_EQUALITY, "independence.sql", line,
                Map.of("leftAlias", leftAlias, "leftColumn", leftColumn,
                        "rightAlias", rightAlias, "rightColumn", rightColumn));
    }
}
