DELETE o, oi
FROM orders o, order_items oi, users u
WHERE
    o.id = oi.order_id
    AND o.user_id = u.id
    AND o.payment_status = 'UNPAID'
    AND o.created_at < NOW() - INTERVAL 7 DAY
    AND u.risk_level = 'HIGH';
