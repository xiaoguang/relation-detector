-- Semantic equivalent scenario: rebuild sales_fact from sales order facts.
INSERT INTO sales_fact (
    customer_id,
    product_id,
    order_id,
    sales_amount,
    quantity_sold,
    warehouse_id,
    tax_amount
)
SELECT
    so.customer_id,
    soi.product_id,
    so.id,
    soi.amount,
    soi.quantity,
    so.warehouse_id,
    ROUND(soi.amount * 0.13, 2)
FROM sales_orders so
JOIN sales_order_items soi ON soi.order_id = so.id
JOIN warehouses w ON w.id = so.warehouse_id
WHERE so.status IN ('confirmed', 'delivered');
