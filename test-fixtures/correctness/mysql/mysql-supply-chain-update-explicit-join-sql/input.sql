UPDATE warehouse_inventory wi
INNER JOIN bin_locations bl ON wi.bin_id = bl.id AND bl.zone_type = 'PICKING'
INNER JOIN order_items oi ON wi.product_id = oi.product_id
INNER JOIN (
    SELECT
        o.id AS order_id,
        o.customer_id,
        c.risk_score,
        DENSE_RANK() OVER (PARTITION BY o.customer_id ORDER BY o.created_at DESC) AS ranking
    FROM orders o
    INNER JOIN customer_profiles c ON o.customer_id = c.id
    WHERE o.payment_status = 'PARTIALLY_PAID'
) latest_orders ON oi.order_id = latest_orders.order_id AND latest_orders.ranking = 1
LEFT JOIN (
    SELECT
        supplier_id,
        product_id,
        AVG(supply_price) AS avg_cost
    FROM supplier_manifests
    GROUP BY supplier_id, product_id
    HAVING COUNT(manifest_id) > 1
) sm ON wi.product_id = sm.product_id AND wi.primary_supplier_id = sm.supplier_id
SET
    wi.stock_reserved = wi.stock_reserved + oi.quantity,
    wi.last_audit_status = CASE
        WHEN latest_orders.risk_score > 80 THEN 'HOLD_FOR_REVIEW'
        WHEN wi.stock_available - oi.quantity < 10 THEN 'LOW_STOCK_WARNING'
        ELSE 'ALLOCATED'
    END,
    oi.fulfillment_status = 'PROCESSING',
    oi.estimated_cost = COALESCE(sm.avg_cost, wi.default_unit_cost) * oi.quantity
WHERE
    wi.is_active = 1
    AND oi.fulfillment_status = 'PENDING';
