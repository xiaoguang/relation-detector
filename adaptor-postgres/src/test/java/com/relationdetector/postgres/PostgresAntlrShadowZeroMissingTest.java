package com.relationdetector.postgres;

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
 * PostgreSQL adaptor-level zero-missing contract for ANTLR shadow extraction.
 *
 * <p>The fixtures follow PostgreSQL documented shapes for LATERAL table
 * expressions, recursive CTEs, and MERGE USING. They validate the adaptor's
 * PostgreSQL parser wiring and the migration rule that ANTLR may add
 * relationships but must not lose any Simple baseline relationship.
 */
class PostgresAntlrShadowZeroMissingTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("postgresFixtures")
    void postgresAntlrShadowDoesNotMissSimpleBaseline(Fixture fixture) {
        ShadowSqlRelationParser parser = (ShadowSqlRelationParser) new PostgresDatabaseAdaptor().sqlRelationParser();
        SqlStatementRecord statement = new SqlStatementRecord(
                fixture.sql(),
                StatementSourceType.PLAIN_SQL,
                fixture.name() + ".sql",
                1,
                fixture.sql().lines().count(),
                java.util.Map.of());

        ShadowSqlRelationParser.Result result = parser.parseWithDiagnostics(statement, null);

        assertTrue(result.missingSimpleRelations().isEmpty(),
                () -> "PostgreSQL ANTLR shadow missed Simple baseline: " + result.missingSimpleRelations());
        for (ExpectedRelation expected : fixture.expectedRelations()) {
            assertRelation(result.shadowRelationships(), expected);
        }
        for (String pseudoTable : fixture.forbiddenPseudoTables()) {
            assertNoPseudoTable(result.shadowRelationships(), pseudoTable);
        }
    }

    private static List<Fixture> postgresFixtures() {
        return List.of(
                new Fixture("postgres-recursive-cte-self-reference", """
                        WITH RECURSIVE employee_paths(id, manager_id) AS (
                          SELECT e.id, e.manager_id
                          FROM employees e
                          WHERE e.manager_id IS NULL
                          UNION ALL
                          SELECT e.id, e.manager_id
                          FROM employees e
                          JOIN employee_paths ep ON ep.id = e.manager_id
                        )
                        SELECT *
                        FROM employee_paths ep
                        JOIN employees manager ON ep.manager_id = manager.id
                        """,
                        List.of(new ExpectedRelation("employees", "manager_id", "employees", "id")),
                        List.of("employee_paths")),
                new Fixture("postgres-lateral-derived-table", """
                        SELECT o.id, u.email
                        FROM orders o
                        LEFT JOIN LATERAL (
                          SELECT o.user_id AS user_id
                        ) projected_user ON true
                        JOIN users u ON projected_user.user_id = u.id
                        WHERE u.deleted_at IS NULL
                        """,
                        List.of(new ExpectedRelation("orders", "user_id", "users", "id")),
                        List.of("projected_user", "lateral")),
                new Fixture("postgres-merge-using-join-condition", """
                        MERGE INTO target_orders AS t
                        USING source_orders AS s
                        ON t.source_order_id = s.id
                        WHEN MATCHED AND s.cancelled_at IS NULL THEN
                          UPDATE SET synced_at = CURRENT_TIMESTAMP
                        WHEN NOT MATCHED THEN
                          INSERT (source_order_id) VALUES (s.id)
                        """,
                        List.of(new ExpectedRelation("target_orders", "source_order_id", "source_orders", "id")),
                        List.of()));
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
