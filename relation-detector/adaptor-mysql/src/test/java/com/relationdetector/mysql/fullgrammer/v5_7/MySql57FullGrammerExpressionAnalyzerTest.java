package com.relationdetector.mysql.fullgrammer.v5_7;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;

class MySql57FullGrammerExpressionAnalyzerTest {
    @Test
    void caseSourceSharedByPredicateAndBranchRemainsValueLineage() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE order_rollup r
                SET amount = CASE
                    WHEN r.amount > 0 THEN r.amount
                    ELSE (SELECT p.amount
                          FROM payments p
                          WHERE p.order_id = r.order_id
                          LIMIT 1)
                END;
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());

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
        SqlStatementRecord statement = new SqlStatementRecord("""
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
                """, StatementSourceType.PLAIN_SQL, "mysql-purchase-return-items.sql", 1, 1, Map.of());
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
    void scalarSubqueryAssignmentSplitsSelectedValueFromLocatorPredicateControl() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE serial_numbers sn
                JOIN products p ON p.id = sn.product_id
                SET sn.batch_id = (
                    SELECT id
                    FROM product_batches
                    WHERE product_id = p.id
                );
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());

        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement, structured)
                .stream()
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
    void scalarCorrelationResolvesThroughShadowedDerivedProjectionAlias() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO jsh_temp_mock_plan (user_id)
                SELECT rand_tbl.user_id
                FROM (
                    SELECT (
                        SELECT emp.user_id
                        FROM jsh_orga_user_rel emp
                        WHERE emp.orga_id = o.org_id
                          AND emp.delete_flag = '0'
                        LIMIT 1
                    ) AS user_id
                    FROM tmp_sequence seq
                    CROSS JOIN (
                        SELECT o.org_id
                        FROM jsh_temp_org_pdf o
                        WHERE o.cdf_end >= RAND()
                        LIMIT 1
                    ) o
                ) rand_tbl;
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());

        var structured = new MySqlFullGrammerStructuredSqlParser().parseSql(statement, null);
        List<DataLineageCandidate> lineages = new StructuredDataLineageExtractor()
                .extract(statement, structured);

        assertTrue(lineages.stream().anyMatch(lineage ->
                        lineage.flowKind().name().equals("CONTROL")
                                && lineage.target().displayName().equals("jsh_temp_mock_plan.user_id")
                                && lineage.sources().stream().anyMatch(source ->
                                source.displayName().equals("jsh_temp_org_pdf.org_id"))),
                () -> "The outer scalar correlation must resolve o.org_id through the derived "
                        + "projection to jsh_temp_org_pdf.org_id: "
                        + lineages.stream().map(this::lineageFingerprint).toList()
                        + " events=" + structured.events());
    }

    @Test
    void scalarSubqueryAggregateCaseSplitsSelectedValueFromLocatorControl() {
        SqlStatementRecord statement = new SqlStatementRecord("""
                UPDATE supplier_products sp
                SET quality_score = COALESCE((
                    SELECT ROUND(COUNT(CASE WHEN ir.inspection_result = 'qualified' THEN 1 END) * 100.0
                        / NULLIF(COUNT(*), 0), 2)
                    FROM inspection_reports ir
                    JOIN product_batches pb ON ir.batch_id = pb.id
                    WHERE pb.supplier_id = sp.supplier_id
                      AND ir.product_id = sp.product_id
                ), 100);
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());

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
        SqlStatementRecord statement = new SqlStatementRecord("""
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
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());

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
    void reconciliationAndMasterDataCasesSplitBranchValuesFromPredicates() {
        SqlStatementRecord statement = new SqlStatementRecord("""
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
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
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
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO semantic_flags (is_current_year, is_womenwear)
                SELECT YEAR(so.order_date) = 2026,
                       pc.name = '女装' OR pc.name = 'women'
                FROM sales_orders so
                JOIN product_categories pc ON pc.id = so.category_id;
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
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
        SqlStatementRecord statement = new SqlStatementRecord("""
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
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
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
        SqlStatementRecord statement = new SqlStatementRecord("""
                INSERT INTO aggregate_rollup (total_amount, qualified_count)
                SELECT SUM(CASE WHEN tx.flag = 'Y' THEN tx.amount ELSE 0 END),
                       COUNT(CASE WHEN tx.flag = 'Y' THEN 1 END)
                FROM transactions tx;
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
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
        SqlStatementRecord statement = new SqlStatementRecord("""
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
                """, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
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
                        + "; actual=" + lineages.stream().map(this::lineageFingerprint).toList());
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
