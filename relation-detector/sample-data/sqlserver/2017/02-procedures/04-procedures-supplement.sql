-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_refresh_sales_commissions
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_sales_commissions]
AS
BEGIN
    MERGE INTO [dbo].[sales_commissions] AS target
    USING (
        SELECT so.[salesperson_id] AS [employee_id],
               so.[id] AS [order_id],
               soi.[id] AS [order_item_id],
               CONVERT(NVARCHAR(7), so.[order_date], 120) AS [period],
               soi.[amount] AS [base_amount],
               CAST(0.0500 AS DECIMAL(6,4)) AS [commission_rate],
               soi.[amount] * CAST(0.0500 AS DECIMAL(6,4)) AS [commission_amount],
               CASE WHEN so.[paid_amount] >= so.[total_amount] THEN soi.[amount] * CAST(0.0100 AS DECIMAL(6,4)) ELSE 0 END AS [bonus]
        FROM [dbo].[sales_orders] AS so
        INNER JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = so.[id]
        WHERE so.[salesperson_id] IS NOT NULL
    ) AS src
    ON target.[order_item_id] = src.[order_item_id]
    WHEN MATCHED THEN UPDATE SET
        target.[base_amount] = src.[base_amount],
        target.[commission_amount] = src.[commission_amount],
        target.[bonus] = src.[bonus],
        target.[total_commission] = src.[commission_amount] + src.[bonus],
        target.[calculated_at] = CURRENT_TIMESTAMP
    WHEN NOT MATCHED THEN INSERT ([employee_id], [order_id], [order_item_id], [period], [base_amount], [commission_rate], [commission_amount], [bonus], [total_commission], [status], [calculated_at])
        VALUES (src.[employee_id], src.[order_id], src.[order_item_id], src.[period], src.[base_amount], src.[commission_rate], src.[commission_amount], src.[bonus], src.[commission_amount] + src.[bonus], 'calculated', CURRENT_TIMESTAMP);
END;
-- relation-detector-fixture-end
