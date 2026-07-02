-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[work_order_materials] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[work_order_materials] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[service_tickets] ([customer_id])
SELECT p.[id]
FROM [dbo].[customers] AS p
INNER JOIN [dbo].[service_tickets] AS c ON c.[customer_id] = p.[id];

INSERT INTO [dbo].[service_tickets] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[service_tickets] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[service_tickets] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[service_tickets] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[service_tickets] ([assigned_to])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[service_tickets] AS c ON c.[assigned_to] = p.[id];

INSERT INTO [dbo].[contracts] ([prepared_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[contracts] AS c ON c.[prepared_by] = p.[id];

INSERT INTO [dbo].[contracts] ([approved_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[contracts] AS c ON c.[approved_by] = p.[id];

INSERT INTO [dbo].[contract_milestones] ([contract_id])
SELECT p.[id]
FROM [dbo].[contracts] AS p
INNER JOIN [dbo].[contract_milestones] AS c ON c.[contract_id] = p.[id];

INSERT INTO [dbo].[ar_aging_snapshots] ([customer_id])
SELECT p.[id]
FROM [dbo].[customers] AS p
INNER JOIN [dbo].[ar_aging_snapshots] AS c ON c.[customer_id] = p.[id];

INSERT INTO [dbo].[ar_aging_snapshots] ([order_id])
SELECT p.[id]
FROM [dbo].[sales_orders] AS p
INNER JOIN [dbo].[ar_aging_snapshots] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[ap_aging_snapshots] ([supplier_id])
SELECT p.[id]
FROM [dbo].[suppliers] AS p
INNER JOIN [dbo].[ap_aging_snapshots] AS c ON c.[supplier_id] = p.[id];

INSERT INTO [dbo].[ap_aging_snapshots] ([order_id])
SELECT p.[id]
FROM [dbo].[purchase_orders] AS p
INNER JOIN [dbo].[ap_aging_snapshots] AS c ON c.[order_id] = p.[id];

INSERT INTO [dbo].[tax_invoices] ([verified_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[tax_invoices] AS c ON c.[verified_by] = p.[id];

INSERT INTO [dbo].[tax_filings] ([prepared_by])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[tax_filings] AS c ON c.[prepared_by] = p.[id];

INSERT INTO [dbo].[inspection_standards] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[inspection_standards] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[inspection_reports] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[inspection_reports] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[inspection_reports] ([batch_id])
SELECT p.[id]
FROM [dbo].[product_batches] AS p
INNER JOIN [dbo].[inspection_reports] AS c ON c.[batch_id] = p.[id];

INSERT INTO [dbo].[inspection_reports] ([standard_id])
SELECT p.[id]
FROM [dbo].[inspection_standards] AS p
INNER JOIN [dbo].[inspection_reports] AS c ON c.[standard_id] = p.[id];

INSERT INTO [dbo].[inspection_reports] ([inspector_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[inspection_reports] AS c ON c.[inspector_id] = p.[id];

INSERT INTO [dbo].[approval_nodes] ([workflow_id])
SELECT p.[id]
FROM [dbo].[approval_workflows] AS p
INNER JOIN [dbo].[approval_nodes] AS c ON c.[workflow_id] = p.[id];
