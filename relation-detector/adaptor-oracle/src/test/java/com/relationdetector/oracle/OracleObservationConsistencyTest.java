package com.relationdetector.oracle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.Enums.StatementSourceType;
import com.relationdetector.contracts.Enums.StructuredParseEventType;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.parse.StructuredParseResult;
import com.relationdetector.contracts.spi.Collectors.StructuredSqlParser;
import com.relationdetector.core.lineage.StructuredDataLineageExtractor;
import com.relationdetector.core.provenance.SemanticObservationFingerprint;
import com.relationdetector.core.relation.TokenEventRelationExtractor;
import com.relationdetector.oracle.fullgrammer.v26ai.OracleFullGrammerDialectModule;
import com.relationdetector.oracle.tokenevent.OracleTokenEventStructuredSqlParser;

class OracleObservationConsistencyTest {
    private final StructuredSqlParser token = new OracleTokenEventStructuredSqlParser();
    private final StructuredSqlParser full = new OracleFullGrammerDialectModule().sqlParser();

    @Test
    void selectIntoJoinHasTheSameSemanticObservations() {
        assertConsistent("""
                SELECT COALESCE(SUM(soi.amount), 0),
                       COALESCE(SUM(soi.quantity * p.purchase_price), 0)
                INTO v_revenue, v_cost
                FROM sales_order_items soi
                JOIN sales_orders so ON soi.order_id = so.id
                JOIN products p ON soi.product_id = p.id
                WHERE soi.product_id = p_product_id
                  AND so.order_date BETWEEN p_start_date AND p_end_date
                  AND so.status NOT IN ('draft', 'cancelled')
                """);
    }

    @Test
    void cteWindowQueryHasTheSameSemanticObservations() {
        assertConsistent("""
                WITH product_sales AS (
                    SELECT
                        soi.product_id,
                        p.sku,
                        p.name AS product_name,
                        pc.name AS category_name,
                        SUM(soi.amount) AS total_sales_amount,
                        SUM(soi.quantity) AS total_sales_qty,
                        ROUND(SUM(soi.amount) * 100.0 / SUM(SUM(soi.amount)) OVER (), 2) AS sales_pct
                    FROM sales_order_items soi
                    JOIN sales_orders so ON soi.order_id = so.id
                    JOIN products p ON soi.product_id = p.id
                    JOIN product_categories pc ON p.category_id = pc.id
                    WHERE so.order_date >= CURRENT_DATE - INTERVAL '180' DAY
                      AND so.status NOT IN ('draft', 'cancelled')
                    GROUP BY soi.product_id, p.sku, p.name, pc.name
                ),
                ranked_products AS (
                    SELECT
                        *,
                        ROW_NUMBER() OVER (ORDER BY total_sales_amount DESC) AS sales_rank,
                        SUM(sales_pct) OVER (ORDER BY total_sales_amount DESC
                            ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS cumulative_pct
                    FROM product_sales
                )
                SELECT sales_rank, sku, product_name, category_name
                FROM ranked_products
                """);
    }

    @Test
    void routineCursorJoinHasTheSameSemanticObservations() {
        assertConsistent("""
                CREATE OR REPLACE PROCEDURE sp_settle_consignment
                AS
                    CURSOR cur IS
                        SELECT ci.id, COALESCE(SUM(cc.consumed_qty), 0)
                        FROM consignment_inventory ci
                        LEFT JOIN consignment_consumptions cc ON ci.id = cc.consignment_id
                        GROUP BY ci.id;
                BEGIN
                    NULL;
                END;
                """);
    }

    @Test
    void castProjectionAcrossUnionHasTheSameSemanticObservations() {
        assertConsistent("""
                SELECT p.sku, CAST(NULL AS TIMESTAMP)
                FROM sales_return_items sri
                JOIN products p ON sri.product_id = p.id
                UNION ALL
                SELECT p.sku, CAST(NULL AS TIMESTAMP)
                FROM inventory_transactions it
                JOIN products p ON it.product_id = p.id
                """);
    }

    @Test
    void distinctOnCteHasTheSameSemanticObservations() {
        assertConsistent("""
                WITH latest_ma AS (
                    SELECT DISTINCT ON (warehouse_id)
                        warehouse_id
                    FROM sales_orders
                    ORDER BY warehouse_id, order_date DESC
                )
                SELECT w.name
                FROM latest_ma lm
                JOIN warehouses w ON lm.warehouse_id = w.id
                """);
    }

