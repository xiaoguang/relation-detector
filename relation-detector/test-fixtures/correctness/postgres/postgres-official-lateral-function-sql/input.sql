-- PostgreSQL official regression/docs inspired: Table Expressions.
-- Covers ROWS FROM, json_to_recordset, generate_series, and LATERAL function
-- rowsets without treating functions or their aliases as physical tables.
SELECT *
FROM orders o
LEFT JOIN LATERAL (
  SELECT o.user_id
) projected_user ON true
JOIN users u ON projected_user.user_id = u.id;

SELECT *
FROM orders o
JOIN ROWS FROM (
  json_to_recordset(o.payload) AS (user_id bigint, tag text),
  generate_series(1, 3)
) AS expanded(user_id, tag, seq)
  ON expanded.user_id = o.user_id
JOIN users u ON o.user_id = u.id;

SELECT *
FROM manufacturers m
LEFT JOIN LATERAL get_product_names(m.id) pname ON true
JOIN products p ON p.manufacturer_id = m.id;
