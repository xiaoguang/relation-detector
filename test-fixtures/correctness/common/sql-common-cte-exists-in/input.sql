WITH active_customers AS (
  SELECT c.id, c.region_id
  FROM customers c
  WHERE EXISTS (
    SELECT 1
    FROM customer_flags f
    WHERE f.customer_id = c.id
  )
)
SELECT o.id, ac.region_id
FROM orders o
JOIN active_customers ac ON o.customer_id = ac.id
WHERE o.sales_rep_id IN (
  SELECT s.id
  FROM sales_reps s
);
