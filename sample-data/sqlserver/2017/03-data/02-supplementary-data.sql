-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[inventory] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[inventory] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[inventory] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[inventory] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[inventory] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[inventory] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[inventory_transactions] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[inventory_transactions] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[inventory_transactions] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[inventory_transactions] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[inventory_transactions] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[inventory_transactions] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[purchase_requisitions] ([department_id])
SELECT p.[id]
FROM [dbo].[departments] AS p
INNER JOIN [dbo].[purchase_requisitions] AS c ON c.[department_id] = p.[id];

INSERT INTO [dbo].[purchase_requisitions] ([requester_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[purchase_requisitions] AS c ON c.[requester_id] = p.[id];

INSERT INTO [dbo].[purchase_requisition_items] ([requisition_id])
SELECT p.[id]
FROM [dbo].[purchase_requisitions] AS p
INNER JOIN [dbo].[purchase_requisition_items] AS c ON c.[requisition_id] = p.[id];

INSERT INTO [dbo].[purchase_requisition_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[purchase_requisition_items] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[purchase_orders] ([supplier_id])
SELECT p.[id]
FROM [dbo].[suppliers] AS p
INNER JOIN [dbo].[purchase_orders] AS c ON c.[supplier_id] = p.[id];

INSERT INTO [dbo].[purchase_orders] ([requisition_id])
SELECT p.[id]
FROM [dbo].[purchase_requisitions] AS p
INNER JOIN [dbo].[purchase_orders] AS c ON c.[requisition_id] = p.[id];

INSERT INTO [dbo].[purchase_orders] ([purchaser_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[purchase_orders] AS c ON c.[purchaser_id] = p.[id];

INSERT INTO [dbo].[purchase_order_items] ([order_id])
SELECT p.[id]
FROM [dbo].[purchase_orders] AS p
INNER JOIN [dbo].[purchase_order_items] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[purchase_order_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[purchase_order_items] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[purchase_receipts] ([order_id])
SELECT p.[id]
FROM [dbo].[purchase_orders] AS p
INNER JOIN [dbo].[purchase_receipts] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[purchase_receipts] ([warehouse_id])
SELECT p.[id]
FROM [dbo].[warehouses] AS p
INNER JOIN [dbo].[purchase_receipts] AS c ON c.[warehouse_id] = p.[id];

INSERT INTO [dbo].[purchase_receipts] ([receiver_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[purchase_receipts] AS c ON c.[receiver_id] = p.[id];

INSERT INTO [dbo].[purchase_receipt_items] ([receipt_id])
SELECT p.[id]
FROM [dbo].[purchase_receipts] AS p
INNER JOIN [dbo].[purchase_receipt_items] AS c ON c.[receipt_id] = p.[id];

INSERT INTO [dbo].[purchase_receipt_items] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[purchase_receipt_items] AS c ON c.[product_id] = p.[id];
