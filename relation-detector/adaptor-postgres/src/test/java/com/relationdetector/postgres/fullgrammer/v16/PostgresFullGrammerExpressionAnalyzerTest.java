package com.relationdetector.postgres.fullgrammer.v16;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import com.relationdetector.contracts.Enums.EvidenceType;
import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.model.RelationshipCandidate;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.core.relation.TokenEventRelationExtractor;

class PostgresFullGrammerExpressionAnalyzerTest {
    @Test
    void fullGrammerSeparatesScalarAggregateValueAndControls() {
        SqlStatementRecord statement = statement("""
                UPDATE supplier_products sp
                SET total_order_qty = (
                    SELECT SUM(poi.quantity)
                    FROM purchase_order_items poi
                    JOIN purchase_orders po ON poi.order_id = po.id
                    WHERE poi.product_id = sp.product_id
                      AND po.supplier_id = sp.supplier_id
                );
                """);
        var structured = new PostgresFullGrammerDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(PostgresFullGrammerExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains(
                        "VALUE:AGGREGATE:purchase_order_items.quantity->supplier_products.total_order_qty"),
                () -> fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.stream().anyMatch(value -> value.startsWith("CONTROL:CASE_WHEN:")
                        && value.contains("purchase_order_items.order_id")
                        && value.contains("purchase_orders.id")
                        && value.contains("supplier_products.product_id")
                        && value.endsWith("->supplier_products.total_order_qty")),
                () -> fingerprints + " events=" + structured.events());
    }

