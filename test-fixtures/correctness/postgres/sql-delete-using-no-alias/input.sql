DELETE FROM orders
USING users
WHERE orders.user_id = users.id;
