-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship probes live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Supplier purchase and receipt scorecard.
SELECT s.[code] AS [supplier_code],
       s.[name] AS [supplier_name],
       COUNT(DISTINCT po.[id]) AS [purchase_order_count],
       SUM(poi.[amount]) AS [purchase_amount],
       SUM(poi.[received_qty]) AS [received_qty]
FROM [dbo].[suppliers] AS s
LEFT JOIN [dbo].[purchase_orders] AS po ON po.[supplier_id] = s.[id]
LEFT JOIN [dbo].[purchase_order_items] AS poi ON poi.[order_id] = po.[id]
GROUP BY s.[code], s.[name];

-- Supplier batch quality view.
SELECT s.[name] AS [supplier_name],
       p.[sku],
       COUNT(pb.[id]) AS [batch_count],
       SUM(pb.[current_qty]) AS [current_qty]
FROM [dbo].[suppliers] AS s
INNER JOIN [dbo].[product_batches] AS pb ON pb.[supplier_id] = s.[id]
INNER JOIN [dbo].[products] AS p ON pb.[product_id] = p.[id]
GROUP BY s.[name], p.[sku];

-- Supplier payable exposure.
SELECT s.[name] AS [supplier_name],
       SUM(api.[invoice_amount] - api.[paid_amount]) AS [open_payable]
FROM [dbo].[suppliers] AS s
INNER JOIN [dbo].[ap_invoices] AS api ON api.[supplier_id] = s.[id]
GROUP BY s.[name];
