-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_non_recursive.inc
-- - MySQL 8.0 Reference Manual: WITH (Common Table Expressions), UPDATE, DELETE
-- CTE rowsets are logical rowsets. They must not be emitted as physical tables
-- even when the CTE feeds UPDATE or DELETE.

WITH candidate_orders(order_id, user_id) AS (
  SELECT o.id, o.user_id
  FROM orders AS o
  JOIN users AS u ON o.user_id = u.id
),
candidate_accounts(user_id, account_id) AS (
  SELECT co.user_id, u.account_id
  FROM candidate_orders AS co
  JOIN users AS u ON co.user_id = u.id
)
UPDATE orders AS target
JOIN candidate_orders AS co ON target.id = co.order_id
JOIN candidate_accounts AS ca ON co.user_id = ca.user_id
JOIN accounts AS a ON ca.account_id = a.id
SET target.audit_account_id = a.id;

WITH removable_orders(order_id, customer_id) AS (
  SELECT o.id, o.customer_id
  FROM orders AS o
  JOIN customers AS c ON o.customer_id = c.id
)
DELETE o
FROM orders AS o
JOIN removable_orders AS ro ON o.id = ro.order_id
JOIN customers AS c ON ro.customer_id = c.id
WHERE c.archived = 1;
