-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/subquery_exists.test
-- - MySQL 8.0 Reference Manual: Subqueries
-- This fixture stresses row/tuple subqueries, ANY/SOME/ALL, correlated
-- subqueries, and scalar subquery equality.

SELECT o.id
FROM orders AS o
WHERE (o.customer_id, o.region_id) IN (
  SELECT c.id, c.region_id
  FROM customers AS c
  JOIN regions AS r ON c.region_id = r.id
);

SELECT i.id
FROM invoices AS i
WHERE i.customer_id = ANY (
  SELECT c.id
  FROM customers AS c
  WHERE c.account_id IN (
    SELECT a.id
    FROM accounts AS a
  )
);

SELECT p.id
FROM payments AS p
WHERE p.order_id = (
  SELECT o.id
  FROM orders AS o
  WHERE o.id = p.order_id
    AND o.user_id = SOME (
      SELECT u.id
      FROM users AS u
    )
);

SELECT s.id
FROM shipments AS s
WHERE s.carrier_id <> ALL (
  SELECT blocked.carrier_id
  FROM blocked_carriers AS blocked
  WHERE blocked.region_id = s.region_id
);
