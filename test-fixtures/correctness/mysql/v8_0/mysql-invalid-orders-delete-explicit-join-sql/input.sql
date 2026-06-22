DELETE o, oi
FROM orders o
INNER JOIN order_items oi ON o.id = oi.order_id
INNER JOIN users u ON o.user_id = u.id
WHERE
    o.payment_status = 'UNPAID'
    AND o.created_at < NOW() - INTERVAL 7 DAY
    AND u.risk_level = 'HIGH';
