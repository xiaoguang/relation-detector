-- PostgreSQL business case 5 equivalent: INNER JOIN rewritten as comma rowsets plus WHERE equality.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi, suppliers s
WHERE i.product_id = oi.product_id
  AND i.supplier_id = s.id
  AND oi.status = 'PENDING';
