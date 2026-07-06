UPDATE warehouse_inventory wi,
       bin_locations bl,
       order_items oi,
       (
           SELECT
               o.id AS order_id,
               o.customer_id,
               c.risk_score,
               DENSE_RANK() OVER (PARTITION BY o.customer_id ORDER BY o.created_at DESC) AS ranking
           FROM orders o,
                customer_profiles c
           WHERE o.customer_id = c.id
             AND o.payment_status = 'PARTIALLY_PAID'
       ) latest_orders
SET
    wi.stock_reserved = wi.stock_reserved + oi.quantity,
    wi.last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END,
    oi.fulfillment_status = 'PROCESSING',
    oi.estimated_cost = COALESCE((
        SELECT AVG(smx.supply_price)
        FROM supplier_manifests smx
        WHERE wi.product_id = smx.product_id
          AND wi.primary_supplier_id = smx.supplier_id
        GROUP BY smx.supplier_id, smx.product_id
        HAVING COUNT(smx.manifest_id) > 1
    ), wi.default_unit_cost) * oi.quantity
WHERE
    wi.bin_id = bl.id
    AND bl.zone_type = 'PICKING'
    AND wi.product_id = oi.product_id
    AND oi.order_id = latest_orders.order_id
    AND latest_orders.ranking = 1
    AND wi.is_active = 1
    AND oi.fulfillment_status = 'PENDING';
