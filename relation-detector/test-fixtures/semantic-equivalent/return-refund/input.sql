-- Semantic equivalent scenario: create refund records from sales returns and payments.
INSERT INTO refund_records (
    return_id,
    order_id,
    customer_id,
    payment_id,
    refund_amount,
    original_paid_amount
)
SELECT
    sr.id,
    so.id,
    so.customer_id,
    p.id,
    sr.refund_amount,
    p.amount
FROM sales_returns sr
JOIN sales_orders so ON so.id = sr.order_id
JOIN payments p ON p.order_id = so.id
WHERE sr.status = 'approved';
