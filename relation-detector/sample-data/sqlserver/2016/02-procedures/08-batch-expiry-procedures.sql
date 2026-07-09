-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_mark_expiring_batches
CREATE OR ALTER PROCEDURE [dbo].[sp_mark_expiring_batches]
AS
BEGIN
    UPDATE pb
    SET [status] = CASE WHEN pb.[expiry_date] < DATEADD(day, 30, CAST(CURRENT_TIMESTAMP AS DATE)) THEN 'expiring' ELSE pb.[status] END
    FROM [dbo].[product_batches] AS pb
    WHERE pb.[current_qty] > 0;

    INSERT INTO [dbo].[inventory_transactions] ([product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change], [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark], [created_at])
    SELECT i.[product_id], i.[batch_id], i.[warehouse_id],
           'batch_expiry_review',
           0,
           i.[quantity],
           i.[quantity],
           'product_batch',
           pb.[id],
           w.[manager_id],
           pb.[batch_no],
           CURRENT_TIMESTAMP
    FROM [dbo].[inventory] AS i
    INNER JOIN [dbo].[product_batches] AS pb ON pb.[id] = i.[batch_id]
    INNER JOIN [dbo].[warehouses] AS w ON w.[id] = i.[warehouse_id]
    WHERE pb.[status] = 'expiring';
END;
-- relation-detector-fixture-end
