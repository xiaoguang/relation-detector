-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[purchase_receipt_items] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[purchase_receipt_items] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[sales_orders] ([customer_id])
SELECT p.[id]
FROM [dbo].[customers] AS p
INNER JOIN [dbo].[sales_orders] AS c ON c.[customer_id] = p.[id];

INSERT INTO [dbo].[sales_orders] ([salesperson_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[sales_orders] AS c ON c.[salesperson_id] = p.[id];

INSERT INTO [dbo].[sales_orders] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[sales_orders] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[sales_order_items] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[sales_order_items] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[sales_order_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[sales_order_items] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[sales_order_items] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[sales_order_items] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[sales_returns] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[sales_returns] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[sales_returns] ([customer_id])
SELECT p.[id]
FROM [dbo].[customers] AS p
INNER JOIN [dbo].[sales_returns] AS c ON c.[customer_id] = p.[id];

INSERT INTO [dbo].[sales_returns] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[sales_returns] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[sales_returns] ([handler_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[sales_returns] AS c ON c.[handler_id] = p.[id];

INSERT INTO [dbo].[sales_returns] ([approved_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[sales_returns] AS c ON c.[approved_by] = p.[id];

INSERT INTO [dbo].[sales_returns] ([refund_voucher_id])
SELECT p.[id]
FROM [dbo].[vouchers] AS p
INNER JOIN [dbo].[sales_returns] AS c ON c.[refund_voucher_id] = p.[id];

INSERT INTO [dbo].[sales_return_items] ([return_id])
SELECT p.[id]
FROM [dbo].[sales_returns] AS p
INNER JOIN [dbo].[sales_return_items] AS c ON c.[return_id] = p.[id];

INSERT INTO [dbo].[sales_return_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[sales_return_items] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[sales_return_items] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[sales_return_items] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[purchase_returns] ([purchase_order_id])
SELECT p.[id]
FROM [dbo].[purchase_orders] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[purchase_order_id] = p.[id];

INSERT INTO [dbo].[purchase_returns] ([purchase_receipt_id])
SELECT p.[id]
FROM [dbo].[purchase_receipts] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[purchase_receipt_id] = p.[id];

INSERT INTO [dbo].[purchase_returns] ([supplier_id])
SELECT p.[id]
FROM [dbo].[suppliers] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[supplier_id] = p.[id];

INSERT INTO [dbo].[purchase_returns] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[purchase_returns] AS c ON c.[warehouse_id] = p.[id];
