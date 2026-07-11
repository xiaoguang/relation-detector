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
    void caseSourceSharedByPredicateAndBranchRemainsValueLineage() {
        SqlStatementRecord statement = statement("""
                UPDATE order_rollup r
                SET amount = CASE
                    WHEN r.amount > 0 THEN r.amount
                    ELSE (SELECT p.amount
                          FROM payments p
                          WHERE p.order_id = r.order_id
                          LIMIT 1)
                END;
                """);

        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:CASE_WHEN:order_rollup.amount,payments.amount->order_rollup.amount"),
                () -> "A column used by both WHEN and THEN must remain a VALUE source: "
                        + fingerprints + " events=" + structured.events());
    }

    @Test
    void nestedScalarBatchProjectionKeepsValuesSeparateFromCorrelatedControls() {
        SqlStatementRecord statement = statement("""
                INSERT INTO purchase_return_items (return_id, product_id, batch_id)
                SELECT
                    pr.id,
                    (SELECT product_id
                     FROM purchase_order_items
                     WHERE order_id = pr.purchase_order_id
                     LIMIT 1),
                    (SELECT id
                     FROM product_batches
                     WHERE product_id = (SELECT product_id
                                         FROM purchase_order_items
                                         WHERE order_id = pr.purchase_order_id
                                         LIMIT 1)
                     LIMIT 1)
                FROM purchase_returns pr;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
                .map(this::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:DIRECT:product_batches.id->purchase_return_items.batch_id"),
                () -> "Scalar projection should be VALUE lineage: " + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:product_batches.product_id,purchase_order_items.product_id,purchase_order_items.order_id,purchase_returns.purchase_order_id->purchase_return_items.batch_id"),
                () -> "Nested scalar predicates and correlation should be CONTROL lineage: "
                        + fingerprints + " events=" + structured.events());
        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.contains("product_batches.order_id")),
                () -> "No source may leak through the product_batches qualifier: " + fingerprints);
    }

    @Test
    void analyzerReadsArithmeticExpressionThroughFullGrammerEvents() {
        var result = new MySqlFullGrammerStructuredSqlParser()
                .parseSql(statement("""
                UPDATE inventory i
                SET i.reserved_quantity = i.reserved_quantity + oi.quantity
                ;
                """), null);

        var assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .filter(event -> "VALUE".equals(event.expression().flowKind().name()))
                .findFirst()
                .orElseThrow();

        assertEquals("ARITHMETIC", assignment.expression().transformType().name());
        assertEquals("VALUE", assignment.expression().flowKind().name());
        assertTrue(assignment.expression().sourceAliases().contains("i"));
        assertTrue(assignment.expression().sourceColumns().contains("reserved_quantity"));
        assertTrue(assignment.expression().sourceAliases().contains("oi"));
        assertTrue(assignment.expression().sourceColumns().contains("quantity"));
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
                .anyMatch(event -> "p_login_id".equals(event.left().alias())
                        || "p_login_id".equals(event.left().column())
                        || "p_login_id".equals(event.right().alias())
                        || "p_login_id".equals(event.right().column()));

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

        var assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .filter(event -> "VALUE".equals(event.expression().flowKind().name()))
                .findFirst()
                .orElseThrow();

        assertEquals("ARITHMETIC", assignment.expression().transformType().name());
        assertTrue(assignment.expression().sourceAliases().contains("cr"));
        assertTrue(assignment.expression().sourceColumns().contains("total_amount"));
        assertTrue(assignment.expression().sourceAliases().contains("o"));
        assertTrue(assignment.expression().sourceColumns().contains("total_amount"));
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

        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("VALUE:")
                                && fingerprint.contains("inspection_reports.inspection_result")
                                && fingerprint.endsWith("->supplier_products.quality_score")),
                () -> "A predicate-only CASE source must not become VALUE lineage: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("CONTROL:CASE_WHEN:")
                                && fingerprint.contains("inspection_reports.inspection_result")
                                && fingerprint.endsWith("->supplier_products.quality_score")),
                () -> "The CASE predicate must remain CONTROL lineage: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:inspection_reports.inspection_result,inspection_reports.batch_id,product_batches.id,product_batches.supplier_id,supplier_products.supplier_id,inspection_reports.product_id,supplier_products.product_id->supplier_products.quality_score"),
                () -> "CASE and locator sources should form one canonical CONTROL lineage observation: "
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

        var assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.INSERT_SELECT_MAPPING)
                .filter(event -> "due_date".equals(event.targetColumn()))
                .findFirst()
                .orElseThrow();

        assertEquals("FUNCTION_CALL", assignment.expression().transformType().name());
        assertEquals(List.of("so", "c"), assignment.expression().sourceAliases());
        assertEquals(List.of("order_date", "credit_days"), assignment.expression().sourceColumns());
    }

    @Test
    void reconciliationAndMasterDataCasesSplitBranchValuesFromPredicates() {
        SqlStatementRecord statement = statement("""
                UPDATE reconciliation_totals rt
                JOIN cashier_journals cj ON cj.account_id = rt.account_id
                SET rt.debit_amount = CASE
                        WHEN cj.journal_type = 'receipt' THEN cj.amount
                        ELSE 0
                    END,
                    rt.customer_address = CASE
                        WHEN rt.field_name = 'address' THEN rt.new_value
                        ELSE rt.customer_address
                    END;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(this::lineageFingerprint).sorted().toList();

        assertTrue(fingerprints.contains(
                        "VALUE:CASE_WHEN:cashier_journals.amount->reconciliation_totals.debit_amount"),
                () -> "THEN branch amount must be VALUE: " + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:cashier_journals.journal_type->reconciliation_totals.debit_amount"),
                () -> "WHEN predicate must be CONTROL: " + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "VALUE:CASE_WHEN:reconciliation_totals.new_value,reconciliation_totals.customer_address->reconciliation_totals.customer_address"),
                () -> "Master-data branch values must remain VALUE: " + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains(
                        "CONTROL:CASE_WHEN:reconciliation_totals.field_name->reconciliation_totals.customer_address"),
                () -> "Master-data field selector must be CONTROL: " + fingerprints + " events=" + structured.events());
    }

    @Test
    void standaloneBooleanProjectionsRemainValueAndPreserveFunctionTransform() {
        SqlStatementRecord statement = statement("""
                INSERT INTO semantic_flags (is_current_year, is_womenwear)
                SELECT YEAR(so.order_date) = 2026,
                       pc.name = '女装' OR pc.name = 'women'
                FROM sales_orders so
                JOIN product_categories pc ON pc.id = so.category_id;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(this::lineageFingerprint).sorted().toList();

        assertTrue(fingerprints.contains(
                        "VALUE:FUNCTION_CALL:sales_orders.order_date->semantic_flags.is_current_year"),
                () -> "YEAR(...) equality is a value-producing function projection: "
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("VALUE:")
                                && fingerprint.contains("product_categories.name")
                                && fingerprint.endsWith("->semantic_flags.is_womenwear")),
                () -> "Boolean name projection must be VALUE: " + fingerprints + " events=" + structured.events());
        assertFalse(fingerprints.stream().anyMatch(fingerprint ->
                        fingerprint.startsWith("CONTROL:")
                                && fingerprint.contains("product_categories.name")
                                && fingerprint.endsWith("->semantic_flags.is_womenwear")),
                () -> "Standalone boolean projection must not be CONTROL: " + fingerprints);
    }

    @Test
    void nestedCaseKeepsSelectorsAndEveryWhenPredicateOutOfLeafValues() {
        SqlStatementRecord statement = statement("""
                UPDATE reconciliation_totals rt
                JOIN cashier_journals cj ON cj.account_id = rt.account_id
                SET rt.amount = CASE rt.journal_scope
                    WHEN 'cash' THEN CASE
                        WHEN cj.journal_type = 'receipt' THEN cj.amount
                        ELSE rt.fallback_amount
                    END
                    WHEN 'manual' THEN CASE
                        WHEN rt.is_enabled = 1 THEN rt.manual_amount
                        ELSE 0
                    END
                    ELSE rt.default_amount
                END;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertLineageSources(lineages, "VALUE", "CASE_WHEN", "reconciliation_totals.amount",
                List.of("cashier_journals.amount", "reconciliation_totals.fallback_amount",
                        "reconciliation_totals.manual_amount", "reconciliation_totals.default_amount"));
        assertLineageSources(lineages, "CONTROL", "CASE_WHEN", "reconciliation_totals.amount",
                List.of("reconciliation_totals.journal_scope", "cashier_journals.journal_type",
                        "reconciliation_totals.is_enabled"));
    }

    @Test
    void aggregateWrappedCaseKeepsOuterAggregateAndPredicateOnlyCountHasNoValue() {
        SqlStatementRecord statement = statement("""
                INSERT INTO aggregate_rollup (total_amount, qualified_count)
                SELECT SUM(CASE WHEN tx.flag = 'Y' THEN tx.amount ELSE 0 END),
                       COUNT(CASE WHEN tx.flag = 'Y' THEN 1 END)
                FROM transactions tx;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertLineageSources(lineages, "VALUE", "AGGREGATE", "aggregate_rollup.total_amount",
                List.of("transactions.amount"));
        assertLineageSources(lineages, "CONTROL", "CASE_WHEN", "aggregate_rollup.total_amount",
                List.of("transactions.flag"));
        assertFalse(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind().name().equals("VALUE")
                                && lineage.target().displayName().equals("aggregate_rollup.qualified_count")),
                () -> "COUNT(CASE predicate THEN literal) must not invent VALUE sources: " + lineages);
        assertLineageSources(lineages, "CONTROL", "CASE_WHEN", "aggregate_rollup.qualified_count",
                List.of("transactions.flag"));
    }

    @Test
    void scalarPredicateAggregateDoesNotOverrideSelectedCumulativeProjection() {
        SqlStatementRecord statement = statement("""
                INSERT INTO jsh_temp_mock_plan (mock_timestamp_str)
                SELECT CONCAT(
                    '2026-01-01 ',
                    LPAD((SELECT h.hour_val
                           FROM (
                               SELECT hour_val,
                                      (@running_h_sum := @running_h_sum + weight) AS h_cdf
                               FROM jsh_temp_hour_pdf
                           ) h
                           WHERE h.h_cdf >= RAND() * (
                               SELECT SUM(weight) FROM jsh_temp_hour_pdf
                           )
                           ORDER BY h.h_cdf
                           LIMIT 1), 2, '0'))
                FROM tmp_sequence;
                """);
        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor().extract(statement, structured);

        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind().name().equals("VALUE")
                                && lineage.transformType().name().equals("CUMULATIVE")
                                && lineage.target().displayName().equals("jsh_temp_mock_plan.mock_timestamp_str")
                                && lineage.sources().stream().anyMatch(source ->
                                source.displayName().equals("jsh_temp_hour_pdf.hour_val"))),
                () -> "Selected cumulative projection must remain VALUE/CUMULATIVE: " + lineages
                        + " events=" + structured.events());
        assertFalse(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind().name().equals("VALUE")
                                && lineage.transformType().name().equals("AGGREGATE")
                                && lineage.target().displayName().equals("jsh_temp_mock_plan.mock_timestamp_str")
                                && lineage.sources().stream().anyMatch(source ->
                                source.displayName().equals("jsh_temp_hour_pdf.hour_val"))),
                () -> "Predicate SUM(weight) must not override the selected VALUE transform: " + lineages);
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind().name().equals("CONTROL")
                                && lineage.target().displayName().equals("jsh_temp_mock_plan.mock_timestamp_str")
                                && lineage.sources().stream().anyMatch(source ->
                                source.displayName().equals("jsh_temp_hour_pdf.weight"))),
                () -> "Predicate aggregate sources must remain CONTROL: " + lineages
                        + " events=" + structured.events());
    }

    private void assertLineageSources(
            List<DataLineageCandidate> lineages,
            String flow,
            String transform,
            String target,
            List<String> expectedSources
    ) {
        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind().name().equals(flow)
                                && lineage.transformType().name().equals(transform)
                                && lineage.target().displayName().equals(target)
                                && lineage.sources().stream().map(source -> source.displayName()).toList()
                                .equals(expectedSources)),
                () -> "Missing " + flow + "/" + transform + " " + expectedSources + " -> " + target
                        + "; actual=" + lineages);
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
