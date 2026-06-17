package com.relationdetector.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.relationdetector.api.RelationshipCandidate;
import com.relationdetector.api.SqlStatementRecord;
import com.relationdetector.api.Enums.EvidenceType;
import com.relationdetector.api.Enums.StatementSourceType;

/**
 * Dialect-shaped complex SQL matrix.
 *
 * <p>These fixtures are intentionally business-flavored instead of copied from
 * vendor documentation. Each statement is based on syntax documented by MySQL
 * or PostgreSQL and then reduced to the smallest form that still exercises the
 * relation detector:
 *
 * <ul>
 *   <li>MySQL WITH/JOIN/derived table/multiple-table UPDATE/DELETE syntax;</li>
 *   <li>PostgreSQL WITH RECURSIVE/table expression/LATERAL/UNNEST/MERGE syntax;</li>
 *   <li>future SQL Server rowset syntax kept as disabled acceptance fixtures.</li>
 * </ul>
 *
 * <p>Every passing scenario has a negative assertion so the parser cannot pass
 * merely by finding "some" relationship while also emitting CTE, derived table,
 * or function rowset pseudo-relations.
 */
class DialectSqlRelationParserComplexMatrixTest {
    private final SimpleSqlRelationParser parser = new SimpleSqlRelationParser();

    @Test
    void mysqlNestedCteResolvesBaseRelationsAndDoesNotEmitCtePseudoTables() {
        String sql = """
                WITH recent_orders AS (
                  SELECT o.id AS order_id, o.customer_id
                  FROM `orders` AS o
                  JOIN `customers` AS c ON o.customer_id = c.id
                  WHERE o.created_at >= CURRENT_DATE - INTERVAL 30 DAY
                ),
                regional_orders AS (
                  SELECT ro.order_id, c.region_id
                  FROM recent_orders ro
                  JOIN customers c ON ro.customer_id = c.id
                )
                SELECT ro.order_id, r.name
                FROM regional_orders ro
                JOIN regions r ON ro.region_id = r.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "customers", "region_id", "regions", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingTable(relations, "recent_orders");
        assertNoRelationContainingTable(relations, "regional_orders");
    }

    @Test
    void mysqlMultiTableUpdateParsesAllJoinPredicates() {
        String sql = """
                UPDATE `orders` o
                JOIN `users` u ON o.user_id = u.id
                JOIN `accounts` a ON u.account_id = a.id
                SET o.reviewed_at = CURRENT_TIMESTAMP
                WHERE o.status = 'PAID'
                  AND a.closed_at IS NULL
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "users", "account_id", "accounts", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingColumn(relations, "orders", "status");
        assertNoRelationContainingColumn(relations, "accounts", "closed_at");
    }

    @Test
    void mysqlDeleteFromLeftJoinKeepsJoinKindAndIgnoresNullFilter() {
        String sql = """
                DELETE o
                FROM orders o
                LEFT JOIN users u ON o.user_id = u.id
                WHERE u.id IS NULL
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertEvidenceAttribute(relations, "orders", "user_id", "joinKind", "LEFT_JOIN");
        assertNoRelationContainingDetail(relations, "u.id IS NULL");
    }

    @Test
    void mysqlDerivedTableColumnAliasResolvesToBaseTableColumns() {
        String sql = """
                SELECT projected.order_id, u.email
                FROM (
                  SELECT o.id AS order_id, o.user_id AS buyer_id
                  FROM orders o
                ) AS projected(order_id, buyer_id)
                JOIN users u ON projected.buyer_id = u.id
                JOIN invoices i ON i.order_id = projected.order_id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "invoices", "order_id", "orders", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingTable(relations, "projected");
    }

    @Test
    void postgresMultiLayerCteWithQuotedIdentifiersResolvesBaseRelations() {
        String sql = """
                WITH "a" AS (
                  SELECT o.id AS order_id, o.customer_id
                  FROM "public"."orders" o
                  JOIN "public"."customers" c ON o.customer_id = c.id
                ),
                b AS (
                  SELECT a.order_id, c.region_id
                  FROM "a" a
                  JOIN "public"."customers" c ON a.customer_id = c.id
                ),
                c AS (
                  SELECT b.order_id, b.region_id
                  FROM b
                )
                SELECT *
                FROM c
                JOIN "public"."regions" r ON c.region_id = r.id
                JOIN invoices i ON i.order_id = c.order_id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "customer_id", "customers", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "customers", "region_id", "regions", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "invoices", "order_id", "orders", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingTable(relations, "a");
        assertNoRelationContainingTable(relations, "b");
        assertNoRelationContainingTable(relations, "c");
    }

    @Test
    void postgresRecursiveCteDoesNotEmitRecursiveCteAsPhysicalTable() {
        String sql = """
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
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "employees", "manager_id", "employees", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingTable(relations, "employee_paths");
    }

