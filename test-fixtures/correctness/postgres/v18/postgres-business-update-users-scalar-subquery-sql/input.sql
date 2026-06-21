-- PostgreSQL business case 2 equivalent: aggregate relation expressed through correlated scalar subqueries.
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
        WHEN COALESCE((
            SELECT SUM(o.pay_amount)
            FROM orders o
            WHERE o.user_id = u.id
              AND o.order_status = 'PAID'
        ), 0.00) >= 5000 THEN 'GOLD'
        ELSE 'REGULAR'
    END
WHERE u.is_active = 1;
