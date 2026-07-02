CREATE OR ALTER PROCEDURE dbo.sp_rebuild_sales_fact
AS
BEGIN
    INSERT INTO dbo.sales_fact (customer_id, order_id, paid_amount, last_paid_at)
    SELECT
        o.customer_id,
        o.order_id,
        SUM(p.amount) AS paid_amount,
        MAX(p.paid_at) AS last_paid_at
    FROM dbo.orders AS o
    INNER JOIN dbo.payments AS p ON p.order_id = o.order_id
    WHERE o.customer_id IN (
        SELECT c.customer_id
        FROM dbo.customers AS c
    )
    GROUP BY o.customer_id, o.order_id;

    UPDATE sf
    SET paid_amount = p.amount,
        last_paid_at = p.paid_at
    FROM dbo.sales_fact AS sf
    INNER JOIN dbo.payments AS p ON p.order_id = sf.order_id;

    MERGE INTO dbo.sales_fact AS sf
    USING (
        SELECT
            o.customer_id,
            o.order_id,
            SUM(p.amount) AS paid_amount,
            MAX(p.paid_at) AS last_paid_at
        FROM dbo.orders AS o
        INNER JOIN dbo.payments AS p ON p.order_id = o.order_id
        GROUP BY o.customer_id, o.order_id
    ) AS src
    ON sf.order_id = src.order_id
    WHEN MATCHED THEN
        UPDATE SET
            sf.paid_amount = src.paid_amount,
            sf.last_paid_at = src.last_paid_at
    WHEN NOT MATCHED THEN
        INSERT (customer_id, order_id, paid_amount, last_paid_at)
        VALUES (src.customer_id, src.order_id, src.paid_amount, src.last_paid_at);
END;
