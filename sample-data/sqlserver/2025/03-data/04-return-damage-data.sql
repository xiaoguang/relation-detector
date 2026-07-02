-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[purchase_returns] ([handler_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[handler_id] = p.[id];

INSERT INTO [dbo].[purchase_returns] ([approved_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[approved_by] = p.[id];

INSERT INTO [dbo].[purchase_returns] ([refund_voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[refund_voucher_id] = p.[id];

INSERT INTO [dbo].[purchase_return_items] ([return_id])
SELECT p.[id]
FROM [dbo].[purchase_returns] AS p
INNER JOIN [dbo].[purchase_return_items] AS c ON c.[return_id] = p.[id];

INSERT INTO [dbo].[purchase_return_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[purchase_return_items] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[purchase_return_items] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[purchase_return_items] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[damage_reports] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[damage_reports] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[damage_reports] ([reported_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[damage_reports] AS c ON c.[reported_by] = p.[id];

INSERT INTO [dbo].[damage_reports] ([approved_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[damage_reports] AS c ON c.[approved_by] = p.[id];

INSERT INTO [dbo].[damage_reports] ([executed_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[damage_reports] AS c ON c.[executed_by] = p.[id];

INSERT INTO [dbo].[damage_reports] ([voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[damage_reports] AS c ON c.[voucher_id] = p.[id];

INSERT INTO [dbo].[damage_report_items] ([report_id])
SELECT p.[id]
FROM [dbo].[damage_reports] AS p
INNER JOIN [dbo].[damage_report_items] AS c ON c.[report_id] = p.[id];

INSERT INTO [dbo].[damage_report_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[damage_report_items] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[damage_report_items] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[damage_report_items] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[accounts] ([parent_id])
SELECT p.[id]
FROM [dbo].[accounts] AS p
INNER JOIN [dbo].[accounts] AS c ON c.[parent_id] = p.[id];

INSERT INTO [dbo].[vouchers] ([prepared_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[vouchers] AS c ON c.[prepared_by] = p.[id];

INSERT INTO [dbo].[vouchers] ([reviewed_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[vouchers] AS c ON c.[reviewed_by] = p.[id];

INSERT INTO [dbo].[vouchers] ([posted_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[vouchers] AS c ON c.[posted_by] = p.[id];

INSERT INTO [dbo].[voucher_items] ([voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[voucher_items] AS c ON c.[voucher_id] = p.[id];

INSERT INTO [dbo].[voucher_items] ([account_id])
SELECT p.[id]
FROM [dbo].[accounts] AS p
INNER JOIN [dbo].[voucher_items] AS c ON c.[account_id] = p.[id];
