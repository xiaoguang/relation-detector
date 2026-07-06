INSERT INTO customer_rollup (customer_id, total_amount)
SELECT o.customer_id, o.total_amount
FROM orders o;
