SELECT *
FROM orders o
JOIN order_tags ot USING (order_id);
