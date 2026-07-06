-- PostgreSQL business case 10: financial CTE update with aggregate CTEs and comma rowsets.
-- Future data-lineage boundary: merchant_category, country_code, and activity dates contribute to compliance_notes.
WITH user_financial_snapshot AS (
    SELECT
        t.user_id,
        COUNT(DISTINCT t.currency) AS active_currencies,
        SUM(CASE WHEN t.direction = 'INFLOW' THEN t.amount ELSE -t.amount END) AS net_cash_flow,
        ROUND(AVG(t.amount)::numeric, 2) AS avg_transaction_size,
        STRING_AGG(DISTINCT t.merchant_category, '; ' ORDER BY t.merchant_category) AS primary_categories,
        MAX(t.created_at) AS last_activity_time
    FROM transaction_ledgers t
    WHERE t.status = 'SUCCESS'
      AND t.created_at >= NOW() - INTERVAL '1 year'
    GROUP BY t.user_id
    HAVING COUNT(t.id) >= 12 AND SUM(t.amount) > 5000.00
),
dormant_risk_scores AS (
    SELECT
        u.id AS user_id,
        u.country_code,
        EXTRACT(DAY FROM AGE(NOW(), snap.last_activity_time)) AS days_since_last_active,
        NTILE(10) OVER (PARTITION BY u.country_code ORDER BY snap.net_cash_flow DESC) AS wealth_tile
    FROM users u
    INNER JOIN user_financial_snapshot snap ON u.id = snap.user_id
    WHERE u.is_corporate = false
)
UPDATE account_balances ab
SET risk_flags = ARRAY_APPEND(ab.risk_flags, 'POTENTIAL_DORMANT_WEALTH'),
    compliance_notes = LOWER(FORMAT('Country: %s | Idle Days: %s | Wealth Tier: %s | Cats: %s',
                             drs.country_code,
                             drs.days_since_last_active::text,
                             drs.wealth_tile::text,
                             snap_main.primary_categories)),
    adjusted_limit = LEAST(ab.max_credit_limit * 0.8, 50000.00),
    last_evaluated_at = CURRENT_TIMESTAMP
FROM dormant_risk_scores drs, user_financial_snapshot snap_main, global_compliance_policies gcp
WHERE ab.user_id = drs.user_id
  AND drs.user_id = snap_main.user_id
  AND ab.region_code = gcp.region_code
  AND drs.days_since_last_active > 180
  AND drs.wealth_tile >= 8
  AND gcp.policy_status = 'ENFORCED'
  AND NOT (ab.risk_flags @> ARRAY['EXEMPT']);
