SELECT
    c.id,
    DATETRUNC(month, so.order_date) AS order_month,
    COUNT(*) AS order_count
FROM dbo.customers c
JOIN dbo.sales_orders so
    ON so.customer_id = c.id
CROSS APPLY GENERATE_SERIES(1, 12) AS gs
WHERE DATEPART(month, so.order_date) = gs.value
GROUP BY c.id, DATETRUNC(month, so.order_date);
