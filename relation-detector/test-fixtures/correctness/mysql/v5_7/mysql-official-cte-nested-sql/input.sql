-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_non_recursive.inc
-- - MySQL 5.7 Reference Manual: derived tables
-- MySQL 5.7 does not support CTE syntax, so nested CTEs are rewritten as
-- nested derived tables with equivalent join predicates.

SELECT co.id, a.id
FROM (
  SELECT regional_orders.id, c.account_id
  FROM (
    SELECT ro.id, ro.customer_id, r.id AS region_id
    FROM (
      SELECT o.id, o.customer_id, o.region_id
      FROM orders AS o
      WHERE o.created_at >= '2026-01-01'
    ) AS ro
    JOIN regions AS r ON ro.region_id = r.id
  ) AS regional_orders
  JOIN customers AS c ON regional_orders.customer_id = c.id
) AS co
JOIN accounts AS a ON co.account_id = a.id;

SELECT audit.id, supplier.id
FROM (
  SELECT so.id, so.supplier_id
  FROM (
    SELECT po.id, po.supplier_id
    FROM purchase_orders AS po
  ) AS so
  WHERE so.id IN (
    SELECT approval.purchase_order_id
    FROM purchase_order_approvals AS approval
  )
) AS audit
JOIN suppliers AS supplier ON audit.supplier_id = supplier.id;
