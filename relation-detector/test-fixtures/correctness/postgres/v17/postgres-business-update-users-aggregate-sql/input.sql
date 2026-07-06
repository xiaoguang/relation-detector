-- PostgreSQL business case 2: UPDATE FROM aggregate derived table.
UPDATE users u
SET total_spent = COALESCE(o_summary.actual_total, 0.00),
    level = CASE
        WHEN o_summary.actual_total >= 10000 THEN 'VIP'
        WHEN o_summary.actual_total >= 5000 THEN 'GOLD'
        ELSE 'REGULAR'
    END
FROM (
    SELECT user_id, SUM(pay_amount) AS actual_total
    FROM orders
    WHERE order_status = 'PAID'
    GROUP BY user_id
) o_summary
WHERE u.id = o_summary.user_id
  AND u.is_active = 1;
