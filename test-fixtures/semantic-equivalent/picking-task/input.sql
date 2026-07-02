-- Semantic equivalent scenario: generate picking task items for an order.
INSERT INTO picking_task_items (
    task_id,
    order_id,
    product_id,
    requested_qty,
    available_qty,
    location_id
)
SELECT
    pt.id,
    so.id,
    soi.product_id,
    soi.quantity,
    ilb.available_quantity,
    ilb.location_id
FROM picking_tasks pt
JOIN sales_orders so ON so.id = pt.order_id
JOIN sales_order_items soi ON soi.order_id = so.id
JOIN inventory_location_balances ilb ON ilb.product_id = soi.product_id
WHERE pt.status = 'open';
