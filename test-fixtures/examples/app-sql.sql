SELECT o.id, u.name
FROM orders o
JOIN users u ON o.user_id = u.id;

SELECT *
FROM users u, audit_logs l
WHERE l.action = 'LOGIN';

