-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship probes live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Production planning demand overview.
SELECT pp.[plan_no],
       p.[sku],
       w.[code] AS [warehouse_code],
       pp.[forecast_qty],
       pp.[confirmed_sales_qty],
       pp.[planned_production_qty]
FROM [dbo].[production_plans] AS pp
INNER JOIN [dbo].[products] AS p ON pp.[product_id] = p.[id]
INNER JOIN [dbo].[warehouses] AS w ON pp.[warehouse_id] = w.[id];

-- MRP suggested purchase by supplier.
SELECT mr.[run_no],
       p.[sku] AS [component_sku],
       s.[name] AS [supplier_name],
       SUM(mri.[net_requirement]) AS [net_requirement],
       SUM(mri.[suggested_order_qty]) AS [suggested_order_qty]
FROM [dbo].[mrp_run_items] AS mri
INNER JOIN [dbo].[mrp_runs] AS mr ON mri.[run_id] = mr.[id]
INNER JOIN [dbo].[products] AS p ON mri.[component_product_id] = p.[id]
LEFT JOIN [dbo].[suppliers] AS s ON mri.[suggested_supplier_id] = s.[id]
GROUP BY mr.[run_no], p.[sku], s.[name];

-- Shift assignment coverage.
SELECT es.[shift_code],
       e.[name] AS [employee_name],
       w.[code] AS [warehouse_code],
       esa.[work_date]
FROM [dbo].[employee_shift_assignments] AS esa
INNER JOIN [dbo].[employee_shifts] AS es ON esa.[shift_id] = es.[id]
INNER JOIN [dbo].[employees] AS e ON esa.[employee_id] = e.[id]
LEFT JOIN [dbo].[warehouses] AS w ON esa.[warehouse_id] = w.[id];