    @Test
    void postgresLateralSubqueryDoesNotTreatLateralAliasAsPhysicalTable() {
        String sql = """
                SELECT o.id, u.email
                FROM orders o
                JOIN LATERAL (
                  SELECT o.user_id AS user_id
                ) x ON true
                JOIN users u ON x.user_id = u.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingTable(relations, "x");
        assertNoRelationContainingTable(relations, "lateral");
    }

    @Test
    void postgresUnnestWithOrdinalityDoesNotTreatFunctionAsPhysicalTable() {
        String sql = """
                SELECT u.id
                FROM users u
                JOIN orders o ON o.user_id = u.id
                JOIN unnest(ARRAY[1, 2, 3]) WITH ORDINALITY AS input_ids(user_id, ord)
                  ON input_ids.user_id = u.id
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingTable(relations, "unnest");
        assertNoRelationContainingTable(relations, "input_ids");
    }

    @Test
    void postgresMergeUsingParsesMergeOnPredicateAsJoinEvidence() {
        String sql = """
                MERGE INTO target_orders t
                USING source_orders s
                ON t.source_order_id = s.id
                WHEN MATCHED THEN
                  UPDATE SET synced_at = CURRENT_TIMESTAMP
                WHEN NOT MATCHED THEN
                  INSERT (source_order_id) VALUES (s.id)
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "target_orders", "source_order_id",
                "source_orders", "id", EvidenceType.SQL_LOG_JOIN);
        assertNoRelationContainingColumn(relations, "target_orders", "synced_at");
    }

    @Disabled("Future SQL Server adaptor acceptance fixture: bracket identifiers and APPLY rowsets are not supported yet.")
    @Test
    void futureSqlServerApplyFixture() {
        String sql = """
                SELECT o.id, u.email
                FROM [dbo].[orders] AS o
                CROSS APPLY (
                  SELECT o.user_id
                ) AS x
                JOIN [dbo].[users] AS u ON x.user_id = u.id
                OUTER APPLY (
                  SELECT TOP 1 p.id
                  FROM [dbo].[payments] p
                  WHERE p.order_id = o.id
                ) AS latest_payment
                """;

        List<RelationshipCandidate> relations = parse(sql);

        assertColumnRelation(relations, "orders", "user_id", "users", "id", EvidenceType.SQL_LOG_JOIN);
        assertColumnRelation(relations, "payments", "order_id", "orders", "id", EvidenceType.SQL_LOG_JOIN);
    }

    private List<RelationshipCandidate> parse(String sql) {
        return parser.parse(new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "dialect-complex-matrix.sql", 1, sql.lines().count(), java.util.Map.of()));
    }

    private void assertColumnRelation(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String targetTable,
            String targetColumn,
            EvidenceType evidenceType
    ) {
        boolean found = relations.stream().anyMatch(r ->
                r.source().isColumnLevel()
                        && r.target().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.target().table().tableName().equals(targetTable)
                        && r.target().column().columnName().equals(targetColumn)
                        && r.evidence().stream().anyMatch(e -> e.type() == evidenceType));
        assertTrue(found, () -> "Missing relation "
                + sourceTable + "." + sourceColumn + " -> "
                + targetTable + "." + targetColumn
                + ". Actual: " + describe(relations));
    }

    private void assertEvidenceAttribute(
            List<RelationshipCandidate> relations,
            String sourceTable,
            String sourceColumn,
            String attribute,
            String value
    ) {
        boolean found = relations.stream().anyMatch(r ->
                r.source().isColumnLevel()
                        && r.source().table().tableName().equals(sourceTable)
                        && r.source().column().columnName().equals(sourceColumn)
                        && r.evidence().stream().anyMatch(e -> value.equals(String.valueOf(e.attributes().get(attribute)))));
        assertTrue(found, () -> "Missing evidence attribute "
                + attribute + "=" + value + " for " + sourceTable + "." + sourceColumn
                + ". Actual: " + describe(relations));
    }

    private void assertNoRelationContainingTable(List<RelationshipCandidate> relations, String tableName) {
        assertFalse(relations.stream().anyMatch(r ->
                        r.source().table().tableName().equals(tableName)
                                || r.target().table().tableName().equals(tableName)),
                () -> "Should not emit pseudo-table relation for " + tableName + ". Actual: " + describe(relations));
    }

    private void assertNoRelationContainingColumn(List<RelationshipCandidate> relations, String tableName, String columnName) {
        assertFalse(relations.stream().anyMatch(r ->
                        (r.source().isColumnLevel()
                                && r.source().table().tableName().equals(tableName)
                                && r.source().column().columnName().equals(columnName))
                                || (r.target().isColumnLevel()
                                && r.target().table().tableName().equals(tableName)
                                && r.target().column().columnName().equals(columnName))),
                () -> "Filter/update column should not become relation evidence: "
                        + tableName + "." + columnName + ". Actual: " + describe(relations));
    }

    private void assertNoRelationContainingDetail(List<RelationshipCandidate> relations, String detail) {
        assertFalse(relations.stream().anyMatch(r ->
                        r.evidence().stream().anyMatch(e -> e.detail().contains(detail))),
                () -> "Filter predicate should not become evidence detail: " + detail
                        + ". Actual: " + describe(relations));
    }

    private String describe(List<RelationshipCandidate> relations) {
        return relations.stream()
                .map(r -> r.source().displayName() + " -> " + r.target().displayName()
                        + " " + r.relationType() + " " + r.evidence().stream()
                        .map(e -> e.type().name() + e.attributes()).collect(Collectors.joining(",")))
                .collect(Collectors.joining("; "));
    }
}
