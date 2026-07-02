-- Semantic equivalent scenario: batch expiry analysis relation query.
SELECT
    pb.id,
    p.id AS product_id,
    i.batch_id
FROM product_batches pb
JOIN products p ON pb.product_id = p.id
JOIN inventory i ON i.batch_id = pb.id
WHERE pb.status = 'active';
