DELETE pr
FROM product_reviews pr
WHERE
    NOT EXISTS (
        SELECT 1
        FROM products p
        WHERE p.id = pr.product_id
    )
    AND pr.created_at < NOW() - INTERVAL 1 MONTH;
