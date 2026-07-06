-- PostgreSQL 17 SQL/JSON rowset: JSON_TABLE is a function rowset, not a physical business table.
SELECT o.id, jt.product_id, p.sku
FROM orders o
JOIN users u ON o.user_id = u.id
CROSS JOIN JSON_TABLE(
    o.payload,
    '$.items[*]'
    COLUMNS (
        product_id bigint PATH '$.product_id',
        quantity numeric PATH '$.quantity'
    )
) AS jt
JOIN products p ON jt.product_id = p.id
WHERE JSON_EXISTS(o.payload, '$.items[*]');
