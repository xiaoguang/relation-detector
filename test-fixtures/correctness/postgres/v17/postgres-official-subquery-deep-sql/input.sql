-- PostgreSQL official regression/docs inspired: subselect.sql and Subquery
-- Expressions. Covers nested correlated EXISTS, tuple IN/NOT IN, row
-- constructor ANY/SOME/ALL, scalar subquery equality, and nested subquery joins.
SELECT *
FROM orders o
WHERE EXISTS (
  SELECT 1
  FROM users u
  WHERE u.id = o.user_id
    AND EXISTS (
      SELECT 1
      FROM accounts a
      WHERE a.id = u.account_id
    )
);

SELECT *
FROM order_items oi
WHERE (oi.order_id, oi.product_id) NOT IN (
  SELECT o.id, p.id
  FROM orders o
  JOIN products p ON p.category_id = o.category_id
);

SELECT *
FROM payments p
WHERE (p.order_id, p.user_id) = ANY (
  SELECT o.id, o.user_id
  FROM orders o
  WHERE o.user_id = p.user_id
)
OR p.order_id = SOME (
  SELECT r.order_id
  FROM refunds r
  WHERE r.user_id = p.user_id
)
OR p.order_id <> ALL (
  SELECT s.order_id
  FROM shipments s
  WHERE s.user_id = p.user_id
);

SELECT *
FROM invoices i
WHERE i.customer_id = (
  SELECT c.id
  FROM customers c
  WHERE c.external_ref = i.customer_ref
);

SELECT *
FROM (
  SELECT o.id, o.user_id
  FROM orders o
  WHERE EXISTS (
    SELECT 1 FROM payments p WHERE p.order_id = o.id
  )
) paid_orders
JOIN users u ON paid_orders.user_id = u.id;
