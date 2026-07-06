-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/t/derived.test
-- - MySQL 8.0 Reference Manual: Derived Tables and Lateral Derived Tables
-- Derived and lateral aliases are logical rowsets and must not be emitted as
-- physical tables.

SELECT o.id, projected.product_id
FROM orders AS o
JOIN LATERAL (
  SELECT li.order_id, p.id AS product_id
  FROM line_items AS li
  JOIN products AS p ON li.product_id = p.id
  WHERE li.order_id = o.id
) AS projected ON projected.order_id = o.id
JOIN products AS visible_product ON projected.product_id = visible_product.id;

SELECT outer_order.id, nested_projection.customer_id
FROM (
  SELECT o.id, o.customer_id
  FROM orders AS o
  JOIN (
    SELECT c.id AS customer_id, c.account_id
    FROM customers AS c
    JOIN accounts AS a ON c.account_id = a.id
  ) AS customer_projection ON o.customer_id = customer_projection.customer_id
) AS outer_order
JOIN (
  SELECT c.id AS customer_id
  FROM customers AS c
) AS nested_projection ON outer_order.customer_id = nested_projection.customer_id;
