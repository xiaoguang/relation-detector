-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship coverage cases live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Customer sales summary by region and category.
SELECT r.[sales_region],
       cat.[level1_name] AS [category_name],
       COUNT(DISTINCT sf.[order_id]) AS [order_count],
       SUM(sf.[quantity_sold]) AS [quantity_sold],
       SUM(sf.[sales_amount]) AS [sales_amount],
       SUM(sf.[net_sales_amount]) AS [net_sales_amount]
FROM [dbo].[sales_fact] AS sf
INNER JOIN [dbo].[region_dim] AS r ON sf.[region_dim_id] = r.[id]
INNER JOIN [dbo].[category_dim] AS cat ON sf.[category_dim_id] = cat.[id]
INNER JOIN [dbo].[fiscal_calendar] AS fc ON sf.[fiscal_date] = fc.[calendar_date]
WHERE fc.[is_current_fiscal_year] = 1
GROUP BY r.[sales_region], cat.[level1_name]
ORDER BY r.[sales_region], cat.[level1_name];

-- Product availability by warehouse.
SELECT w.[code] AS [warehouse_code],
       p.[sku],
       p.[name] AS [product_name],
       SUM(i.[quantity]) AS [on_hand_qty],
       SUM(i.[locked_quantity]) AS [locked_qty],
       SUM(i.[available_quantity]) AS [available_qty]
FROM [dbo].[inventory] AS i
INNER JOIN [dbo].[products] AS p ON i.[product_id] = p.[id]
INNER JOIN [dbo].[warehouses] AS w ON i.[warehouse_id] = w.[id]
GROUP BY w.[code], p.[sku], p.[name]
HAVING SUM(i.[available_quantity]) < MAX(p.[min_stock]);

-- Purchase order fulfillment progress.
SELECT po.[order_no],
       s.[name] AS [supplier_name],
       po.[status],
       SUM(poi.[quantity]) AS [ordered_qty],
       SUM(poi.[received_qty]) AS [received_qty],
       SUM(poi.[amount]) AS [ordered_amount]
FROM [dbo].[purchase_orders] AS po
INNER JOIN [dbo].[suppliers] AS s ON po.[supplier_id] = s.[id]
INNER JOIN [dbo].[purchase_order_items] AS poi ON poi.[order_id] = po.[id]
GROUP BY po.[order_no], s.[name], po.[status];
