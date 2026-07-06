-- PostgreSQL official regression/docs inspired: with.sql and WITH Queries.
-- Covers nested WITH, recursive CTEs, MATERIALIZED / NOT MATERIALIZED, and CTE
-- reuse without promoting CTE names to physical tables.
WITH base_orders AS MATERIALIZED (
  SELECT o.id AS order_id, o.user_id, o.customer_id
  FROM orders o
  JOIN users u ON o.user_id = u.id
),
customer_orders AS NOT MATERIALIZED (
  WITH customer_regions AS (
    SELECT c.id AS customer_id, c.region_id
    FROM customers c
    JOIN regions r ON c.region_id = r.id
  )
  SELECT bo.order_id, bo.user_id, cr.region_id
  FROM base_orders bo
  JOIN customer_regions cr ON bo.customer_id = cr.customer_id
)
SELECT *
FROM customer_orders co1
JOIN customer_orders co2 ON co1.user_id = co2.user_id
JOIN audit_events ae ON ae.order_id = co1.order_id;

WITH RECURSIVE department_tree(id, parent_department) AS (
  SELECT d.id, d.parent_department
  FROM departments d
  WHERE d.parent_department IS NULL
  UNION ALL
  SELECT child.id, child.parent_department
  FROM departments child
  JOIN department_tree parent ON child.parent_department = parent.id
)
SELECT *
FROM department_tree dt
JOIN departments d ON dt.parent_department = d.id;
