SELECT c.id, u.id
FROM customers c
JOIN users u ON lower(c.email) = u.email;
