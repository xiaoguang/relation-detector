UPDATE orders o
SET status = 'PAID'
FROM users u
WHERE o.user_id = u.id;
