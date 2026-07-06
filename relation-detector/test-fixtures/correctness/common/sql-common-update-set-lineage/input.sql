UPDATE customer_rollup cr
SET total_amount = cr.total_amount + (
  SELECT o.total_amount
  FROM orders o
  WHERE o.customer_id = cr.customer_id
);
