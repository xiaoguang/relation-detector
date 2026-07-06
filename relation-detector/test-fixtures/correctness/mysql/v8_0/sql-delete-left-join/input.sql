DELETE FROM o
USING orders AS o
LEFT JOIN users AS u ON o.user_id = u.id
WHERE u.id IS NULL;
