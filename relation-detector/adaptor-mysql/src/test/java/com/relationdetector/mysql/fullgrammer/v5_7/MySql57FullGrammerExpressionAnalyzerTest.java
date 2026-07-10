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

    private String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }
}
