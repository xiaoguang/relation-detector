SELECT
    c.id,
    STRING_AGG(CONVERT(NVARCHAR(MAX), so.order_number), ',')
        WITHIN GROUP (ORDER BY so.order_number) AS order_numbers
FROM dbo.customers c
JOIN dbo.sales_orders so
    ON so.customer_id = c.id
GROUP BY c.id;
