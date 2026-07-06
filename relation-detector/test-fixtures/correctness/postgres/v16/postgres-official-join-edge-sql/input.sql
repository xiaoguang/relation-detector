-- PostgreSQL official regression/docs inspired: join.sql and Table Expressions.
-- Covers explicit outer joins, JOIN USING alias, NATURAL join, parenthesized
-- join trees, and legacy comma join predicates.
SELECT *
FROM orders o
INNER JOIN users u ON o.user_id = u.id
LEFT OUTER JOIN payments p ON p.order_id = o.id
RIGHT JOIN shipments s ON s.order_id = o.id
FULL OUTER JOIN refunds r ON r.order_id = o.id;

SELECT *
FROM orders o
JOIN order_tags ot USING (order_id) AS order_join;

SELECT *
FROM customers c
NATURAL JOIN customer_audit ca;

SELECT *
FROM (orders o LEFT JOIN users u ON o.user_id = u.id)
JOIN invoices i ON i.order_id = o.id;
