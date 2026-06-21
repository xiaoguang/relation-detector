-- PostgreSQL business case 4 equivalent: orphan cleanup through NOT EXISTS.
DELETE FROM product_reviews pr
WHERE NOT EXISTS (
    SELECT 1
    FROM products p
    WHERE pr.product_id = p.id
);
