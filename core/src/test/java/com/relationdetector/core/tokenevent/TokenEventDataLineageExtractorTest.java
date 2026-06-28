package com.relationdetector.core.tokenevent;

import com.relationdetector.core.parse.SqlDialect;
import com.relationdetector.core.lineage.*;
import com.relationdetector.core.relation.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.relationdetector.contracts.model.DataLineageCandidate;
import com.relationdetector.contracts.parse.SqlStatementRecord;
import com.relationdetector.contracts.Enums.StatementSourceType;

class TokenEventDataLineageExtractorTest {
    private final TokenEventDataLineageExtractor extractor = new TokenEventDataLineageExtractor();

    @Test
    void extractsAggregateLineageThroughDerivedUpdate() {
        String sql = """
                UPDATE users u
                LEFT JOIN (
                    SELECT user_id, SUM(pay_amount) AS actual_total
                    FROM orders
                    GROUP BY user_id
                ) o_summary ON u.id = o_summary.user_id
                SET u.total_spent = COALESCE(o_summary.actual_total, 0.00)
                """;

        assertEquals(List.of("VALUE:AGGREGATE:orders.pay_amount->users.total_spent"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void extractsCumulativeLineageThroughDerivedUpdate() {
        String sql = """
                SET @running_sum = 0;
                SELECT SUM(weight) INTO v_total_w FROM jsh_temp_org_pdf;
                UPDATE jsh_temp_org_pdf t
                INNER JOIN (
                    SELECT org_id,
                           (@running_sum := @running_sum + weight) / v_total_w AS new_cdf
                    FROM jsh_temp_org_pdf
                    ORDER BY org_id ASC
                ) calculated ON t.org_id = calculated.org_id
                SET t.cdf_end = calculated.new_cdf
                """;

        assertEquals(List.of("VALUE:CUMULATIVE:jsh_temp_org_pdf.weight"
                        + "->jsh_temp_org_pdf.cdf_end"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void doesNotBindParameterWriteBackToFilteredSourceColumn() {
        String sql = """
                INSERT INTO biz_sync_progress(jobId, tenantId, status)
                SELECT 1001, p_tenant_id, 'RUNNING'
                FROM jsh_depot_item di
                WHERE di.tenant_id = p_tenant_id
                """;

        assertTrue(lineageFingerprints(sql, SqlDialect.MYSQL).stream()
                .noneMatch(fingerprint -> fingerprint.contains("jsh_depot_item.tenant_id->biz_sync_progress.tenantId")));
    }

    @Test
    void doesNotJoinInsertValuesWithLaterSelectStatement() {
        String sql = """
                INSERT INTO biz_sync_progress(jobId, tenantId, status)
                VALUES (v_job_id, p_tenant_id, 'RUNNING');

                INSERT INTO biz_bill_item_fact_new (tenantId, sourceOrderId)
                SELECT di.tenant_id, dh.id
                FROM jsh_depot_item di
                JOIN jsh_depot_head dh ON dh.id = di.header_id
                """;

        assertTrue(lineageFingerprints(sql, SqlDialect.MYSQL).stream()
                .noneMatch(fingerprint -> fingerprint.contains("->biz_sync_progress.")));
    }

    @Test
    void extractsEveryInsertSelectStatementInsideObjectBlock() {
        String sql = """
                CREATE PROCEDURE rebuild_procurement_snapshots()
                BEGIN
                    INSERT INTO purchase_order_items_snapshot (material_id, requested_qty)
                    SELECT ri.material_id, ri.requested_qty
                    FROM purchase_requisition_items ri;

                    INSERT INTO inbound_items_snapshot (material_id, actual_qty)
                    SELECT poi.material_id, poi.requested_qty
                    FROM purchase_order_items poi;
                END
                """;

        assertEquals(List.of(
                        "VALUE:DIRECT:purchase_requisition_items.material_id"
                                + "->purchase_order_items_snapshot.material_id",
                        "VALUE:DIRECT:purchase_requisition_items.requested_qty"
                                + "->purchase_order_items_snapshot.requested_qty",
                        "VALUE:DIRECT:purchase_order_items.material_id->inbound_items_snapshot.material_id",
                        "VALUE:DIRECT:purchase_order_items.requested_qty->inbound_items_snapshot.actual_qty"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void extractsEveryUpdateStatementInsideObjectBlock() {
        String sql = """
                CREATE PROCEDURE refresh_operational_rollups()
                BEGIN
                    UPDATE order_headers oh
                    JOIN customer_accounts ca ON ca.id = oh.customer_id
                    SET oh.risk_level = ca.risk_level;

                    UPDATE inventory_items ii
                    JOIN supplier_prices sp ON sp.sku = ii.sku
                    SET ii.last_cost = sp.unit_cost,
                        ii.stock_value = ii.quantity * sp.unit_cost;
                END
                """;

        assertEquals(List.of(
                        "VALUE:DIRECT:customer_accounts.risk_level->order_headers.risk_level",
                        "VALUE:DIRECT:supplier_prices.unit_cost->inventory_items.last_cost",
                        "VALUE:ARITHMETIC:inventory_items.quantity,supplier_prices.unit_cost"
                                + "->inventory_items.stock_value"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void extractsAllPhysicalSourcesFromCaseInsertProjection() {
        String sql = """
                INSERT INTO biz_bill_item_fact_new (purchaseApplyLinkNo)
                SELECT
                    CASE
                      WHEN dh.sub_type = '采购订单' THEN dh.link_apply
                      WHEN dh.sub_type = '采购' THEN po.link_apply
                      ELSE NULL
                    END
                FROM jsh_depot_head dh
                LEFT JOIN jsh_depot_head po ON po.number = dh.link_number
                """;

        assertEquals(List.of(
                        "CONTROL:CASE_WHEN:jsh_depot_head.sub_type,jsh_depot_head.link_apply"
                                + "->biz_bill_item_fact_new.purchaseApplyLinkNo"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void caseProjectionSourcesDoNotSwallowNonCaseSubqueryColumns() {
        String sql = """
                INSERT INTO jsh_temp_org_pdf (weight)
                SELECT
                    ROUND(
                        (CASE WHEN jo.org_no LIKE '%-HD-%' THEN 100 ELSE 50 END)
                        * (1 + (
                            SELECT COUNT(*)
                            FROM jsh_organization sub
                            WHERE sub.tenant_id = jo.tenant_id
                              AND sub.id <> jo.id
                        ))
                        * (CASE WHEN jo.org_abr LIKE '%旗舰店%' THEN 2.5 ELSE 1.0 END)
                    )
                FROM jsh_organization jo
                """;

        assertEquals(List.of(
                        "CONTROL:CASE_WHEN:jsh_organization.org_no->jsh_temp_org_pdf.weight",
                        "CONTROL:CASE_WHEN:jsh_organization.org_abr->jsh_temp_org_pdf.weight"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void propagatesCumulativeTransformThroughDerivedInsertProjection() {
        String sql = """
                SET @running_h_sum := 0;
                INSERT INTO jsh_temp_mock_plan (mock_timestamp_str)
                SELECT rand_tbl.mock_time
                FROM (
                    SELECT CONCAT(
                        LPAD((SELECT h.hour_val
                              FROM (
                                  SELECT hour_val, (@running_h_sum := @running_h_sum + weight) AS h_cdf
                                  FROM jsh_temp_hour_pdf
                                  ORDER BY hour_val ASC
                              ) h
                              WHERE h.h_cdf >= 1
                              ORDER BY h.h_cdf ASC LIMIT 1), 2, '0')
                    ) AS mock_time
                ) rand_tbl
                """;

        assertEquals(List.of(
                        "VALUE:CUMULATIVE:jsh_temp_hour_pdf.hour_val,jsh_temp_hour_pdf.weight"
                                + "->jsh_temp_mock_plan.mock_timestamp_str"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void doesNotTreatOnDuplicateKeyUpdateAsStandaloneUpdateStatement() {
        String sql = """
                INSERT INTO customer_risk_snapshot (customer_id, risk_level)
                SELECT c.id, c.risk_level
                FROM customers c
                ON DUPLICATE KEY UPDATE risk_level = VALUES(risk_level)
                """;

        assertEquals(List.of(
                        "VALUE:DIRECT:customers.id->customer_risk_snapshot.customer_id",
                        "VALUE:DIRECT:customers.risk_level->customer_risk_snapshot.risk_level"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void extractsInsertSelectLineageByTargetColumnPosition() {
        String sql = """
                INSERT INTO user_spending_snapshots (user_id, total_spent)
                SELECT u.id, SUM(o.pay_amount)
                FROM users u
                JOIN orders o ON o.user_id = u.id
                GROUP BY u.id
                """;

        assertEquals(List.of(
                        "VALUE:DIRECT:users.id->user_spending_snapshots.user_id",
                        "VALUE:AGGREGATE:orders.pay_amount->user_spending_snapshots.total_spent"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void extractsAllInsertSelectSourcesFromArithmeticProjection() {
        String sql = """
                INSERT INTO inventory_transactions (quantity_change)
                SELECT sti.counted_quantity - i.quantity
                FROM stocktake_items sti
                JOIN inventory i ON i.product_id = sti.product_id
                """;

        assertEquals(List.of(
                        "VALUE:ARITHMETIC:stocktake_items.counted_quantity,inventory.quantity->inventory_transactions.quantity_change"),
                lineageFingerprints(sql, SqlDialect.MYSQL));
    }

    @Test
    void extractsMergeInsertValuesLineage() {
        String sql = """
                MERGE INTO target_orders AS t
                USING source_orders AS s
                ON t.source_order_id = s.id
                WHEN NOT MATCHED THEN
                  INSERT (source_order_id) VALUES (s.id)
                """;

        assertEquals(List.of("VALUE:DIRECT:source_orders.id->target_orders.source_order_id"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void extractsMergeLineageInsideDataModifyingCte() {
        String sql = """
                WITH update_batch AS (
                    SELECT pu.sku, pu.new_price
                    FROM pg17_price_updates pu
                    WHERE pu.processed = false
                ),
                merge_results AS (
                    MERGE INTO pg17_product_catalog pc
                    USING update_batch ub
                    ON pc.sku = ub.sku
                    WHEN MATCHED THEN
                        UPDATE SET current_price = ub.new_price
                    RETURNING pc.sku
                )
                SELECT mr.sku
                FROM merge_results mr
                """;

        List<String> fingerprints = lineageFingerprints(sql, SqlDialect.POSTGRES);
        assertTrue(fingerprints.contains(
                "VALUE:DIRECT:pg17_price_updates.new_price->pg17_product_catalog.current_price"),
                () -> fingerprints.toString());
    }

    @Test
    void extractsPostgresUpdateFromLineageForUnqualifiedTargets() {
        String sql = """
                UPDATE orders o
                SET customer_country = c.country_code,
                    total_amount = o.total_amount + oi.extended_amount,
                    risk_note = concat(c.risk_level, ':', o.status)
                FROM customers c
                JOIN order_items oi ON oi.order_id = o.id
                WHERE o.customer_id = c.id
                  AND EXISTS (
                      SELECT 1
                      FROM payments p
                      WHERE p.order_id = o.id
                  )
                """;

        assertEquals(List.of(
                        "VALUE:DIRECT:customers.country_code->orders.customer_country",
                        "VALUE:ARITHMETIC:orders.total_amount,order_items.extended_amount->orders.total_amount",
                        "VALUE:CONCAT_FORMAT:customers.risk_level,orders.status->orders.risk_note"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void scalarAggregateSubqueryLineageUsesValueExpressionSourcesOnly() {
        String sql = """
                UPDATE users u
                SET total_spent = COALESCE((
                        SELECT SUM(o.pay_amount)
                        FROM orders o
                        WHERE o.user_id = u.id
                          AND o.order_status = 'PAID'
                    ), 0.00),
                    level = CASE
                        WHEN COALESCE((
                            SELECT SUM(o.pay_amount)
                            FROM orders o
                            WHERE o.user_id = u.id
                              AND o.order_status = 'PAID'
                        ), 0.00) >= 10000 THEN 'VIP'
                        ELSE 'REGULAR'
                    END
                WHERE u.is_active = 1
                """;

        assertEquals(List.of(
                        "VALUE:AGGREGATE:orders.pay_amount->users.total_spent",
                        "CONTROL:CASE_WHEN:orders.pay_amount->users.level"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void postgresConcatOperatorProducesConcatFormatLineage() {
        String sql = """
                UPDATE order_ledgers l
                SET remarks = 'User risk level: ' || u.risk_level || ' | Order Rank: ' || fo.rnk
                FROM fraud_orders fo, users u
                WHERE l.order_id = fo.order_id
                  AND fo.user_id = u.id
                """;

        assertEquals(List.of("VALUE:CONCAT_FORMAT:users.risk_level,fraud_orders.rnk->order_ledgers.remarks"),
                lineageFingerprints(sql, SqlDialect.POSTGRES));
    }

    @Test
    void skipsLineageWhoseTargetIsExplicitProcedureLocalTemporaryTable() {
        String sql = """
                CREATE PROCEDURE rebuild_tmp()
                BEGIN
                    CREATE TEMPORARY TABLE tmp_rollup (order_amount DECIMAL(10,2));
                    INSERT INTO tmp_rollup (order_amount)
                    SELECT o.amount
                    FROM orders o;
                END
                """;

        assertTrue(lineageFingerprints(sql, SqlDialect.MYSQL).isEmpty());
    }

    @Test
    void skipsLineageWhoseSourceIsExplicitProcedureLocalTemporaryTable() {
        String sql = """
                CREATE PROCEDURE flush_tmp()
                BEGIN
                    CREATE TEMPORARY TABLE tmp_rollup (order_amount DECIMAL(10,2));
                    INSERT INTO tmp_rollup (order_amount)
                    SELECT o.amount
                    FROM orders o;

                    INSERT INTO order_facts (amount)
                    SELECT order_amount
                    FROM tmp_rollup;
                END
                """;

        assertTrue(lineageFingerprints(sql, SqlDialect.MYSQL).isEmpty());
    }

    @Test
    void skipsLineageWhoseSourceIsKnownProcedureLocalTemporaryTableFromStatementContext() {
        String sql = """
                INSERT INTO order_facts (amount)
                SELECT order_amount
                FROM tmp_rollup
                """;
        SqlStatementRecord statement = new SqlStatementRecord(
                sql,
                StatementSourceType.PROCEDURE,
                "procedure-with-temp-context.sql",
                1,
                1,
                Map.of("localTempTables", List.of("tmp_rollup")));

        List<String> fingerprints = extractor.extract(
                        statement,
                        new TokenEventStructuredSqlParser(SqlDialect.MYSQL).parseSql(statement, null))
                .stream()
                .map(TokenEventDataLineageExtractorTest::fingerprint)
                .toList();

        assertTrue(fingerprints.isEmpty(), fingerprints::toString);
    }

    private List<String> lineageFingerprints(String sql, SqlDialect dialect) {
        SqlStatementRecord statement = new SqlStatementRecord(
                sql,
                StatementSourceType.PLAIN_SQL,
                "token-event-lineage-unit.sql",
                1,
                1,
                Map.of());
        return extractor.extract(statement, new TokenEventStructuredSqlParser(dialect).parseSql(statement, null))
                .stream()
                .map(TokenEventDataLineageExtractorTest::fingerprint)
                .toList();
    }

    private static String fingerprint(DataLineageCandidate candidate) {
        return candidate.flowKind() + ":"
                + candidate.transformType() + ":"
                + candidate.sources().stream()
                        .map(com.relationdetector.contracts.model.Endpoint::displayName)
                        .collect(Collectors.joining(","))
                + "->" + candidate.target().displayName();
    }
}
