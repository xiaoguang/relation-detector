-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/join.test
-- - MySQL 8.0 Reference Manual: JOIN Clause
-- This file focuses on MySQL-specific join forms that are easy to regress:
-- STRAIGHT_JOIN, NATURAL JOIN, nested parenthesized joins, index hints, and
-- ODBC escaped outer joins.

SELECT o.id, u.id
FROM orders AS o
STRAIGHT_JOIN users AS u ON o.user_id = u.id;

SELECT os.order_id, sl.status_code
FROM order_status AS os
NATURAL JOIN status_labels AS sl;

SELECT o.id, oi.id, p.id
FROM orders AS o
JOIN (order_items AS oi JOIN products AS p ON oi.product_id = p.id)
  ON o.id = oi.order_id;

SELECT o.id, u.id
FROM orders AS o FORCE INDEX (idx_orders_user)
JOIN users AS u USE INDEX (PRIMARY)
  ON o.user_id = u.id;

SELECT o.id, c.id
FROM { OJ orders AS o LEFT OUTER JOIN customers AS c ON o.customer_id = c.id };
