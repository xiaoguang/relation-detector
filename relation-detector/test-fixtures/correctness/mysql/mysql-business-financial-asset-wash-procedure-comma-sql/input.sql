-- MySQL business procedure case equivalent to mysql-business-financial-asset-wash-procedure-sql.
-- INNER JOIN portions are rewritten as comma rowsets plus WHERE equality predicates.
-- Expected fingerprints must match mysql-business-financial-asset-wash-procedure-sql.
-- relation-detector-fixture-source: PROCEDURE:finance.sp_financial_asset_wash_update_comma
CREATE PROCEDURE sp_financial_asset_wash_update_comma(
    IN p_input_ledger_json JSON,
    IN p_max_limit_cap NUMERIC(16,4)
)
BEGIN
    UPDATE account_balances ab,
    (
        WITH memory_input_cargo AS (
            SELECT
                jt.input_user_id,
                jt.input_amount,
                jt.input_currency
            FROM JSON_TABLE(
                p_input_ledger_json,
                '$[*]' COLUMNS(
                    input_user_id INT PATH '$.id',
                    input_amount NUMERIC(16,4) PATH '$.amt',
                    input_currency VARCHAR(10) PATH '$.cur'
                )
            ) AS jt
        ),
        user_financial_snapshot AS (
            SELECT
                t.user_id,
                COUNT(DISTINCT t.currency) AS active_currencies,
                SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
                ROUND(AVG(t.amount), 2) AS avg_transaction_size,
                GROUP_CONCAT(DISTINCT t.merchant_category ORDER BY t.merchant_category SEPARATOR '; ') AS primary_categories,
                MAX(t.created_at) AS last_activity_time
            FROM transaction_ledgers t
            WHERE t.status = 'SUCCESS'
              AND t.created_at >= NOW() - INTERVAL 1 YEAR
            GROUP BY t.user_id
            HAVING COUNT(t.id) >= 12 AND SUM(t.amount) > 5000.00
        ),
        dormant_risk_scores AS (
            SELECT
                u.id AS user_id,
                u.country_code,
                TIMESTAMPDIFF(DAY, snap.last_activity_time, NOW()) AS days_since_last_active,
                NTILE(10) OVER (PARTITION BY u.country_code ORDER BY snap.net_cash_flow DESC) AS wealth_tile
            FROM users u, user_financial_snapshot snap
            WHERE u.id = snap.user_id
              AND u.is_corporate = 0
        )
        SELECT
            drs.user_id,
            drs.country_code,
            drs.days_since_last_active,
            drs.wealth_tile,
            snap_main.primary_categories
        FROM dormant_risk_scores drs, user_financial_snapshot snap_main
        WHERE drs.user_id = snap_main.user_id
    ) AS drs_engine,
    global_compliance_policies gcp
    SET
        ab.risk_flags = JSON_ARRAY_APPEND(COALESCE(ab.risk_flags, JSON_ARRAY()), '$', 'POTENTIAL_DORMANT_WEALTH'),
        ab.compliance_notes = LOWER(CONCAT(
            'Country: ', drs_engine.country_code,
            ' | Idle Days: ', drs_engine.days_since_last_active,
            ' | Wealth Tier: ', drs_engine.wealth_tile,
            ' | Cats: ', drs_engine.primary_categories
        )),
        ab.adjusted_limit = LEAST(ab.max_credit_limit * 0.8, p_max_limit_cap),
        ab.last_evaluated_at = CURRENT_TIMESTAMP
    WHERE ab.user_id = drs_engine.user_id
      AND ab.region_code = gcp.region_code
      AND drs_engine.days_since_last_active > 180
      AND drs_engine.wealth_tile >= 8
      AND gcp.policy_status = 'ENFORCED'
      AND NOT JSON_CONTAINS(ab.risk_flags, '"EXEMPT"');
END
-- relation-detector-fixture-end
