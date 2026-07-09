-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_refresh_customer_balance_from_orders
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_customer_balance_from_orders]
AS
BEGIN
    UPDATE c
    SET [balance] = order_rollup.[order_amount] - order_rollup.[paid_amount],
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[customers] AS c
    INNER JOIN (
        SELECT so.[customer_id], SUM(so.[total_amount]) AS [order_amount], SUM(so.[paid_amount]) AS [paid_amount]
        FROM [dbo].[sales_orders] AS so
        GROUP BY so.[customer_id]
    ) AS order_rollup ON order_rollup.[customer_id] = c.[id];

    INSERT INTO [dbo].[audit_log] ([action], [target_type], [target_id], [new_value], [created_at])
    SELECT 'refresh_customer_balance', 'customer', c.[id], c.[code], CURRENT_TIMESTAMP
    FROM [dbo].[customers] AS c
    WHERE c.[balance] > c.[credit_limit];
END;
-- relation-detector-fixture-end
