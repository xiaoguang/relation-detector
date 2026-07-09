-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_post_stocktake_variance
CREATE OR ALTER PROCEDURE [dbo].[sp_post_stocktake_variance]
AS
BEGIN
    MERGE INTO [dbo].[inventory] AS target
    USING (
        SELECT st.[warehouse_id], si.[product_id], si.[batch_id], SUM(si.[counted_quantity]) AS [counted_quantity]
        FROM [dbo].[stocktakes] AS st
        INNER JOIN [dbo].[stocktake_items] AS si ON si.[stocktake_id] = st.[id]
        WHERE st.[status] = 'approved'
        GROUP BY st.[warehouse_id], si.[product_id], si.[batch_id]
    ) AS src
    ON target.[warehouse_id] = src.[warehouse_id]
       AND target.[product_id] = src.[product_id]
       AND target.[batch_id] = src.[batch_id]
    WHEN MATCHED THEN UPDATE SET
        target.[quantity] = src.[counted_quantity],
        target.[available_quantity] = src.[counted_quantity] - target.[locked_quantity],
        target.[last_stocktake_date] = CURRENT_TIMESTAMP;

    INSERT INTO [dbo].[inventory_transactions] ([product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change], [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark], [created_at])
    SELECT si.[product_id], si.[batch_id], st.[warehouse_id], 'stocktake_variance',
           si.[variance_quantity], si.[book_quantity], si.[counted_quantity], 'stocktake', st.[id], st.[created_by], si.[variance_reason], CURRENT_TIMESTAMP
    FROM [dbo].[stocktakes] AS st
    INNER JOIN [dbo].[stocktake_items] AS si ON si.[stocktake_id] = st.[id]
    WHERE si.[variance_quantity] <> 0;
END;
-- relation-detector-fixture-end
