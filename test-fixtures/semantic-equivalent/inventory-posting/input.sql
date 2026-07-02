-- Semantic equivalent scenario: post inventory transaction from shipped order items.
INSERT INTO inventory_transactions (
    product_id,
    warehouse_id,
    quantity_delta,
    before_quantity,
    after_quantity,
    source_order_id
)
SELECT
    i.product_id,
    i.warehouse_id,
    soi.quantity,
    i.quantity,
    i.quantity - soi.quantity,
    so.id
FROM inventory i
JOIN sales_order_items soi ON soi.product_id = i.product_id
JOIN sales_orders so ON so.id = soi.order_id
WHERE so.status = 'delivered';
