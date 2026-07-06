SELECT c.id
FROM customers c
WHERE c.status IN ('ACTIVE', 'VIP')
  AND c.name LIKE 'A%';
