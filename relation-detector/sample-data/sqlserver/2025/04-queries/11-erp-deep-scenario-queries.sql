-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship coverage cases live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Picking task execution summary.
SELECT pt.[task_no],
       so.[order_no],
       e.[name] AS [picker_name],
       SUM(pti.[required_qty]) AS [required_qty],
       SUM(pti.[picked_qty]) AS [picked_qty]
FROM [dbo].[picking_tasks] AS pt
INNER JOIN [dbo].[sales_orders] AS so ON pt.[sales_order_id] = so.[id]
LEFT JOIN [dbo].[employees] AS e ON pt.[assigned_to] = e.[id]
INNER JOIN [dbo].[picking_task_items] AS pti ON pti.[picking_task_id] = pt.[id]
GROUP BY pt.[task_no], so.[order_no], e.[name];

-- Repair order cost summary.
SELECT ro.[repair_no],
       c.[name] AS [customer_name],
       st.[ticket_no],
       ro.[estimated_cost],
       ro.[actual_cost],
       SUM(rop.[quantity] * rop.[unit_cost]) AS [parts_cost]
FROM [dbo].[repair_orders] AS ro
INNER JOIN [dbo].[customers] AS c ON ro.[customer_id] = c.[id]
LEFT JOIN [dbo].[service_tickets] AS st ON ro.[service_ticket_id] = st.[id]
LEFT JOIN [dbo].[repair_order_parts] AS rop ON rop.[repair_order_id] = ro.[id]
GROUP BY ro.[repair_no], c.[name], st.[ticket_no], ro.[estimated_cost], ro.[actual_cost];

-- Fiscal sales trend by month.
SELECT fc.[period_code],
       r.[sales_region],
       SUM(sf.[sales_amount]) AS [sales_amount],
       SUM(sf.[gross_margin_amount]) AS [gross_margin_amount]
FROM [dbo].[sales_fact] AS sf
INNER JOIN [dbo].[fiscal_calendar] AS fc ON sf.[fiscal_date] = fc.[calendar_date]
INNER JOIN [dbo].[region_dim] AS r ON sf.[region_dim_id] = r.[id]
GROUP BY fc.[period_code], r.[sales_region]
ORDER BY fc.[period_code], r.[sales_region];
