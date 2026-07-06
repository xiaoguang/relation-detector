-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers multiway outer joins, parenthesized join trees, derived joins,
-- legacy comma join mixed with explicit JOIN, and multiple ON equality keys.
SELECT *
FROM order_roots o
LEFT JOIN order_items oi
  ON oi.order_id = o.id
 AND oi.tenant_id = o.tenant_id
FULL OUTER JOIN shipments s
  ON s.order_id = o.id
FULL JOIN refunds r
  ON r.order_id = o.id
JOIN (
  SELECT p.order_id, p.invoice_id
  FROM payments p
  JOIN invoices i ON p.invoice_id = i.id
) pay ON pay.order_id = o.id;

SELECT *
FROM tenants t,
     orders o
JOIN users u ON o.user_id = u.id
WHERE o.tenant_id = t.id
  AND u.tenant_id = t.id;

SELECT *
FROM (orders o LEFT JOIN users u ON o.user_id = u.id)
LEFT JOIN (shipments s FULL JOIN carriers c ON s.carrier_id = c.id)
  ON s.order_id = o.id;
