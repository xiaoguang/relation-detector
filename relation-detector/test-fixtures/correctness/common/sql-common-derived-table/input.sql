SELECT o.id, dc.id AS customer_id
FROM orders o
JOIN (
  SELECT c.id
  FROM customers c
) dc ON o.customer_id = dc.id;
