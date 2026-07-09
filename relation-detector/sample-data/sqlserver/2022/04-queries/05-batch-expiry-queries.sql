-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship coverage cases live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Batch expiry risk by product and warehouse.
SELECT p.[sku],
       p.[name] AS [product_name],
       pb.[batch_no],
       w.[code] AS [warehouse_code],
       pb.[expiry_date],
       SUM(i.[available_quantity]) AS [available_qty]
FROM [dbo].[product_batches] AS pb
INNER JOIN [dbo].[products] AS p ON pb.[product_id] = p.[id]
INNER JOIN [dbo].[inventory] AS i ON i.[batch_id] = pb.[id]
INNER JOIN [dbo].[warehouses] AS w ON i.[warehouse_id] = w.[id]
GROUP BY p.[sku], p.[name], pb.[batch_no], w.[code], pb.[expiry_date]
HAVING SUM(i.[available_quantity]) > 0;

-- Batch purchase source trace.
SELECT pb.[batch_no],
       s.[name] AS [supplier_name],
       p.[sku],
       pb.[initial_qty],
       pb.[current_qty]
FROM [dbo].[product_batches] AS pb
INNER JOIN [dbo].[products] AS p ON pb.[product_id] = p.[id]
LEFT JOIN [dbo].[suppliers] AS s ON pb.[supplier_id] = s.[id];

-- Batch-managed sales items.
SELECT so.[order_no],
       p.[sku],
       pb.[batch_no],
       soi.[quantity],
       soi.[amount]
FROM [dbo].[sales_order_items] AS soi
INNER JOIN [dbo].[sales_orders] AS so ON soi.[order_id] = so.[id]
INNER JOIN [dbo].[products] AS p ON soi.[product_id] = p.[id]
LEFT JOIN [dbo].[product_batches] AS pb ON soi.[batch_id] = pb.[id];
