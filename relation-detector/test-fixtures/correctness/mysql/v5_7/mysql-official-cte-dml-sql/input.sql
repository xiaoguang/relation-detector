-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_non_recursive.inc
-- - MySQL 5.7 Reference Manual: UPDATE, DELETE, derived tables
-- MySQL 5.7 does not support CTE syntax, so this fixture keeps the same
-- relation shape using derived tables.

UPDATE orders AS target
JOIN (
  SELECT o.id AS order_id, o.user_id
  FROM orders AS o
  JOIN users AS u ON o.user_id = u.id
) AS co ON target.id = co.order_id
JOIN (
  SELECT co_inner.user_id, u.account_id
  FROM (
    SELECT o.id AS order_id, o.user_id
    FROM orders AS o
    JOIN users AS u0 ON o.user_id = u0.id
  ) AS co_inner
  JOIN users AS u ON co_inner.user_id = u.id
) AS ca ON co.user_id = ca.user_id
JOIN accounts AS a ON ca.account_id = a.id
SET target.audit_account_id = a.id;

DELETE o
FROM orders AS o
JOIN (
  SELECT o_inner.id AS order_id, o_inner.customer_id
  FROM orders AS o_inner
  JOIN customers AS c_inner ON o_inner.customer_id = c_inner.id
) AS ro ON o.id = ro.order_id
JOIN customers AS c ON ro.customer_id = c.id
WHERE c.archived = 1;
