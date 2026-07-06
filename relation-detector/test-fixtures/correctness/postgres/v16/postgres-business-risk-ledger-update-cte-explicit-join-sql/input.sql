-- PostgreSQL business case 7 equivalent: explicit INNER JOIN version of the final CTE rowsets.
WITH active_users AS (
    SELECT id, risk_level
    FROM users
    WHERE status = 'ACTIVE' AND risk_level IN ('HIGH', 'MEDIUM')
),
fraud_orders AS (
    SELECT
        o.id AS order_id,
        o.user_id,
        ROW_NUMBER() OVER (PARTITION BY o.user_id ORDER BY o.amount DESC) AS rnk
    FROM orders o
    INNER JOIN active_users au ON o.user_id = au.id
    WHERE o.created_at >= NOW() - INTERVAL '30 days'
)
UPDATE order_ledgers l
SET ledger_status = 'SUSPENDED',
    remarks = 'User risk level: ' || u.risk_level || ' | Order Rank: ' || fo.rnk
FROM fraud_orders fo
INNER JOIN users u ON fo.user_id = u.id
WHERE l.order_id = fo.order_id
  AND fo.rnk <= 3
  AND l.reconciliation_status = 'UNVERIFIED';
