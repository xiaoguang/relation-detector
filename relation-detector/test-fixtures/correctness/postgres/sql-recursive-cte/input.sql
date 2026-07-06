WITH RECURSIVE employee_paths(id, manager_id) AS (
  SELECT e.id, e.manager_id
  FROM employees e
  WHERE e.manager_id IS NULL
  UNION ALL
  SELECT e.id, e.manager_id
  FROM employees e
  JOIN employee_paths ep ON ep.id = e.manager_id
)
SELECT *
FROM employee_paths ep
JOIN employees manager ON ep.manager_id = manager.id;
