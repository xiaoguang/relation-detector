UPDATE users u
LEFT JOIN (
    SELECT user_id, SUM(pay_amount) AS actual_total
    FROM orders
    WHERE order_status = 'PAID'
    GROUP BY user_id
) o_summary ON u.id = o_summary.user_id
SET
    u.total_spent = COALESCE(o_summary.actual_total, 0.00),
    u.level = CASE
        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
        ELSE 'REGULAR'
    END
WHERE u.is_active = 1;
