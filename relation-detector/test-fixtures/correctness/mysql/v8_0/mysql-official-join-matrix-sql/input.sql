-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/join.test
-- - MySQL 8.0 Reference Manual: JOIN Clause
-- This file keeps only standalone SQL statements that exercise relation extraction.

SELECT o.id, oi.order_id
FROM orders AS o
JOIN order_items AS oi ON o.id = oi.order_id;

SELECT o.id, c.id
FROM orders AS o
INNER JOIN customers AS c ON o.customer_id = c.id;

SELECT o.id, w.id
FROM orders AS o
CROSS JOIN warehouses AS w
WHERE o.warehouse_id = w.id
  AND o.status = 'READY';

SELECT s.id, c.id
FROM shipments AS s
LEFT JOIN carriers AS c ON s.carrier_id = c.id
WHERE c.active_flag = 1;

SELECT r.id, o.id
FROM returns AS r
RIGHT JOIN orders AS o ON r.order_id = o.id
WHERE r.status = 'OPEN';

SELECT o.order_id, oa.order_id
FROM orders AS o
JOIN order_audit AS oa USING (order_id);

SELECT i.id, p.id, c.id
FROM invoices AS i, payments AS p, customers AS c
WHERE i.payment_id = p.id
  AND i.customer_id = c.id
  AND i.status = 'PAID'
  AND p.amount > 0;
