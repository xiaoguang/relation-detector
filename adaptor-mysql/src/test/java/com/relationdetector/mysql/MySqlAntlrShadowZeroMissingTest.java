package com.relationdetector.mysql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.StatementSourceType;
import com.relationdetector.core.ShadowSqlRelationParser;

/**
 * MySQL adaptor-level zero-missing contract for the ANTLR shadow path.
 *
 * <p>These fixtures are business-shaped SQL built from MySQL documented syntax:
 * JOIN/table references can include LATERAL derived tables; multi-table UPDATE
 * and DELETE both reuse table_references. The test intentionally calls the
 * parser exposed by {@link MySqlDatabaseAdaptor}, so a future adaptor wiring
 * mistake cannot be hidden by core-only tests.
 */
class MySqlAntlrShadowZeroMissingTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("mysqlFixtures")
    void mysqlAntlrShadowDoesNotMissSimpleBaseline(Fixture fixture) {
        ShadowSqlRelationParser parser = (ShadowSqlRelationParser) new MySqlDatabaseAdaptor().sqlRelationParser();
        SqlStatementRecord statement = new SqlStatementRecord(
                fixture.sql(),
                StatementSourceType.PLAIN_SQL,
                fixture.name() + ".sql",
                1,
                fixture.sql().lines().count(),
                java.util.Map.of());

        ShadowSqlRelationParser.Result result = parser.parseWithDiagnostics(statement, null);

        assertTrue(result.missingSimpleRelations().isEmpty(),
                () -> "MySQL ANTLR shadow missed Simple baseline: " + result.missingSimpleRelations());
        for (ExpectedRelation expected : fixture.expectedRelations()) {
            assertRelation(result.shadowRelationships(), expected);
        }
        for (String pseudoTable : fixture.forbiddenPseudoTables()) {
            assertNoPseudoTable(result.shadowRelationships(), pseudoTable);
        }
    }

    private static List<Fixture> mysqlFixtures() {
        return List.of(
                new Fixture("mysql-multi-table-update-comma-and-join", """
                        UPDATE orders o, users u
                        JOIN accounts a ON u.account_id = a.id
                        SET o.reviewed_at = CURRENT_TIMESTAMP
                        WHERE o.user_id = u.id
                          AND o.status = 'PAID'
                          AND a.closed_at IS NULL
                        """,
                        List.of(
                                new ExpectedRelation("orders", "user_id", "users", "id"),
                                new ExpectedRelation("users", "account_id", "accounts", "id")),
                        List.of()),
                new Fixture("mysql-delete-using-table-references-left-join", """
                        DELETE FROM o
                        USING orders AS o
                        LEFT JOIN users AS u ON o.user_id = u.id
                        WHERE u.id IS NULL
                        """,
                        List.of(new ExpectedRelation("orders", "user_id", "users", "id")),
                        List.of()),
                new Fixture("mysql-cte-plus-lateral-derived-table", """
                        WITH recent_orders AS (
                          SELECT o.id AS order_id, o.user_id
                          FROM `orders` AS o
                          WHERE o.created_at >= CURRENT_DATE - INTERVAL 7 DAY
                        )
                        SELECT ro.order_id, u.email
                        FROM recent_orders ro
                        JOIN LATERAL (
                          SELECT ro.user_id AS buyer_id
                        ) AS buyer_projection ON true
                        JOIN `users` AS u ON buyer_projection.buyer_id = u.id
                        """,
                        List.of(new ExpectedRelation("orders", "user_id", "users", "id")),
                        List.of("recent_orders", "buyer_projection", "lateral")));
    }

    private void assertRelation(List<RelationshipCandidate> relations, ExpectedRelation expected) {
        assertTrue(relations.stream().anyMatch(relation ->
                        relation.source().displayName().equals(expected.sourceTable + "." + expected.sourceColumn)
                                && relation.target().displayName().equals(expected.targetTable + "." + expected.targetColumn)),
                () -> "Missing expected relation " + expected + ". Actual: " + relations);
    }

    private void assertNoPseudoTable(List<RelationshipCandidate> relations, String table) {
        assertFalse(relations.stream().anyMatch(relation ->
                        relation.source().table().tableName().equals(table)
                                || relation.target().table().tableName().equals(table)),
                () -> "ANTLR shadow should not emit pseudo-table " + table + ". Actual: " + relations);
    }

    private record Fixture(
            String name,
            String sql,
            List<ExpectedRelation> expectedRelations,
            List<String> forbiddenPseudoTables
    ) {
    }

    private record ExpectedRelation(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
    }
}
