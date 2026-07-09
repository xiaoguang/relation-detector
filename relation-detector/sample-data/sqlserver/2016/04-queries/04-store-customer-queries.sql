-- ============================================================
-- SQL Server ERP natural business query samples.
-- These queries are intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- High-density relationship coverage cases live under test-fixtures/semantic-equivalent.
-- ============================================================

-- Cashier journal reconciliation overview.
SELECT cj.[journal_no],
       e.[name] AS [cashier_name],
       a.[account_no],
       cj.[cash_amount],
       cj.[card_amount],
       cj.[total_amount]
FROM [dbo].[cashier_journals] AS cj
INNER JOIN [dbo].[employees] AS e ON cj.[cashier_id] = e.[id]
LEFT JOIN [dbo].[accounts] AS a ON cj.[account_id] = a.[id]
WHERE cj.[business_date] >= DATEADD(day, -30, CAST(SYSDATETIME() AS date));

-- Customer payment trend.
SELECT c.[code] AS [customer_code],
       c.[name] AS [customer_name],
       COUNT(p.[id]) AS [payment_count],
       SUM(p.[amount]) AS [paid_amount]
FROM [dbo].[customers] AS c
INNER JOIN [dbo].[payments] AS p ON p.[customer_id] = c.[id]
GROUP BY c.[code], c.[name];

-- Salary payment voucher summary.
SELECT sp.[payment_no],
       e.[name] AS [employee_name],
       v.[voucher_no],
       sp.[net_amount]
FROM [dbo].[salary_payments] AS sp
INNER JOIN [dbo].[employees] AS e ON sp.[employee_id] = e.[id]
LEFT JOIN [dbo].[vouchers] AS v ON sp.[voucher_id] = v.[id];
