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
