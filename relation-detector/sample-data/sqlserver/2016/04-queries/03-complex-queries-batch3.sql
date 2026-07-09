-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship coverage cases live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Customer return and refund summary.
SELECT sr.[return_no],
       c.[name] AS [customer_name],
       so.[order_no],
       SUM(sri.[amount]) AS [return_amount],
       SUM(sri.[quantity]) AS [return_qty]
FROM [dbo].[sales_returns] AS sr
INNER JOIN [dbo].[customers] AS c ON sr.[customer_id] = c.[id]
LEFT JOIN [dbo].[sales_orders] AS so ON sr.[sales_order_id] = so.[id]
INNER JOIN [dbo].[sales_return_items] AS sri ON sri.[return_id] = sr.[id]
GROUP BY sr.[return_no], c.[name], so.[order_no];

-- Expiring batch inventory.
SELECT p.[sku],
       pb.[batch_no],
       pb.[expiry_date],
       w.[code] AS [warehouse_code],
       SUM(i.[quantity]) AS [batch_qty]
FROM [dbo].[product_batches] AS pb
INNER JOIN [dbo].[products] AS p ON pb.[product_id] = p.[id]
INNER JOIN [dbo].[inventory] AS i ON i.[batch_id] = pb.[id]
INNER JOIN [dbo].[warehouses] AS w ON i.[warehouse_id] = w.[id]
WHERE pb.[expiry_date] <= DATEADD(day, 60, CAST(SYSDATETIME() AS date))
GROUP BY p.[sku], pb.[batch_no], pb.[expiry_date], w.[code];

-- Purchase return impact by supplier.
SELECT s.[code] AS [supplier_code],
       s.[name] AS [supplier_name],
       COUNT(pr.[id]) AS [return_count],
       SUM(pri.[amount]) AS [return_amount]
FROM [dbo].[purchase_returns] AS pr
INNER JOIN [dbo].[suppliers] AS s ON pr.[supplier_id] = s.[id]
INNER JOIN [dbo].[purchase_return_items] AS pri ON pri.[return_id] = pr.[id]
GROUP BY s.[code], s.[name];
