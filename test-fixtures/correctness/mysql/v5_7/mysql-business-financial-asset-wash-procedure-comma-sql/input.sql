-- MySQL 5.7 semantic rewrite: JSON_TABLE/CTE/window input is represented by a physical staging table.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_financial_asset_wash_update
CREATE PROCEDURE sp_financial_asset_wash_update(
    IN p_max_limit_cap DECIMAL(16,4)
)
BEGIN
    UPDATE account_balances ab
    , (
        SELECT
            t.user_id,
            u.country_code,
            COUNT(DISTINCT t.currency) AS active_currencies,
            SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
            ROUND(AVG(t.amount), 2) AS avg_transaction_size,
            GROUP_CONCAT(DISTINCT t.merchant_category ORDER BY t.merchant_category SEPARATOR '; ') AS primary_categories,
            MAX(t.created_at) AS last_activity_time
        FROM asset_wash_input awi
        INNER JOIN transaction_ledgers t ON awi.input_user_id = t.user_id
        INNER JOIN users u ON u.id = t.user_id
        WHERE t.status = 'SUCCESS'
          AND t.created_at >= DATE_SUB(NOW(), INTERVAL 1 YEAR)
        GROUP BY t.user_id, u.country_code
        HAVING COUNT(t.id) >= 12 AND SUM(t.amount) > 5000.00
    ) risk_snapshot, global_compliance_policies gcp
    SET
        ab.compliance_notes = LOWER(CONCAT('Country: ', risk_snapshot.country_code, ' | Cats: ', risk_snapshot.primary_categories)),
        ab.adjusted_limit = LEAST(ab.max_credit_limit * 0.8, p_max_limit_cap),
        ab.last_evaluated_at = CURRENT_TIMESTAMP
    WHERE ab.user_id = risk_snapshot.user_id
      AND ab.region_code = gcp.region_code
      AND gcp.policy_status = 'ENFORCED'
      AND DATEDIFF(NOW(), risk_snapshot.last_activity_time) > 180;
END
-- relation-detector-fixture-end