    @Test
    void analyzerReadsCaseExpressionThroughFullGrammerEvents() {
        var result = new PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                UPDATE users u
                SET risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                ;
                """), null);

        List<String> fingerprints = new StructuredDataLineageExtractor()
                .extract(statement("""
                        UPDATE users u
                        SET risk_band = CASE WHEN u.risk_score > 80 THEN 'HIGH' ELSE u.risk_band END
                        ;
                        """), result)
                .stream()
                .map(PostgresFullGrammerExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("CONTROL:CASE_WHEN:users.risk_score->users.risk_band"),
                () -> fingerprints.toString());
        assertTrue(fingerprints.contains("VALUE:CASE_WHEN:users.risk_band->users.risk_band"),
                () -> fingerprints.toString());
    }

    @Test
    void nestedInSubqueryDoesNotBindInnerUnqualifiedColumnToMiddleScope() {
        SqlStatementRecord statement = statement("""
                SELECT a.account_id,
                       (SELECT count(*) FROM pg10_transactions
                        WHERE account_id IN (
                            SELECT account_id FROM pg10_accounts
                            WHERE branch_code = a.branch_code
                              AND risk_score <= a.risk_score
                        )
                       ) AS peer_branch_weekly_txns
                FROM pg10_accounts a;
                """);

        var structured = new PostgresFullGrammerDialectModule().sqlParser().parseSql(statement, null);
        List<RelationshipCandidate> relationships = new TokenEventRelationExtractor().extract(statement, structured);

        assertTrue(relationships.stream().anyMatch(candidate ->
                fingerprint(candidate).equals("CO_OCCURRENCE:pg10_accounts.account_id->pg10_transactions.account_id:SQL_LOG_SUBQUERY_IN")));
        assertFalse(relationships.stream().anyMatch(candidate ->
                fingerprint(candidate).equals("CO_OCCURRENCE:pg10_transactions.branch_code->pg10_accounts.branch_code:SQL_LOG_JOIN")));
    }

    @Test
    void scalarProjectionSubqueryEmitsNestedJoinRelationship() {
        SqlStatementRecord statement = statement("""
                SELECT
                    pb.batch_no,
                    (SELECT string_agg(DISTINCT CONCAT(w.name, ':', i.quantity), ', ')
                     FROM inventory i
                     JOIN warehouses w ON i.warehouse_id = w.id
                     WHERE i.batch_id = pb.id AND i.quantity > 0) AS current_stock_distribution
                FROM product_batches pb;
                """);

        var structured = new PostgresFullGrammerDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new TokenEventRelationExtractor().extract(statement, structured)
                .stream()
                .map(PostgresFullGrammerExpressionAnalyzerTest::fingerprint)
                .toList();

        assertTrue(fingerprints.contains("CO_OCCURRENCE:inventory.warehouse_id->warehouses.id:SQL_LOG_JOIN"),
                () -> "Missing nested scalar subquery join. Actual=" + fingerprints);
    }

    @Test
    void analyzerMarksExpressionProjectionAsNonRelationColumnExpression() {
        var result = new PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                SELECT u.account_status
                FROM application_users u
                WHERE u.account_status IN (
                    SELECT 'STATUS_' || status_label
                    FROM structural_statuses
                );
                """), null);

        assertFalse(result.events().stream().anyMatch(event ->
                event.type() == StructuredParseEventType.IN_SUBQUERY_PREDICATE
                        && "structural_statuses".equals(event.innerTable())));

    }

    @Test
    void analyzerClassifiesPostgresConcatOperatorAsConcatFormat() {
        var result = new PostgresFullGrammerDialectModule()
                .sqlParser()
                .parseSql(statement("""
                UPDATE order_ledgers l
                SET remarks = 'User risk level: ' || u.risk_level || ' | Order Rank: ' || fo.rnk
                FROM fraud_orders fo, users u
                WHERE l.order_id = fo.order_id
                  AND fo.user_id = u.id;
                """), null);

        var assignment = result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.UPDATE_ASSIGNMENT)
                .filter(event -> "remarks".equals(event.targetColumn()))
                .findFirst()
                .orElseThrow();

        assertEquals("CONCAT_FORMAT", assignment.expression().transformType().name());
        assertEquals(List.of("u", "fo"), assignment.expression().sourceAliases());
        assertEquals(List.of("risk_level", "rnk"), assignment.expression().sourceColumns());
    }

    @Test
    void fullGrammerPlainInsertSelectEmitsLineage() {
        SqlStatementRecord statement = statement("""
                INSERT INTO shipments (shipment_no, order_id, warehouse_id, to_address, receiver_phone)
                SELECT
                    CONCAT('SH-', CAST(so.id AS text)),
                    so.id,
                    so.warehouse_id,
                    c.address,
                    c.phone
                FROM sales_orders so
                JOIN customers c ON c.id = so.customer_id;
                """);

        var structured = new PostgresFullGrammerDialectModule().sqlParser().parseSql(statement, null);
        List<String> fingerprints = new StructuredDataLineageExtractor().extract(statement, structured).stream()
                .map(PostgresFullGrammerExpressionAnalyzerTest::lineageFingerprint)
                .sorted()
                .toList();

        assertTrue(fingerprints.contains("VALUE:CONCAT_FORMAT:sales_orders.id->shipments.shipment_no"),
                () -> "Full-grammer INSERT SELECT should map expression source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:sales_orders.id->shipments.order_id"),
                () -> "Full-grammer INSERT SELECT should map direct source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:sales_orders.warehouse_id->shipments.warehouse_id"),
                () -> "Full-grammer INSERT SELECT should map direct source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:customers.address->shipments.to_address"),
                () -> "Full-grammer INSERT SELECT should map joined source to target. Actual="
                        + fingerprints + " events=" + structured.events());
        assertTrue(fingerprints.contains("VALUE:DIRECT:customers.phone->shipments.receiver_phone"),
                () -> "Full-grammer INSERT SELECT should map joined source to target. Actual="
                        + fingerprints + " events=" + structured.events());
    }

    private static SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL, "fixture.sql", 1, 1, Map.of());
    }

    private static String fingerprint(RelationshipCandidate candidate) {
        EvidenceType evidenceType = candidate.evidence().isEmpty() ? null : candidate.evidence().get(0).type();
        return candidate.relationType() + ":"
                + candidate.source().displayName() + "->" + candidate.target().displayName()
                + ":" + evidenceType;
    }

    private static String lineageFingerprint(DataLineageCandidate lineage) {
        return lineage.flowKind() + ":"
                + lineage.transformType() + ":"
                + lineage.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(java.util.stream.Collectors.joining(","))
                + "->" + lineage.target().displayName();
    }

}
