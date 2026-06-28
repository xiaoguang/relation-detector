SELECT e.id, m.id AS manager_id
FROM employees e
JOIN employees m ON e.manager_id = m.id;
