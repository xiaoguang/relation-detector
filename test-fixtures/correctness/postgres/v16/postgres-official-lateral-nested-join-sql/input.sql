-- PostgreSQL official docs inspired: Table Expressions, LATERAL, ROWS FROM,
-- table functions, UNNEST WITH ORDINALITY, and nested LATERAL joins.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT li.order_id, li.product_id
  FROM line_items li
  WHERE li.order_id = o.id
) liq ON true
JOIN products p ON liq.product_id = p.id;

SELECT *
FROM orders o
LEFT JOIN LATERAL ROWS FROM (
  json_to_recordset(o.payload) AS (product_id BIGINT, quantity INTEGER),
  generate_series(1, 3)
) AS decoded(product_id, quantity, ordinal) ON true
JOIN products p ON decoded.product_id = p.id;

SELECT *
FROM orders o
LEFT JOIN LATERAL get_order_users(o.id) AS gou(user_id) ON true
JOIN users u ON gou.user_id = u.id
WHERE EXISTS (
  SELECT 1
  FROM audit_events ae
  WHERE ae.order_id = o.id
);
