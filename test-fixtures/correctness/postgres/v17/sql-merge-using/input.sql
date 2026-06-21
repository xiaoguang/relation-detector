MERGE INTO target_orders AS t
USING source_orders AS s
ON t.source_order_id = s.id
WHEN MATCHED AND s.cancelled_at IS NULL THEN
  UPDATE SET synced_at = CURRENT_TIMESTAMP
WHEN NOT MATCHED THEN
  INSERT (source_order_id) VALUES (s.id);
