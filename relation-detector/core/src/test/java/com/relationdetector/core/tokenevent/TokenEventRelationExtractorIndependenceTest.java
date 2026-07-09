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
import com.relationdetector.contracts.Enums.EvidenceType;
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
        assertEquals(RelationType.CO_OCCURRENCE, relation.relationType());
        assertEquals("orders.user_id", relation.source().displayName());
        assertEquals("users.id", relation.target().displayName());
        assertEquals(EvidenceType.SQL_LOG_JOIN, relation.evidence().get(0).type());
        assertTrue(relation.evidence().get(0).detail().contains("token-event"));
    }

    @Test
    void sqlOnlyIdShapeDoesNotInferFkDirection() {
        SqlStatementRecord statement = record("SELECT * FROM invoices i JOIN accounts a ON i.account_id = a.id");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "invoices", "i", 1),
                table("JOIN", "accounts", "a", 1),
                equality("i", "account_id", "a", "id", 1)
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Expected SQL-only equality to remain as column co-occurrence: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.CO_OCCURRENCE, relation.relationType());
        assertEquals(EvidenceType.SQL_LOG_JOIN, relation.evidence().get(0).type());
        assertEquals("accounts.id", relation.source().displayName());
        assertEquals("invoices.account_id", relation.target().displayName());
    }

    @Test
    void sameAliasSameColumnEqualityDoesNotProduceCoOccurrence() {
        SqlStatementRecord statement = record("SELECT * FROM employees e WHERE e.id = e.id");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                equality("e", "id", "e", "id", 1)
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(), () -> "Same alias self-comparison must not produce a relation: " + relations);
    }

    @Test
    void differentAliasSamePhysicalColumnSelfJoinKeepsRoleCoOccurrence() {
        SqlStatementRecord statement = record("SELECT * FROM employees e JOIN employees manager ON e.id = manager.id");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                table("JOIN", "employees", "manager", 1),
                equality("e", "id", "manager", "id", 1)
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Different aliases of the same physical column still express a self-join role: " + relations);
        RelationshipCandidate relation = relations.get(0);
        assertEquals(RelationType.CO_OCCURRENCE, relation.relationType());
        assertEquals("employees.id", relation.source().displayName());
        assertEquals("employees.id", relation.target().displayName());
        assertEquals(true, relation.evidence().get(0).attributes().get("selfJoinRole"));
        assertEquals("e", relation.evidence().get(0).attributes().get("leftAlias"));
        assertEquals("manager", relation.evidence().get(0).attributes().get("rightAlias"));
    }

    @Test
    void differentAliasSamePhysicalColumnInSubqueryKeepsRoleCoOccurrence() {
        SqlStatementRecord statement = record("SELECT * FROM employees e WHERE e.id IN (SELECT e2.id FROM employees e2)");
        StructuredParseResult structured = structured(List.of(
                table("FROM", "employees", "e", 1),
                table("FROM", "employees", "e2", 1),
                new StructuredSqlEvent(StructuredParseEventType.IN_SUBQUERY_PREDICATE, "independence.sql", 1,
                        Map.of("outerAlias", "e", "outerColumn", "id",
                                "innerAlias", "e2", "innerColumn", "id",
                                "verifiedColumnSubquery", true))
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertEquals(1, relations.size(), () -> "Different aliases of the same physical column should keep role context through IN: " + relations);
        assertEquals(EvidenceType.SQL_LOG_SUBQUERY_IN, relations.get(0).evidence().get(0).type());
        assertEquals(true, relations.get(0).evidence().get(0).attributes().get("selfJoinRole"));
    }

    @Test
    void ambiguousAliasDoesNotResolveAcrossIndependentQueryBlocks() {
        SqlStatementRecord statement = record("""
                WITH sales_stats AS (
                    SELECT soi.product_id
                    FROM sales_order_items soi
                    JOIN products p ON soi.product_id = p.id
                )
                SELECT e.id, p.name
                FROM employees e
                JOIN positions p ON e.position_id = p.id
                """);
        StructuredParseResult structured = structured(List.of(
                table("FROM", "sales_order_items", "soi", 1),
                table("JOIN", "products", "p", 1),
                table("FROM", "employees", "e", 6),
                table("JOIN", "positions", "p", 7),
                equality("soi", "product_id", "p", "id", 3),
                equality("e", "position_id", "p", "id", 7)
        ));

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertEquals(2, relations.size(),
                () -> "Alias p should resolve to the nearest query-block rowset without cross-block leakage: " + relations);
        assertTrue(relations.stream().anyMatch(relation ->
                        relation.source().displayName().equals("products.id")
                                && relation.target().displayName().equals("sales_order_items.product_id")),
                () -> "CTE predicate should resolve p to products: " + relations);
        assertTrue(relations.stream().anyMatch(relation ->
                        relation.source().displayName().equals("employees.position_id")
                                && relation.target().displayName().equals("positions.id")),
                () -> "Outer predicate should resolve p to positions: " + relations);
        assertTrue(relations.stream().noneMatch(relation ->
                        relation.source().displayName().equals("positions.id")
                                && relation.target().displayName().equals("sales_order_items.product_id")),
                () -> "Alias p must not leak from the outer query into the CTE predicate: " + relations);
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
                () -> "MySQL-only STRAIGHT_JOIN rowset extraction belongs in MySQL token-event typed visitor: "
                        + relations);
    }

    @Test
    void tokenEventExtractorDoesNotOwnPostgresOnlyOnlyCompatibility() {
        SqlStatementRecord statement = record("SELECT * FROM ONLY orders o JOIN users u ON o.user_id = u.id");
        StructuredParseResult structured = structured(List.of());

        List<RelationshipCandidate> relations = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relations.isEmpty(),
                () -> "PostgreSQL-only ONLY rowset extraction belongs in PostgreSQL token-event typed visitor: "
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
                    () -> "MySQL-only rowset extraction belongs in MySQL token-event typed visitor. SQL: " + sql
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
