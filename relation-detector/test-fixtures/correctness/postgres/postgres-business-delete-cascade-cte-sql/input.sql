-- PostgreSQL business case 3: data-modifying CTE cascade delete.
WITH deleted_orders AS (
    DELETE FROM orders o
    USING users u
    WHERE o.user_id = u.id
      AND o.payment_status = 'UNPAID'
      AND u.risk_level = 'HIGH'
    RETURNING o.id
)
DELETE FROM order_items oi
USING deleted_orders deleted_order_rows
WHERE oi.order_id = deleted_order_rows.id;
