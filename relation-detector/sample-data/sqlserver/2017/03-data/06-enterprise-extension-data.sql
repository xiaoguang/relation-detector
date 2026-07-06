-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[promotion_products] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[promotion_products] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[promotion_products] ([category_id])
SELECT p.[id]
FROM [dbo].[product_categories] AS p
INNER JOIN [dbo].[promotion_products] AS c ON c.[category_id] = p.[id];

INSERT INTO [dbo].[promotion_usages] ([promotion_id])
SELECT p.[id]
FROM [dbo].[promotions] AS p
INNER JOIN [dbo].[promotion_usages] AS c ON c.[promotion_id] = p.[id];

INSERT INTO [dbo].[promotion_usages] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[promotion_usages] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[promotion_usages] ([customer_id])
SELECT p.[id]
FROM [dbo].[customers] AS p
INNER JOIN [dbo].[promotion_usages] AS c ON c.[customer_id] = p.[id];

INSERT INTO [dbo].[invoices] ([supplier_id])
SELECT p.[id]
FROM [dbo].[suppliers] AS p
INNER JOIN [dbo].[invoices] AS c ON c.[supplier_id] = p.[id];

INSERT INTO [dbo].[invoices] ([customer_id])
SELECT p.[id]
FROM [dbo].[customers] AS p
INNER JOIN [dbo].[invoices] AS c ON c.[customer_id] = p.[id];

INSERT INTO [dbo].[three_way_matching] ([invoice_id])
SELECT p.[id]
FROM [dbo].[invoices] AS p
INNER JOIN [dbo].[three_way_matching] AS c ON c.[invoice_id] = p.[id];

INSERT INTO [dbo].[three_way_matching] ([purchase_order_id])
SELECT p.[id]
FROM [dbo].[purchase_orders] AS p
INNER JOIN [dbo].[three_way_matching] AS c ON c.[purchase_order_id] = p.[id];

INSERT INTO [dbo].[three_way_matching] ([purchase_receipt_id])
SELECT p.[id]
FROM [dbo].[purchase_receipts] AS p
INNER JOIN [dbo].[three_way_matching] AS c ON c.[purchase_receipt_id] = p.[id];

INSERT INTO [dbo].[three_way_matching] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[three_way_matching] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[fixed_assets] ([department_id])
SELECT p.[id]
FROM [dbo].[departments] AS p
INNER JOIN [dbo].[fixed_assets] AS c ON c.[department_id] = p.[id];

INSERT INTO [dbo].[fixed_assets] ([custodian_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[fixed_assets] AS c ON c.[custodian_id] = p.[id];

INSERT INTO [dbo].[depreciation_log] ([asset_id])
SELECT p.[id]
FROM [dbo].[fixed_assets] AS p
INNER JOIN [dbo].[depreciation_log] AS c ON c.[asset_id] = p.[id];

INSERT INTO [dbo].[boms] ([parent_product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[boms] AS c ON c.[parent_product_id] = p.[id];

INSERT INTO [dbo].[boms] ([child_product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[boms] AS c ON c.[child_product_id] = p.[id];

INSERT INTO [dbo].[work_orders] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[work_orders] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[work_orders] ([bom_id])
SELECT p.[id]
FROM [dbo].[boms] AS p
INNER JOIN [dbo].[work_orders] AS c ON c.[bom_id] = p.[id];

INSERT INTO [dbo].[work_orders] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[work_orders] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[work_order_materials] ([work_order_id])
SELECT p.[id]
FROM [dbo].[work_orders] AS p
INNER JOIN [dbo].[work_order_materials] AS c ON c.[work_order_id] = p.[id];
