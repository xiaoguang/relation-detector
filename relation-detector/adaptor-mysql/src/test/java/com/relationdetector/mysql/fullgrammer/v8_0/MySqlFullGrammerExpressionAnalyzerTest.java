package com.relationdetector.mysql.fullgrammer.v8_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
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
                .filter(event -> "VALUE".equals(event.attributes().get("flowKind")))
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
                .filter(event -> "VALUE".equals(event.attributes().get("flowKind")))
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
    void scalarSubqueryAssignmentSplitsSelectedValueFromLocatorPredicateControl() {
        SqlStatementRecord statement = statement("""
                UPDATE serial_numbers sn
                JOIN products p ON p.id = sn.product_id
                SET sn.batch_id = (
                    SELECT id
                    FROM product_batches
                    WHERE product_id = p.id
                );
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .toList();
        List<String> fingerprints = lineages.stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:DIRECT:product_batches.id->serial_numbers.batch_id"),
                () -> "Scalar subquery selected column should be VALUE lineage: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:product_batches.product_id,products.id->serial_numbers.batch_id"),
                () -> "Scalar subquery predicate columns should be CONTROL lineage: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void scalarSubqueryAggregateCaseSplitsSelectedValueFromLocatorControl() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET quality_score = COALESCE((
                    SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
                        / NULLIF(COUNT(*), 0), 2)
                    FROM inspection_reports ir
                    JOIN product_batches pb ON ir.batch_id = pb.id
                    WHERE pb.supplier_id = sp.supplier_id
                      AND ir.product_id = sp.product_id
                ), 100);
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:CASE_WHEN:inspection_reports.inspection_result->supplier_products.quality_score"),
                () -> "Scalar subquery SELECT projection CASE source should be VALUE lineage: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:inspection_reports.batch_id,product_batches.id,product_batches.supplier_id,supplier_products.supplier_id,inspection_reports.product_id,supplier_products.product_id->supplier_products.quality_score"),
                () -> "JOIN/WHERE/correlated sources should remain CONTROL lineage: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void scalarSubqueryAggregateArithmeticDoesNotPromoteLocatorPredicatesToValue() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET return_rate = ROUND(
                    COALESCE((
                        SELECT SUM(pri.return_qty)
                        FROM purchase_returns pr
                        JOIN purchase_return_items pri ON pri.return_id = pr.id
                        WHERE pr.supplier_id = sp.supplier_id
                          AND pri.product_id = sp.product_id
                    ), 0) / NULLIF((
                        SELECT SUM(poi.received_qty)
                        FROM purchase_order_items poi
                        JOIN purchase_orders po ON poi.order_id = po.id
                        WHERE po.supplier_id = sp.supplier_id
                          AND poi.product_id = sp.product_id
                    ), 0), 4);
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("VALUE:")
                                && fingerprint.contains("purchase_return_items.return_qty")),
                () -> "Numerator aggregate argument should stay VALUE lineage: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("VALUE:")
                                && fingerprint.contains("purchase_order_items.received_qty")),
                () -> "Denominator aggregate argument should stay VALUE lineage: "
                        + fingerprints + " events=" + structured.events());
        for (String locatorColumn : List.of(
                "purchase_return_items.return_id",
                "purchase_returns.id",
                "purchase_returns.supplier_id",
                "purchase_return_items.product_id",
                "purchase_order_items.order_id",
                "purchase_orders.id",
                "purchase_orders.supplier_id",
                "purchase_order_items.product_id",
                "supplier_products.supplier_id",
                "supplier_products.product_id")) {
            assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                            fingerprint.startsWith("VALUE:") && fingerprint.contains(locatorColumn)),
                    () -> "Scalar subquery locator column must not be VALUE lineage: "
                            + locatorColumn + " actual=" + fingerprints + " events=" + structured.events());
        }
    }

    @Test
    void functionArgumentCoUseDoesNotProduceColumnCoOccurrenceRelationship() {
        SqlStatementRecord statement = statement("""
                SELECT TIMESTAMPDIFF(YEAR, e.hire_date, e.resignation_date) AS tenure_years
                FROM employees e
                WHERE e.status = 'resigned';
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, structured)
                .stream()
                .map(this::relationFingerprint)
                .toList();

        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.contains("employees.hire_date->employees.resignation_date")
                                || fingerprint.contains("employees.resignation_date->employees.hire_date")),
                () -> "Function argument co-use is a metric dependency, not a physical relationship: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void expressionProjectionEqualityDoesNotBecomePhysicalRelationship() {
        SqlStatementRecord statement = statement("""
                WITH revenue AS (
                    SELECT DATE_FORMAT(so.order_date, '%Y-%m') AS period
                    FROM sales_orders so
                    GROUP BY DATE_FORMAT(so.order_date, '%Y-%m')
                ),
                salary_cost AS (
                    SELECT sp.salary_month AS period
                    FROM salary_payments sp
                    GROUP BY sp.salary_month
                )
                SELECT COALESCE(r.period, sc.period) AS period
                FROM revenue r
                JOIN salary_cost sc ON r.period = sc.period;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);

        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, structured)
                .stream()
                .map(this::relationFingerprint)
                .toList();

        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.contains("sales_orders.order_date->salary_payments.salary_month")
                                || fingerprint.contains("salary_payments.salary_month->sales_orders.order_date")),
                () -> "Formatted/derived period equality is metric dependency, not a physical relationship: "
                        + fingerprints + " events=" + structured.events());
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

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

}
