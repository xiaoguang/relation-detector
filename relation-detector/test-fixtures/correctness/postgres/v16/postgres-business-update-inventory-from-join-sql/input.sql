-- PostgreSQL business case 5: UPDATE FROM with one ambiguous equality and one FK-like join.
UPDATE inventory i
SET stock_reserved = i.stock_reserved + oi.quantity,
    last_ordered_from = s.supplier_name
FROM order_items oi
INNER JOIN suppliers s ON i.supplier_id = s.id
WHERE i.product_id = oi.product_id
  AND oi.status = 'PENDING';
