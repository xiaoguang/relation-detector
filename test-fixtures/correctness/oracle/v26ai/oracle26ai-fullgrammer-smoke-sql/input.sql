INSERT INTO sales_summary (customer_id, total_amount)
SELECT c.id, SUM(o.amount)
FROM customers c
JOIN orders o ON o.customer_id = c.id
GROUP BY c.id;
