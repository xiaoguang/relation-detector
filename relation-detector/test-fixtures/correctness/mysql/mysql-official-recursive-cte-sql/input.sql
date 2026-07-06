-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_recursive.inc
-- - MySQL 8.0 Reference Manual: WITH (Common Table Expressions)
-- Recursive CTE rowsets are logical rowsets; they must not be emitted as
-- physical tables.

WITH RECURSIVE employee_tree AS (
  SELECT e.id, e.manager_id
  FROM employees AS e
  WHERE e.manager_id IS NULL
  UNION ALL
  SELECT child.id, child.manager_id
  FROM employees AS child
  JOIN employee_tree AS parent ON child.manager_id = parent.id
)
SELECT et.id, mgr.id
FROM employee_tree AS et
LEFT JOIN employees AS mgr ON et.manager_id = mgr.id;

WITH RECURSIVE category_path AS (
  SELECT c.id, c.parent_id
  FROM categories AS c
  WHERE c.parent_id IS NULL
  UNION ALL
  SELECT child.id, child.parent_id
  FROM categories AS child
  JOIN category_path AS parent ON child.parent_id = parent.id
)
SELECT cp.id, p.id
FROM category_path AS cp
JOIN categories AS p ON cp.parent_id = p.id;