    @Test
    void nestedScalarSubqueriesInSetProjectionHaveTheSameSemanticObservations() {
        assertConsistent("""
                SELECT 'net profit',
                    ROUND(
                        (SELECT SUM(soi.amount - soi.quantity * p.purchase_price)
                         FROM sales_order_items soi
                         JOIN sales_orders so ON soi.order_id = so.id
                         JOIN products p ON soi.product_id = p.id)
                        - COALESCE((SELECT SUM(dri.loss_amount)
                                    FROM damage_report_items dri
                                    JOIN damage_reports dr ON dri.report_id = dr.id), 0)
                    , 2)
                FROM dual
                """);
    }

    @Test
    void unnamedCteSetProjectionStillTraversesNestedScalarSubqueries() {
        assertConsistent("""
                WITH waterfall_data AS (
                    SELECT 'gross profit' AS layer, SUM(soi.amount) AS amount
                    FROM sales_order_items soi
                    UNION ALL
                    SELECT 'net profit',
                        ROUND(
                            (SELECT SUM(soi.amount - soi.quantity * p.purchase_price)
                             FROM sales_order_items soi
                             JOIN sales_orders so ON soi.order_id = so.id
                             JOIN products p ON soi.product_id = p.id)
                            - COALESCE((SELECT SUM(dri.loss_amount)
                                        FROM damage_report_items dri
                                        JOIN damage_reports dr ON dri.report_id = dr.id), 0)
                        , 2)
                    FROM dual
                )
                SELECT layer, amount FROM waterfall_data
                """);
    }

    @Test
    void unqualifiedInProjectionIgnoresNestedScalarSubqueryRowsets() {
        assertConsistent("""
                SELECT po.order_no
                FROM purchase_orders po
                WHERE po.purchaser_id IN (
                    SELECT id
                    FROM employees
                    WHERE manager_id = (
                        SELECT manager_id FROM warehouses WHERE id = 1
                    )
                )
                """);
    }

    @Test
    void alterTableForeignKeyUsesTheSameConstraintLine() {
        String sql = """
                ALTER TABLE sales_returns ADD CONSTRAINT fk_sr_refund_voucher
                    FOREIGN KEY (refund_voucher_id) REFERENCES vouchers(id) ON DELETE SET NULL
                """;
        SqlStatementRecord statement = statement(sql);
        List<Long> tokenLines = foreignKeyLines(token.parseSql(statement, null));
        List<Long> fullLines = foreignKeyLines(full.parseSql(statement, null));

        assertFalse(tokenLines.isEmpty(), "The compact parser must emit a typed foreign key event");
        assertEquals(tokenLines, fullLines, () -> "token=" + tokenLines + " full=" + fullLines);
    }

    private void assertConsistent(String sql) {
        SqlStatementRecord statement = statement(sql);
        StructuredParseResult tokenResult = token.parseSql(statement, null);
        StructuredParseResult fullResult = full.parseSql(statement, null);
        List<SemanticObservationFingerprint> tokenObservations = observations(statement, tokenResult);
        List<SemanticObservationFingerprint> fullObservations = observations(statement, fullResult);

        assertFalse(tokenObservations.isEmpty() && fullObservations.isEmpty(),
                "At least one Oracle parser must produce typed observations");
        assertEquals(tokenObservations, fullObservations,
                () -> "token=" + tokenObservations + " tokenAttributes=" + tokenResult.attributes()
                        + " full=" + fullObservations + " fullAttributes=" + fullResult.attributes());
    }

    private List<SemanticObservationFingerprint> observations(
            SqlStatementRecord statement,
            StructuredParseResult structured
    ) {
        List<SemanticObservationFingerprint> observations = new ArrayList<>();
        new TokenEventRelationExtractor().extract(statement, structured).forEach(candidate ->
                observations.addAll(SemanticObservationFingerprint.relationships(candidate)));
        new StructuredDataLineageExtractor().extract(statement, structured).forEach(candidate ->
                observations.addAll(SemanticObservationFingerprint.lineages(candidate)));
        return observations.stream().sorted().toList();
    }

    private SqlStatementRecord statement(String sql) {
        return new SqlStatementRecord(sql, StatementSourceType.PLAIN_SQL,
                "oracle-observation-consistency.sql", 1, sql.lines().count(), Map.of());
    }

    private List<Long> foreignKeyLines(StructuredParseResult result) {
        return result.events().stream()
                .filter(event -> event.type() == StructuredParseEventType.DDL_FOREIGN_KEY)
                .map(event -> event.provenance().line())
                .toList();
    }
}
