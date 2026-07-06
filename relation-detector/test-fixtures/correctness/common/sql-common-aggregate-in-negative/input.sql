SELECT o.customer_id
FROM orders o
WHERE o.total_amount IN (
  SELECT SUM(p.amount)
  FROM payments p
);
