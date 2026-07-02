DELETE pr
FROM product_reviews pr
LEFT JOIN products p ON pr.product_id = p.id
WHERE
    p.id IS NULL
    AND pr.created_at < NOW() - INTERVAL 1 MONTH;
