-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers extra parentheses, correlated EXISTS, tuple IN/NOT IN,
-- scalar subquery equality, and ANY/SOME/ALL boundaries.
SELECT *
FROM ((SELECT o.id, o.user_id FROM orders o)) projected_orders
JOIN users u ON projected_orders.user_id = u.id;

SELECT *
FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM shipments s
  WHERE s.order_id = o.id
);

SELECT *
FROM order_items oi
WHERE (oi.order_id, oi.product_id) IN (
  SELECT o.id, p.id
  FROM orders o
  JOIN products p ON p.category_id = o.category_id
);

SELECT *
FROM refunds r
WHERE r.order_id NOT IN (
  SELECT o.id FROM orders o WHERE o.status = 'CLOSED'
);

SELECT *
FROM invoices i
WHERE i.order_id = (SELECT max(o.id) FROM orders o WHERE o.customer_id = i.customer_id);

SELECT *
FROM payments p
WHERE p.order_id = ANY (SELECT o.id FROM orders o WHERE o.user_id = p.user_id)
   OR p.order_id = SOME (SELECT o.id FROM orders o WHERE o.user_id = p.user_id)
   OR p.order_id <> ALL (SELECT r.order_id FROM refunds r WHERE r.user_id = p.user_id);
