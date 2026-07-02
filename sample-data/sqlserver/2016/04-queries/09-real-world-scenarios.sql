-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship probes live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Inventory valuation by product and warehouse.
SELECT p.[sku],
       w.[code] AS [warehouse_code],
       SUM(iv.[quantity]) AS [quantity],
       SUM(iv.[inventory_value]) AS [inventory_value]
FROM [dbo].[inventory_valuation_snapshots] AS iv
INNER JOIN [dbo].[products] AS p ON iv.[product_id] = p.[id]
INNER JOIN [dbo].[warehouses] AS w ON iv.[warehouse_id] = w.[id]
GROUP BY p.[sku], w.[code];

-- Finished goods receipt by work order.
SELECT wo.[order_no],
       p.[sku],
       fgr.[receipt_no],
       fgr.[received_qty],
       w.[code] AS [warehouse_code]
FROM [dbo].[finished_goods_receipts] AS fgr
INNER JOIN [dbo].[work_orders] AS wo ON fgr.[work_order_id] = wo.[id]
INNER JOIN [dbo].[products] AS p ON fgr.[product_id] = p.[id]
INNER JOIN [dbo].[warehouses] AS w ON fgr.[warehouse_id] = w.[id];

-- Standard cost approval list.
SELECT sc.[cost_version],
       p.[sku],
       sc.[standard_cost],
       e.[name] AS [approved_by]
FROM [dbo].[standard_costs] AS sc
INNER JOIN [dbo].[products] AS p ON sc.[product_id] = p.[id]
LEFT JOIN [dbo].[employees] AS e ON sc.[approved_by] = e.[id];
