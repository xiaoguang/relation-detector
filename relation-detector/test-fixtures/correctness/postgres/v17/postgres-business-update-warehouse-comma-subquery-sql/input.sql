-- PostgreSQL business case 6 equivalent: INNER joins as comma rowsets, LEFT aggregate as derived subquery relation.
UPDATE warehouse_inventory wi
SET stock_reserved = wi.stock_reserved + oi.quantity,
    last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END
FROM bin_locations bl,
     order_items oi,
     (
        SELECT
            o.id AS order_id,
            o.customer_id,
            c.risk_score,
            DENSE_RANK() OVER (PARTITION BY o.customer_id ORDER BY o.created_at DESC) AS ranking
        FROM orders o, customer_profiles c
        WHERE o.customer_id = c.id
          AND o.payment_status = 'PARTIALLY_PAID'
     ) latest_orders,
     (
        SELECT
            supplier_id,
            product_id,
            AVG(supply_price) AS avg_cost
        FROM supplier_manifests
        GROUP BY supplier_id, product_id
        HAVING COUNT(manifest_id) > 1
     ) sm
WHERE wi.bin_id = bl.id
  AND wi.product_id = oi.product_id
  AND bl.id = oi.product_id
  AND oi.order_id = latest_orders.order_id
  AND latest_orders.ranking = 1
  AND oi.product_id = sm.product_id
  AND wi.primary_supplier_id = sm.supplier_id
  AND bl.zone_type = 'PICKING'
  AND wi.is_active = 1
  AND oi.fulfillment_status = 'PENDING';
