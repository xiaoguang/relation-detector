-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_non_recursive.inc
-- - MySQL 8.0 Reference Manual: WITH (Common Table Expressions)
-- The case focuses on CTE scope and lineage. CTE names must not be emitted as
-- physical tables.

WITH recent_orders AS (
  SELECT o.id, o.customer_id, o.region_id
  FROM orders AS o
  WHERE o.created_at >= '2026-01-01'
),
regional_orders AS (
  SELECT ro.id, ro.customer_id, r.id AS region_id
  FROM recent_orders AS ro
  JOIN regions AS r ON ro.region_id = r.id
),
customer_orders AS (
  SELECT regional_orders.id, c.account_id
  FROM regional_orders
  JOIN customers AS c ON regional_orders.customer_id = c.id
)
SELECT co.id, a.id
FROM customer_orders AS co
JOIN accounts AS a ON co.account_id = a.id;

SELECT audit.id, supplier.id
FROM (
  WITH supplier_orders AS (
    SELECT po.id, po.supplier_id
    FROM purchase_orders AS po
  ),
  approved_supplier_orders AS (
    SELECT so.id, so.supplier_id
    FROM supplier_orders AS so
    WHERE so.id IN (
      SELECT approval.purchase_order_id
      FROM purchase_order_approvals AS approval
    )
  )
  SELECT id, supplier_id
  FROM approved_supplier_orders
) AS audit
JOIN suppliers AS supplier ON audit.supplier_id = supplier.id;
