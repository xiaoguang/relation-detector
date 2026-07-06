-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship probes live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Sales return item reason analysis.
SELECT sr.[return_no],
       c.[name] AS [customer_name],
       p.[sku],
       sri.[quantity],
       sri.[amount],
       sri.[reason]
FROM [dbo].[sales_return_items] AS sri
INNER JOIN [dbo].[sales_returns] AS sr ON sri.[return_id] = sr.[id]
INNER JOIN [dbo].[customers] AS c ON sr.[customer_id] = c.[id]
INNER JOIN [dbo].[products] AS p ON sri.[product_id] = p.[id];

-- Purchase return reason analysis.
SELECT pr.[return_no],
       s.[name] AS [supplier_name],
       p.[sku],
       pri.[quantity],
       pri.[amount],
       pri.[reason]
FROM [dbo].[purchase_return_items] AS pri
INNER JOIN [dbo].[purchase_returns] AS pr ON pri.[return_id] = pr.[id]
INNER JOIN [dbo].[suppliers] AS s ON pr.[supplier_id] = s.[id]
INNER JOIN [dbo].[products] AS p ON pri.[product_id] = p.[id];

-- Repair parts issue summary.
SELECT ro.[repair_no],
       p.[sku],
       w.[code] AS [warehouse_code],
       SUM(rop.[quantity]) AS [issued_qty],
       SUM(rop.[quantity] * rop.[unit_cost]) AS [issued_cost]
FROM [dbo].[repair_order_parts] AS rop
INNER JOIN [dbo].[repair_orders] AS ro ON rop.[repair_order_id] = ro.[id]
INNER JOIN [dbo].[products] AS p ON rop.[product_id] = p.[id]
LEFT JOIN [dbo].[warehouses] AS w ON rop.[issued_from_warehouse_id] = w.[id]
GROUP BY ro.[repair_no], p.[sku], w.[code];
