-- PostgreSQL official docs inspired: WITH Queries and data-modifying
-- statements in WITH. Covers DELETE RETURNING, INSERT SELECT, UPDATE FROM,
-- MERGE USING, MATERIALIZED, and NOT MATERIALIZED boundaries.
WITH moved_rows AS (
  DELETE FROM order_staging os
  USING orders o
  WHERE os.order_id = o.id
  RETURNING os.order_id, os.user_id
),
archived_rows AS (
  INSERT INTO order_archive(order_id, user_id)
  SELECT mr.order_id, mr.user_id
  FROM moved_rows mr
  RETURNING order_id, user_id
)
SELECT *
FROM archived_rows ar
JOIN users u ON ar.user_id = u.id;

WITH candidates AS MATERIALIZED (
  SELECT o.id, o.user_id
  FROM orders o
),
updated_accounts AS (
  UPDATE accounts a
  SET touched_at = now()
  FROM candidates c
  WHERE a.user_id = c.user_id
  RETURNING a.user_id
)
SELECT *
FROM updated_accounts ua
JOIN users u ON ua.user_id = u.id;

WITH source_rows AS NOT MATERIALIZED (
  SELECT s.order_id, s.customer_id
  FROM staging_orders s
)
MERGE INTO orders o
USING source_rows sr
ON o.id = sr.order_id
WHEN MATCHED THEN
  UPDATE SET customer_id = sr.customer_id;
