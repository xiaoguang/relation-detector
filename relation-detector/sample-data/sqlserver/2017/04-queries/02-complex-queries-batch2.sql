-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship probes live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Sales order payment status.
SELECT so.[order_no],
       c.[name] AS [customer_name],
       so.[total_amount],
       so.[paid_amount],
       SUM(p.[amount]) AS [payment_amount]
FROM [dbo].[sales_orders] AS so
INNER JOIN [dbo].[customers] AS c ON so.[customer_id] = c.[id]
LEFT JOIN [dbo].[payments] AS p ON p.[order_id] = so.[id]
GROUP BY so.[order_no], c.[name], so.[total_amount], so.[paid_amount];

-- Employee department attendance rollup.
SELECT d.[name] AS [department_name],
       e.[employee_no],
       e.[name] AS [employee_name],
       COUNT(a.[id]) AS [attendance_days]
FROM [dbo].[employees] AS e
INNER JOIN [dbo].[departments] AS d ON e.[department_id] = d.[id]
LEFT JOIN [dbo].[attendance] AS a ON a.[employee_id] = e.[id]
GROUP BY d.[name], e.[employee_no], e.[name];

-- Accounts payable aging by supplier.
SELECT s.[name] AS [supplier_name],
       COUNT(api.[id]) AS [invoice_count],
       SUM(api.[invoice_amount]) AS [invoice_amount],
       SUM(api.[paid_amount]) AS [paid_amount]
FROM [dbo].[ap_invoices] AS api
INNER JOIN [dbo].[suppliers] AS s ON api.[supplier_id] = s.[id]
WHERE api.[status] <> 'PAID'
GROUP BY s.[name];
