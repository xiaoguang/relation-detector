package com.relationdetector.mysql.fullgrammer.v8_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

class MySqlFullGrammerExpressionAnalyzerTest {
    @Test
    void analyzerReadsArithmeticExpressionThroughFullGrammerEvents() {
        var result = new MySqlFullGrammerStructuredSqlParser()
                .parseSql(statement("""
                UPDATE inventory i
                SET i.reserved_quantity = i.reserved_quantity + oi.quantity
                ;
                """), null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("ARITHMETIC", assignment.get("transformType"));
        assertEquals("VALUE", assignment.get("flowKind"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("i"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("reserved_quantity"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("oi"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("quantity"));
    }

    @Test
    void procedureParametersAreNotDefaultQualifiedAsPhysicalColumns() {
        var result = new MySqlFullGrammerStructuredSqlParser()
                .parseSql(statement("""
                CREATE PROCEDURE p(IN p_login_id BIGINT)
                BEGIN
                    DECLARE v_tenant_id BIGINT;
                    SELECT tenant_id INTO v_tenant_id
                    FROM jsh_user
                    WHERE id = p_login_id
                    LIMIT 1;
                END
                """), null);

        boolean parameterEquality = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.PREDICATE_EQUALITY)
                .anyMatch(event -> event.attributes().containsValue("p_login_id"));

        assertFalse(parameterEquality);
    }

    @Test
    void scalarInSubqueryBindsOuterUnqualifiedColumnBeforeVisitingNestedRowsets() {
        SqlStatementRecord statement = statement("""
                SELECT MAX(journal_date)
                FROM cashier_journals
                WHERE reference_type = 'sales_order'
                  AND reference_id IN (
                      SELECT id
                      FROM sales_orders
                      WHERE customer_id = 42
                  );
                """);

        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, structured)
                .stream()
                .map(this::relationFingerprint)
                .toList();

        assertEquals(List.of("CO_OCCURRENCE:cashier_journals.reference_id->sales_orders.id:SQL_LOG_SUBQUERY_IN"),
                fingerprints);
    }

    @Test
    void scalarProjectionSubqueryEmitsNestedJoinRelationship() {
        SqlStatementRecord statement = statement("""
                SELECT
                    pb.batch_no,
                    (SELECT GROUP_CONCAT(DISTINCT CONCAT(w.name, ':', i.quantity) SEPARATOR ', ')
                     FROM inventory i
                     JOIN warehouses w ON i.warehouse_id = w.id
                     WHERE i.batch_id = pb.id AND i.quantity > 0) AS current_stock_distribution
                FROM product_batches pb;
                """);

        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, structured)
                .stream()
                .map(this::relationFingerprint)
                .toList();

        assertTrue(fingerprints.contains("CO_OCCURRENCE:inventory.warehouse_id->warehouses.id:SQL_LOG_JOIN"),
                () -> "Missing nested scalar subquery join. Actual=" + fingerprints);
    }

    @Test
    void cteProjectionThroughNestedDerivedTableKeepsOuterDefaultQualifier() {
        SqlStatementRecord statement = statement("""
                WITH leave_balance AS (
                    SELECT e.id AS employee_id
                    FROM employees e
                ),
                latest_performance AS (
                    SELECT employee_id
                    FROM (
                        SELECT employee_id,
                               ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY review_period DESC) AS rn
                        FROM performance_reviews
                        WHERE status = 'confirmed'
                    ) t
                    WHERE rn = 1
                )
                SELECT *
                FROM leave_balance lb
                JOIN latest_performance lp ON lb.employee_id = lp.employee_id;
                """);

        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, structured)
                .stream()
                .map(this::relationFingerprint)
                .toList();

        assertTrue(fingerprints.contains("CO_OCCURRENCE:employees.id->performance_reviews.employee_id:SQL_LOG_JOIN"),
                () -> "CTE projection should resolve through the nested derived table alias. Actual=" + fingerprints
                        + " events=" + structured.events());
    }


    @Test
    void updateArithmeticExpressionIncludesScalarSubquerySelectSource() {
        SqlStatementRecord statement = statement("""
                UPDATE customer_rollup cr
                SET total_amount = cr.total_amount + (
                  SELECT o.total_amount
                  FROM orders o
                  WHERE o.customer_id = cr.customer_id
                );
                """);
        var result = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("ARITHMETIC", assignment.get("transformType"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("cr"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("total_amount"));
        assertTrue(((List<?>) assignment.get("sourceAliases")).contains("o"));
        assertTrue(((List<?>) assignment.get("sourceColumns")).contains("total_amount"));
    }

    @Test
    void dateAddIntervalColumnIsFunctionCallLineageSource() {
        SqlStatementRecord statement = statement("""
                INSERT INTO ar_aging_snapshots (customer_id, due_date)
                SELECT so.customer_id,
                       DATE_ADD(so.order_date, INTERVAL c.credit_days DAY)
                FROM sales_orders so
                JOIN customers c ON so.customer_id = c.id;
                """);
        var result = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        Map<String, Object> assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING)
                .filter(event -> "due_date".equals(event.attributes().get("targetColumn")))
                .findFirst()
                .orElseThrow()
                .attributes();

        assertEquals("FUNCTION_CALL", assignment.get("transformType"));
        assertEquals(List.of("so", "c"), assignment.get("sourceAliases"));
        assertEquals(List.of("order_date", "credit_days"), assignment.get("sourceColumns"));
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

    private String relationFingerprint(RelationshipCandidate candidate) {
        return candidate.relationType()
                + ":" + candidate.source().displayName()
                + "->" + candidate.target().displayName()
                + ":" + candidate.evidence().get(0).type();
    }

}
