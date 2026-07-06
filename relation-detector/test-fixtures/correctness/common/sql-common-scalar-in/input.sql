SELECT o.id
FROM orders o
WHERE o.customer_id IN (
  SELECT c.id
  FROM customers c
);
