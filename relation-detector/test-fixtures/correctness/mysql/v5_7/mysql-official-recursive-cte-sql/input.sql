-- MySQL official-test inspired fixture.
-- Sources:
-- - mysql/mysql-server mysql-test/include/with_recursive.inc
-- - MySQL 5.7 Reference Manual: JOIN Clause
-- MySQL 5.7 does not support recursive CTE syntax, so this fixture keeps the
-- same parent-child relationship checks as direct self joins.

SELECT child.id, mgr.id
FROM employees AS child
LEFT JOIN employees AS mgr ON child.manager_id = mgr.id;

SELECT child.id, parent.id
FROM categories AS child
JOIN categories AS parent ON child.parent_id = parent.id;
