-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/derived.test
-- - mysql/mysql-server mysql-test/t/subquery_exists.test
-- - MySQL 5.7 Reference Manual: Subqueries and Derived Tables
-- Derived aliases must not be emitted as physical tables.

SELECT m2.id, d.pla_id
FROM materials AS m2
INNER JOIN (
  SELECT mp.pla_id, MIN(m1.material_code) AS material_code
  FROM material_places AS mp
  INNER JOIN materials AS m1 ON mp.material_id = m1.id
  GROUP BY mp.pla_id
) AS d ON d.material_code = m2.material_code;

SELECT a.id, latest.id
FROM accounts AS a
JOIN (
  SELECT l.account_id, MAX(l.id) AS id
  FROM account_logs AS l
  WHERE EXISTS (
    SELECT 1
    FROM log_permissions AS lp
    WHERE lp.log_id = l.id
  )
  GROUP BY l.account_id
) AS latest ON latest.account_id = a.id;

SELECT o.id, c.id
FROM orders AS o
JOIN customers AS c ON o.customer_id = c.id
WHERE o.id IN (
  SELECT shipment.order_id
  FROM shipments AS shipment
  WHERE shipment.customer_id = c.id
);

SELECT outer_order.id, nested_customer.id
FROM (
  SELECT o.id, o.customer_id
  FROM orders AS o
  WHERE o.customer_id = (
    SELECT c.id
    FROM customers AS c
    WHERE c.id = o.customer_id
  )
) AS outer_order
JOIN (
  SELECT c.id
  FROM customers AS c
) AS nested_customer ON outer_order.customer_id = nested_customer.id;
