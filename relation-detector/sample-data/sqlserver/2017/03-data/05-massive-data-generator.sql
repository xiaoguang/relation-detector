-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[cashier_journals] ([account_id])
SELECT p.[id]
FROM [dbo].[accounts] AS p
INNER JOIN [dbo].[cashier_journals] AS c ON c.[account_id] = p.[id];

INSERT INTO [dbo].[cashier_journals] ([cashier_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[cashier_journals] AS c ON c.[cashier_id] = p.[id];

INSERT INTO [dbo].[cashier_journals] ([voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[cashier_journals] AS c ON c.[voucher_id] = p.[id];

INSERT INTO [dbo].[salary_payments] ([employee_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[salary_payments] AS c ON c.[employee_id] = p.[id];

INSERT INTO [dbo].[salary_payments] ([voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[salary_payments] AS c ON c.[voucher_id] = p.[id];

INSERT INTO [dbo].[reconciliations] ([account_id])
SELECT p.[id]
FROM [dbo].[accounts] AS p
INNER JOIN [dbo].[reconciliations] AS c ON c.[account_id] = p.[id];

INSERT INTO [dbo].[reconciliations] ([prepared_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[reconciliations] AS c ON c.[prepared_by] = p.[id];

INSERT INTO [dbo].[reconciliations] ([reviewed_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[reconciliations] AS c ON c.[reviewed_by] = p.[id];

INSERT INTO [dbo].[reconciliation_items] ([reconciliation_id])
SELECT p.[id]
FROM [dbo].[reconciliations] AS p
INNER JOIN [dbo].[reconciliation_items] AS c ON c.[reconciliation_id] = p.[id];

INSERT INTO [dbo].[settlements] ([voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[settlements] AS c ON c.[voucher_id] = p.[id];

INSERT INTO [dbo].[settlements] ([prepared_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[settlements] AS c ON c.[prepared_by] = p.[id];

INSERT INTO [dbo].[settlements] ([approved_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[settlements] AS c ON c.[approved_by] = p.[id];

INSERT INTO [dbo].[settlement_items] ([settlement_id])
SELECT p.[id]
FROM [dbo].[settlements] AS p
INNER JOIN [dbo].[settlement_items] AS c ON c.[settlement_id] = p.[id];

INSERT INTO [dbo].[shipments] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[shipments] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[shipments] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[shipments] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[shipping_tracks] ([shipment_id])
SELECT p.[id]
FROM [dbo].[shipments] AS p
INNER JOIN [dbo].[shipping_tracks] AS c ON c.[shipment_id] = p.[id];

INSERT INTO [dbo].[commission_rules] ([product_category_id])
SELECT p.[id]
FROM [dbo].[product_categories] AS p
INNER JOIN [dbo].[commission_rules] AS c ON c.[product_category_id] = p.[id];

INSERT INTO [dbo].[sales_commissions] ([employee_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[sales_commissions] AS c ON c.[employee_id] = p.[id];

INSERT INTO [dbo].[sales_commissions] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[sales_commissions] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[promotion_products] ([promotion_id])
SELECT p.[id]
FROM [dbo].[promotions] AS p
INNER JOIN [dbo].[promotion_products] AS c ON c.[promotion_id] = p.[id];
