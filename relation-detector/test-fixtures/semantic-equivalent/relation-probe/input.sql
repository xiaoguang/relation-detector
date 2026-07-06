
-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/01-complex-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[parent_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_id]) AS [child_count]
FROM [dbo].[departments] AS c
INNER JOIN [dbo].[departments] AS p ON c.[parent_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[parent_id])
  AND c.[parent_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);

SELECT c.[department_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[department_id]) AS [child_count]
FROM [dbo].[positions] AS c
INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[department_id])
  AND c.[department_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);

SELECT c.[department_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[department_id]) AS [child_count]
FROM [dbo].[employees] AS c
INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[department_id])
  AND c.[department_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);

SELECT c.[position_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[position_id]) AS [child_count]
FROM [dbo].[employees] AS c
INNER JOIN [dbo].[positions] AS p ON c.[position_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[positions] AS p2 WHERE p2.[id] = c.[position_id])
  AND c.[position_id] IN (SELECT p3.[id] FROM [dbo].[positions] AS p3);

SELECT c.[manager_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[manager_id]) AS [child_count]
FROM [dbo].[employees] AS c
INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[manager_id])
  AND c.[manager_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[employee_salary_log] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[attendance] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[leave_records] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[parent_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_id]) AS [child_count]
FROM [dbo].[permissions] AS c
INNER JOIN [dbo].[permissions] AS p ON c.[parent_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[permissions] AS p2 WHERE p2.[id] = c.[parent_id])
  AND c.[parent_id] IN (SELECT p3.[id] FROM [dbo].[permissions] AS p3);

SELECT c.[role_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[role_id]) AS [child_count]
FROM [dbo].[role_permissions] AS c
INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[roles] AS p2 WHERE p2.[id] = c.[role_id])
  AND c.[role_id] IN (SELECT p3.[id] FROM [dbo].[roles] AS p3);

SELECT c.[permission_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[permission_id]) AS [child_count]
FROM [dbo].[role_permissions] AS c
INNER JOIN [dbo].[permissions] AS p ON c.[permission_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[permissions] AS p2 WHERE p2.[id] = c.[permission_id])
  AND c.[permission_id] IN (SELECT p3.[id] FROM [dbo].[permissions] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[employee_roles] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[role_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[role_id]) AS [child_count]
FROM [dbo].[employee_roles] AS c
INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[roles] AS p2 WHERE p2.[id] = c.[role_id])
  AND c.[role_id] IN (SELECT p3.[id] FROM [dbo].[roles] AS p3);

SELECT c.[parent_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_id]) AS [child_count]
FROM [dbo].[product_categories] AS c
INNER JOIN [dbo].[product_categories] AS p ON c.[parent_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_categories] AS p2 WHERE p2.[id] = c.[parent_id])
  AND c.[parent_id] IN (SELECT p3.[id] FROM [dbo].[product_categories] AS p3);

SELECT c.[category_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[category_id]) AS [child_count]
FROM [dbo].[products] AS c
INNER JOIN [dbo].[product_categories] AS p ON c.[category_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_categories] AS p2 WHERE p2.[id] = c.[category_id])
  AND c.[category_id] IN (SELECT p3.[id] FROM [dbo].[product_categories] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[supplier_products] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[supplier_products] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[product_batches] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[product_batches] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[manager_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[manager_id]) AS [child_count]
FROM [dbo].[warehouses] AS c
INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[manager_id])
  AND c.[manager_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inventory] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[inventory] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[inventory] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inventory_transactions] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[inventory_transactions] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[inventory_transactions] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[department_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[department_id]) AS [child_count]
FROM [dbo].[purchase_requisitions] AS c
INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[department_id])
  AND c.[department_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/02-complex-queries-batch2.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[requester_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[requester_id]) AS [child_count]
FROM [dbo].[purchase_requisitions] AS c
INNER JOIN [dbo].[employees] AS p ON c.[requester_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[requester_id])
  AND c.[requester_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[requisition_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[requisition_id]) AS [child_count]
FROM [dbo].[purchase_requisition_items] AS c
INNER JOIN [dbo].[purchase_requisitions] AS p ON c.[requisition_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_requisitions] AS p2 WHERE p2.[id] = c.[requisition_id])
  AND c.[requisition_id] IN (SELECT p3.[id] FROM [dbo].[purchase_requisitions] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[purchase_requisition_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[purchase_orders] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[requisition_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[requisition_id]) AS [child_count]
FROM [dbo].[purchase_orders] AS c
INNER JOIN [dbo].[purchase_requisitions] AS p ON c.[requisition_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_requisitions] AS p2 WHERE p2.[id] = c.[requisition_id])
  AND c.[requisition_id] IN (SELECT p3.[id] FROM [dbo].[purchase_requisitions] AS p3);

SELECT c.[purchaser_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[purchaser_id]) AS [child_count]
FROM [dbo].[purchase_orders] AS c
INNER JOIN [dbo].[employees] AS p ON c.[purchaser_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[purchaser_id])
  AND c.[purchaser_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[purchase_order_items] AS c
INNER JOIN [dbo].[purchase_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[purchase_orders] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[purchase_order_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[purchase_receipts] AS c
INNER JOIN [dbo].[purchase_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[purchase_orders] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[purchase_receipts] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[receiver_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[receiver_id]) AS [child_count]
FROM [dbo].[purchase_receipts] AS c
INNER JOIN [dbo].[employees] AS p ON c.[receiver_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[receiver_id])
  AND c.[receiver_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[receipt_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[receipt_id]) AS [child_count]
FROM [dbo].[purchase_receipt_items] AS c
INNER JOIN [dbo].[purchase_receipts] AS p ON c.[receipt_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_receipts] AS p2 WHERE p2.[id] = c.[receipt_id])
  AND c.[receipt_id] IN (SELECT p3.[id] FROM [dbo].[purchase_receipts] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[purchase_receipt_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[purchase_receipt_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[sales_orders] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[salesperson_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[salesperson_id]) AS [child_count]
FROM [dbo].[sales_orders] AS c
INNER JOIN [dbo].[employees] AS p ON c.[salesperson_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[salesperson_id])
  AND c.[salesperson_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[sales_orders] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[sales_order_items] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[sales_order_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[sales_order_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[sales_returns] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[sales_returns] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[sales_returns] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[handler_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[handler_id]) AS [child_count]
FROM [dbo].[sales_returns] AS c
INNER JOIN [dbo].[employees] AS p ON c.[handler_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[handler_id])
  AND c.[handler_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[sales_returns] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[refund_voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[refund_voucher_id]) AS [child_count]
FROM [dbo].[sales_returns] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[refund_voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[refund_voucher_id])
  AND c.[refund_voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[return_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[return_id]) AS [child_count]
FROM [dbo].[sales_return_items] AS c
INNER JOIN [dbo].[sales_returns] AS p ON c.[return_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_returns] AS p2 WHERE p2.[id] = c.[return_id])
  AND c.[return_id] IN (SELECT p3.[id] FROM [dbo].[sales_returns] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/03-complex-queries-batch3.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[sales_return_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[sales_return_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[purchase_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[purchase_order_id]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[purchase_orders] AS p ON c.[purchase_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_orders] AS p2 WHERE p2.[id] = c.[purchase_order_id])
  AND c.[purchase_order_id] IN (SELECT p3.[id] FROM [dbo].[purchase_orders] AS p3);

SELECT c.[purchase_receipt_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[purchase_receipt_id]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[purchase_receipts] AS p ON c.[purchase_receipt_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_receipts] AS p2 WHERE p2.[id] = c.[purchase_receipt_id])
  AND c.[purchase_receipt_id] IN (SELECT p3.[id] FROM [dbo].[purchase_receipts] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[handler_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[handler_id]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[employees] AS p ON c.[handler_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[handler_id])
  AND c.[handler_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[refund_voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[refund_voucher_id]) AS [child_count]
FROM [dbo].[purchase_returns] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[refund_voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[refund_voucher_id])
  AND c.[refund_voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[return_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[return_id]) AS [child_count]
FROM [dbo].[purchase_return_items] AS c
INNER JOIN [dbo].[purchase_returns] AS p ON c.[return_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_returns] AS p2 WHERE p2.[id] = c.[return_id])
  AND c.[return_id] IN (SELECT p3.[id] FROM [dbo].[purchase_returns] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[purchase_return_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[purchase_return_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[damage_reports] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[reported_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[reported_by]) AS [child_count]
FROM [dbo].[damage_reports] AS c
INNER JOIN [dbo].[employees] AS p ON c.[reported_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[reported_by])
  AND c.[reported_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[damage_reports] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[executed_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[executed_by]) AS [child_count]
FROM [dbo].[damage_reports] AS c
INNER JOIN [dbo].[employees] AS p ON c.[executed_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[executed_by])
  AND c.[executed_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[voucher_id]) AS [child_count]
FROM [dbo].[damage_reports] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[voucher_id])
  AND c.[voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[report_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[report_id]) AS [child_count]
FROM [dbo].[damage_report_items] AS c
INNER JOIN [dbo].[damage_reports] AS p ON c.[report_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[damage_reports] AS p2 WHERE p2.[id] = c.[report_id])
  AND c.[report_id] IN (SELECT p3.[id] FROM [dbo].[damage_reports] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[damage_report_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[damage_report_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[parent_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_id]) AS [child_count]
FROM [dbo].[accounts] AS c
INNER JOIN [dbo].[accounts] AS p ON c.[parent_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounts] AS p2 WHERE p2.[id] = c.[parent_id])
  AND c.[parent_id] IN (SELECT p3.[id] FROM [dbo].[accounts] AS p3);

SELECT c.[prepared_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[prepared_by]) AS [child_count]
FROM [dbo].[vouchers] AS c
INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[prepared_by])
  AND c.[prepared_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[reviewed_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[reviewed_by]) AS [child_count]
FROM [dbo].[vouchers] AS c
INNER JOIN [dbo].[employees] AS p ON c.[reviewed_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[reviewed_by])
  AND c.[reviewed_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[posted_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[posted_by]) AS [child_count]
FROM [dbo].[vouchers] AS c
INNER JOIN [dbo].[employees] AS p ON c.[posted_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[posted_by])
  AND c.[posted_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[voucher_id]) AS [child_count]
FROM [dbo].[voucher_items] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[voucher_id])
  AND c.[voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[account_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[account_id]) AS [child_count]
FROM [dbo].[voucher_items] AS c
INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounts] AS p2 WHERE p2.[id] = c.[account_id])
  AND c.[account_id] IN (SELECT p3.[id] FROM [dbo].[accounts] AS p3);

SELECT c.[account_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[account_id]) AS [child_count]
FROM [dbo].[cashier_journals] AS c
INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounts] AS p2 WHERE p2.[id] = c.[account_id])
  AND c.[account_id] IN (SELECT p3.[id] FROM [dbo].[accounts] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/04-store-customer-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[cashier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[cashier_id]) AS [child_count]
FROM [dbo].[cashier_journals] AS c
INNER JOIN [dbo].[employees] AS p ON c.[cashier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[cashier_id])
  AND c.[cashier_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[voucher_id]) AS [child_count]
FROM [dbo].[cashier_journals] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[voucher_id])
  AND c.[voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[salary_payments] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[voucher_id]) AS [child_count]
FROM [dbo].[salary_payments] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[voucher_id])
  AND c.[voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[account_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[account_id]) AS [child_count]
FROM [dbo].[reconciliations] AS c
INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounts] AS p2 WHERE p2.[id] = c.[account_id])
  AND c.[account_id] IN (SELECT p3.[id] FROM [dbo].[accounts] AS p3);

SELECT c.[prepared_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[prepared_by]) AS [child_count]
FROM [dbo].[reconciliations] AS c
INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[prepared_by])
  AND c.[prepared_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[reviewed_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[reviewed_by]) AS [child_count]
FROM [dbo].[reconciliations] AS c
INNER JOIN [dbo].[employees] AS p ON c.[reviewed_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[reviewed_by])
  AND c.[reviewed_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[reconciliation_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[reconciliation_id]) AS [child_count]
FROM [dbo].[reconciliation_items] AS c
INNER JOIN [dbo].[reconciliations] AS p ON c.[reconciliation_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[reconciliations] AS p2 WHERE p2.[id] = c.[reconciliation_id])
  AND c.[reconciliation_id] IN (SELECT p3.[id] FROM [dbo].[reconciliations] AS p3);

SELECT c.[voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[voucher_id]) AS [child_count]
FROM [dbo].[settlements] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[voucher_id])
  AND c.[voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[prepared_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[prepared_by]) AS [child_count]
FROM [dbo].[settlements] AS c
INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[prepared_by])
  AND c.[prepared_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[settlements] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[settlement_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[settlement_id]) AS [child_count]
FROM [dbo].[settlement_items] AS c
INNER JOIN [dbo].[settlements] AS p ON c.[settlement_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[settlements] AS p2 WHERE p2.[id] = c.[settlement_id])
  AND c.[settlement_id] IN (SELECT p3.[id] FROM [dbo].[settlements] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[shipments] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[shipments] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[shipment_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[shipment_id]) AS [child_count]
FROM [dbo].[shipping_tracks] AS c
INNER JOIN [dbo].[shipments] AS p ON c.[shipment_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[shipments] AS p2 WHERE p2.[id] = c.[shipment_id])
  AND c.[shipment_id] IN (SELECT p3.[id] FROM [dbo].[shipments] AS p3);

SELECT c.[product_category_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_category_id]) AS [child_count]
FROM [dbo].[commission_rules] AS c
INNER JOIN [dbo].[product_categories] AS p ON c.[product_category_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_categories] AS p2 WHERE p2.[id] = c.[product_category_id])
  AND c.[product_category_id] IN (SELECT p3.[id] FROM [dbo].[product_categories] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[sales_commissions] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[sales_commissions] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[promotion_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[promotion_id]) AS [child_count]
FROM [dbo].[promotion_products] AS c
INNER JOIN [dbo].[promotions] AS p ON c.[promotion_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[promotions] AS p2 WHERE p2.[id] = c.[promotion_id])
  AND c.[promotion_id] IN (SELECT p3.[id] FROM [dbo].[promotions] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[promotion_products] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[category_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[category_id]) AS [child_count]
FROM [dbo].[promotion_products] AS c
INNER JOIN [dbo].[product_categories] AS p ON c.[category_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_categories] AS p2 WHERE p2.[id] = c.[category_id])
  AND c.[category_id] IN (SELECT p3.[id] FROM [dbo].[product_categories] AS p3);

SELECT c.[promotion_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[promotion_id]) AS [child_count]
FROM [dbo].[promotion_usages] AS c
INNER JOIN [dbo].[promotions] AS p ON c.[promotion_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[promotions] AS p2 WHERE p2.[id] = c.[promotion_id])
  AND c.[promotion_id] IN (SELECT p3.[id] FROM [dbo].[promotions] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[promotion_usages] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[promotion_usages] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[invoices] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[invoices] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[invoice_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[invoice_id]) AS [child_count]
FROM [dbo].[three_way_matching] AS c
INNER JOIN [dbo].[invoices] AS p ON c.[invoice_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[invoices] AS p2 WHERE p2.[id] = c.[invoice_id])
  AND c.[invoice_id] IN (SELECT p3.[id] FROM [dbo].[invoices] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/05-batch-expiry-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[purchase_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[purchase_order_id]) AS [child_count]
FROM [dbo].[three_way_matching] AS c
INNER JOIN [dbo].[purchase_orders] AS p ON c.[purchase_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_orders] AS p2 WHERE p2.[id] = c.[purchase_order_id])
  AND c.[purchase_order_id] IN (SELECT p3.[id] FROM [dbo].[purchase_orders] AS p3);

SELECT c.[purchase_receipt_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[purchase_receipt_id]) AS [child_count]
FROM [dbo].[three_way_matching] AS c
INNER JOIN [dbo].[purchase_receipts] AS p ON c.[purchase_receipt_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_receipts] AS p2 WHERE p2.[id] = c.[purchase_receipt_id])
  AND c.[purchase_receipt_id] IN (SELECT p3.[id] FROM [dbo].[purchase_receipts] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[three_way_matching] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[department_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[department_id]) AS [child_count]
FROM [dbo].[fixed_assets] AS c
INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[department_id])
  AND c.[department_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);

SELECT c.[custodian_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[custodian_id]) AS [child_count]
FROM [dbo].[fixed_assets] AS c
INNER JOIN [dbo].[employees] AS p ON c.[custodian_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[custodian_id])
  AND c.[custodian_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[asset_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[asset_id]) AS [child_count]
FROM [dbo].[depreciation_log] AS c
INNER JOIN [dbo].[fixed_assets] AS p ON c.[asset_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[fixed_assets] AS p2 WHERE p2.[id] = c.[asset_id])
  AND c.[asset_id] IN (SELECT p3.[id] FROM [dbo].[fixed_assets] AS p3);

SELECT c.[parent_product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_product_id]) AS [child_count]
FROM [dbo].[boms] AS c
INNER JOIN [dbo].[products] AS p ON c.[parent_product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[parent_product_id])
  AND c.[parent_product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[child_product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[child_product_id]) AS [child_count]
FROM [dbo].[boms] AS c
INNER JOIN [dbo].[products] AS p ON c.[child_product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[child_product_id])
  AND c.[child_product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[work_orders] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[bom_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[bom_id]) AS [child_count]
FROM [dbo].[work_orders] AS c
INNER JOIN [dbo].[boms] AS p ON c.[bom_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[boms] AS p2 WHERE p2.[id] = c.[bom_id])
  AND c.[bom_id] IN (SELECT p3.[id] FROM [dbo].[boms] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[work_orders] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[work_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[work_order_id]) AS [child_count]
FROM [dbo].[work_order_materials] AS c
INNER JOIN [dbo].[work_orders] AS p ON c.[work_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[work_orders] AS p2 WHERE p2.[id] = c.[work_order_id])
  AND c.[work_order_id] IN (SELECT p3.[id] FROM [dbo].[work_orders] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[work_order_materials] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[service_tickets] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[service_tickets] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[service_tickets] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[assigned_to] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[assigned_to]) AS [child_count]
FROM [dbo].[service_tickets] AS c
INNER JOIN [dbo].[employees] AS p ON c.[assigned_to] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[assigned_to])
  AND c.[assigned_to] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[prepared_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[prepared_by]) AS [child_count]
FROM [dbo].[contracts] AS c
INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[prepared_by])
  AND c.[prepared_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[contracts] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[contract_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[contract_id]) AS [child_count]
FROM [dbo].[contract_milestones] AS c
INNER JOIN [dbo].[contracts] AS p ON c.[contract_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[contracts] AS p2 WHERE p2.[id] = c.[contract_id])
  AND c.[contract_id] IN (SELECT p3.[id] FROM [dbo].[contracts] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[ar_aging_snapshots] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[ar_aging_snapshots] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[ap_aging_snapshots] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[ap_aging_snapshots] AS c
INNER JOIN [dbo].[purchase_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[purchase_orders] AS p3);

SELECT c.[verified_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[verified_by]) AS [child_count]
FROM [dbo].[tax_invoices] AS c
INNER JOIN [dbo].[employees] AS p ON c.[verified_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[verified_by])
  AND c.[verified_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[prepared_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[prepared_by]) AS [child_count]
FROM [dbo].[tax_filings] AS c
INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[prepared_by])
  AND c.[prepared_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inspection_standards] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/06-return-damage-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inspection_reports] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[inspection_reports] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[standard_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[standard_id]) AS [child_count]
FROM [dbo].[inspection_reports] AS c
INNER JOIN [dbo].[inspection_standards] AS p ON c.[standard_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[inspection_standards] AS p2 WHERE p2.[id] = c.[standard_id])
  AND c.[standard_id] IN (SELECT p3.[id] FROM [dbo].[inspection_standards] AS p3);

SELECT c.[inspector_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[inspector_id]) AS [child_count]
FROM [dbo].[inspection_reports] AS c
INNER JOIN [dbo].[employees] AS p ON c.[inspector_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[inspector_id])
  AND c.[inspector_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[workflow_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[workflow_id]) AS [child_count]
FROM [dbo].[approval_nodes] AS c
INNER JOIN [dbo].[approval_workflows] AS p ON c.[workflow_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[approval_workflows] AS p2 WHERE p2.[id] = c.[workflow_id])
  AND c.[workflow_id] IN (SELECT p3.[id] FROM [dbo].[approval_workflows] AS p3);

SELECT c.[workflow_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[workflow_id]) AS [child_count]
FROM [dbo].[approval_instances] AS c
INNER JOIN [dbo].[approval_workflows] AS p ON c.[workflow_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[approval_workflows] AS p2 WHERE p2.[id] = c.[workflow_id])
  AND c.[workflow_id] IN (SELECT p3.[id] FROM [dbo].[approval_workflows] AS p3);

SELECT c.[submitted_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[submitted_by]) AS [child_count]
FROM [dbo].[approval_instances] AS c
INNER JOIN [dbo].[employees] AS p ON c.[submitted_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[submitted_by])
  AND c.[submitted_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[instance_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[instance_id]) AS [child_count]
FROM [dbo].[approval_records] AS c
INNER JOIN [dbo].[approval_instances] AS p ON c.[instance_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[approval_instances] AS p2 WHERE p2.[id] = c.[instance_id])
  AND c.[instance_id] IN (SELECT p3.[id] FROM [dbo].[approval_instances] AS p3);

SELECT c.[node_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[node_id]) AS [child_count]
FROM [dbo].[approval_records] AS c
INNER JOIN [dbo].[approval_nodes] AS p ON c.[node_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[approval_nodes] AS p2 WHERE p2.[id] = c.[node_id])
  AND c.[node_id] IN (SELECT p3.[id] FROM [dbo].[approval_nodes] AS p3);

SELECT c.[approver_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approver_id]) AS [child_count]
FROM [dbo].[approval_records] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approver_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approver_id])
  AND c.[approver_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[department_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[department_id]) AS [child_count]
FROM [dbo].[projects] AS c
INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[department_id])
  AND c.[department_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);

SELECT c.[manager_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[manager_id]) AS [child_count]
FROM [dbo].[projects] AS c
INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[manager_id])
  AND c.[manager_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[project_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[project_id]) AS [child_count]
FROM [dbo].[project_costs] AS c
INNER JOIN [dbo].[projects] AS p ON c.[project_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[projects] AS p2 WHERE p2.[id] = c.[project_id])
  AND c.[project_id] IN (SELECT p3.[id] FROM [dbo].[projects] AS p3);

SELECT c.[account_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[account_id]) AS [child_count]
FROM [dbo].[foreign_currency_accounts] AS c
INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounts] AS p2 WHERE p2.[id] = c.[account_id])
  AND c.[account_id] IN (SELECT p3.[id] FROM [dbo].[accounts] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[performance_reviews] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[reviewer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[reviewer_id]) AS [child_count]
FROM [dbo].[performance_reviews] AS c
INNER JOIN [dbo].[employees] AS p ON c.[reviewer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[reviewer_id])
  AND c.[reviewer_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[serial_numbers] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[serial_numbers] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[serial_numbers] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[serial_number_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[serial_number_id]) AS [child_count]
FROM [dbo].[serial_number_logs] AS c
INNER JOIN [dbo].[serial_numbers] AS p ON c.[serial_number_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[serial_numbers] AS p2 WHERE p2.[id] = c.[serial_number_id])
  AND c.[serial_number_id] IN (SELECT p3.[id] FROM [dbo].[serial_numbers] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[consignment_inventory] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[consignment_inventory] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[consignment_inventory] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[consignment_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[consignment_id]) AS [child_count]
FROM [dbo].[consignment_consumptions] AS c
INNER JOIN [dbo].[consignment_inventory] AS p ON c.[consignment_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[consignment_inventory] AS p2 WHERE p2.[id] = c.[consignment_id])
  AND c.[consignment_id] IN (SELECT p3.[id] FROM [dbo].[consignment_inventory] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[price_change_logs] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[changed_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[changed_by]) AS [child_count]
FROM [dbo].[price_change_logs] AS c
INNER JOIN [dbo].[employees] AS p ON c.[changed_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[changed_by])
  AND c.[changed_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[tenant_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[tenant_id]) AS [child_count]
FROM [dbo].[ledger_books] AS c
INNER JOIN [dbo].[tenants] AS p ON c.[tenant_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[tenants] AS p2 WHERE p2.[id] = c.[tenant_id])
  AND c.[tenant_id] IN (SELECT p3.[id] FROM [dbo].[tenants] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/07-supplier-analysis-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[customer_addresses] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[supplier_addresses] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[ledger_book_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[ledger_book_id]) AS [child_count]
FROM [dbo].[accounting_periods] AS c
INNER JOIN [dbo].[ledger_books] AS p ON c.[ledger_book_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[ledger_books] AS p2 WHERE p2.[id] = c.[ledger_book_id])
  AND c.[ledger_book_id] IN (SELECT p3.[id] FROM [dbo].[ledger_books] AS p3);

SELECT c.[closed_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[closed_by]) AS [child_count]
FROM [dbo].[accounting_periods] AS c
INNER JOIN [dbo].[employees] AS p ON c.[closed_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[closed_by])
  AND c.[closed_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[period_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[period_id]) AS [child_count]
FROM [dbo].[period_close_jobs] AS c
INNER JOIN [dbo].[accounting_periods] AS p ON c.[period_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounting_periods] AS p2 WHERE p2.[id] = c.[period_id])
  AND c.[period_id] IN (SELECT p3.[id] FROM [dbo].[accounting_periods] AS p3);

SELECT c.[account_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[account_id]) AS [child_count]
FROM [dbo].[payment_receipts] AS c
INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounts] AS p2 WHERE p2.[id] = c.[account_id])
  AND c.[account_id] IN (SELECT p3.[id] FROM [dbo].[accounts] AS p3);

SELECT c.[handled_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[handled_by]) AS [child_count]
FROM [dbo].[payment_receipts] AS c
INNER JOIN [dbo].[employees] AS p ON c.[handled_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[handled_by])
  AND c.[handled_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[receipt_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[receipt_id]) AS [child_count]
FROM [dbo].[payment_receipt_allocations] AS c
INNER JOIN [dbo].[payment_receipts] AS p ON c.[receipt_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[payment_receipts] AS p2 WHERE p2.[id] = c.[receipt_id])
  AND c.[receipt_id] IN (SELECT p3.[id] FROM [dbo].[payment_receipts] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[stocktakes] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[created_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[created_by]) AS [child_count]
FROM [dbo].[stocktakes] AS c
INNER JOIN [dbo].[employees] AS p ON c.[created_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[created_by])
  AND c.[created_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[reviewed_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[reviewed_by]) AS [child_count]
FROM [dbo].[stocktakes] AS c
INNER JOIN [dbo].[employees] AS p ON c.[reviewed_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[reviewed_by])
  AND c.[reviewed_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[stocktake_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[stocktake_id]) AS [child_count]
FROM [dbo].[stocktake_items] AS c
INNER JOIN [dbo].[stocktakes] AS p ON c.[stocktake_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[stocktakes] AS p2 WHERE p2.[id] = c.[stocktake_id])
  AND c.[stocktake_id] IN (SELECT p3.[id] FROM [dbo].[stocktakes] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[stocktake_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[stocktake_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[from_warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[from_warehouse_id]) AS [child_count]
FROM [dbo].[stock_transfers] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[from_warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[from_warehouse_id])
  AND c.[from_warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[to_warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[to_warehouse_id]) AS [child_count]
FROM [dbo].[stock_transfers] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[to_warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[to_warehouse_id])
  AND c.[to_warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[requested_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[requested_by]) AS [child_count]
FROM [dbo].[stock_transfers] AS c
INNER JOIN [dbo].[employees] AS p ON c.[requested_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[requested_by])
  AND c.[requested_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[stock_transfers] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[transfer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[transfer_id]) AS [child_count]
FROM [dbo].[stock_transfer_items] AS c
INNER JOIN [dbo].[stock_transfers] AS p ON c.[transfer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[stock_transfers] AS p2 WHERE p2.[id] = c.[transfer_id])
  AND c.[transfer_id] IN (SELECT p3.[id] FROM [dbo].[stock_transfers] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[stock_transfer_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[stock_transfer_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inventory_reservations] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[inventory_reservations] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[inventory_reservations] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[production_routes] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[route_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[route_id]) AS [child_count]
FROM [dbo].[production_operations] AS c
INNER JOIN [dbo].[production_routes] AS p ON c.[route_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[production_routes] AS p2 WHERE p2.[id] = c.[route_id])
  AND c.[route_id] IN (SELECT p3.[id] FROM [dbo].[production_routes] AS p3);

SELECT c.[predecessor_operation_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[predecessor_operation_id]) AS [child_count]
FROM [dbo].[production_operations] AS c
INNER JOIN [dbo].[production_operations] AS p ON c.[predecessor_operation_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[production_operations] AS p2 WHERE p2.[id] = c.[predecessor_operation_id])
  AND c.[predecessor_operation_id] IN (SELECT p3.[id] FROM [dbo].[production_operations] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/08-common-system-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[employee_shift_assignments] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[shift_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[shift_id]) AS [child_count]
FROM [dbo].[employee_shift_assignments] AS c
INNER JOIN [dbo].[employee_shifts] AS p ON c.[shift_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employee_shifts] AS p2 WHERE p2.[id] = c.[shift_id])
  AND c.[shift_id] IN (SELECT p3.[id] FROM [dbo].[employee_shifts] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[employee_shift_assignments] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[production_plans] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[production_plans] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[planner_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[planner_id]) AS [child_count]
FROM [dbo].[production_plans] AS c
INNER JOIN [dbo].[employees] AS p ON c.[planner_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[planner_id])
  AND c.[planner_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[production_plans] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[plan_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[plan_id]) AS [child_count]
FROM [dbo].[mrp_runs] AS c
INNER JOIN [dbo].[production_plans] AS p ON c.[plan_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[production_plans] AS p2 WHERE p2.[id] = c.[plan_id])
  AND c.[plan_id] IN (SELECT p3.[id] FROM [dbo].[production_plans] AS p3);

SELECT c.[created_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[created_by]) AS [child_count]
FROM [dbo].[mrp_runs] AS c
INNER JOIN [dbo].[employees] AS p ON c.[created_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[created_by])
  AND c.[created_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[run_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[run_id]) AS [child_count]
FROM [dbo].[mrp_run_items] AS c
INNER JOIN [dbo].[mrp_runs] AS p ON c.[run_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[mrp_runs] AS p2 WHERE p2.[id] = c.[run_id])
  AND c.[run_id] IN (SELECT p3.[id] FROM [dbo].[mrp_runs] AS p3);

SELECT c.[parent_product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_product_id]) AS [child_count]
FROM [dbo].[mrp_run_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[parent_product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[parent_product_id])
  AND c.[parent_product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[component_product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[component_product_id]) AS [child_count]
FROM [dbo].[mrp_run_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[component_product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[component_product_id])
  AND c.[component_product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[suggested_supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[suggested_supplier_id]) AS [child_count]
FROM [dbo].[mrp_run_items] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[suggested_supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[suggested_supplier_id])
  AND c.[suggested_supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[work_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[work_order_id]) AS [child_count]
FROM [dbo].[work_order_operations] AS c
INNER JOIN [dbo].[work_orders] AS p ON c.[work_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[work_orders] AS p2 WHERE p2.[id] = c.[work_order_id])
  AND c.[work_order_id] IN (SELECT p3.[id] FROM [dbo].[work_orders] AS p3);

SELECT c.[operation_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[operation_id]) AS [child_count]
FROM [dbo].[work_order_operations] AS c
INNER JOIN [dbo].[production_operations] AS p ON c.[operation_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[production_operations] AS p2 WHERE p2.[id] = c.[operation_id])
  AND c.[operation_id] IN (SELECT p3.[id] FROM [dbo].[production_operations] AS p3);

SELECT c.[assigned_employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[assigned_employee_id]) AS [child_count]
FROM [dbo].[work_order_operations] AS c
INNER JOIN [dbo].[employees] AS p ON c.[assigned_employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[assigned_employee_id])
  AND c.[assigned_employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[work_order_operation_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[work_order_operation_id]) AS [child_count]
FROM [dbo].[operation_reports] AS c
INNER JOIN [dbo].[work_order_operations] AS p ON c.[work_order_operation_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[work_order_operations] AS p2 WHERE p2.[id] = c.[work_order_operation_id])
  AND c.[work_order_operation_id] IN (SELECT p3.[id] FROM [dbo].[work_order_operations] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[operation_reports] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[work_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[work_order_id]) AS [child_count]
FROM [dbo].[material_issues] AS c
INNER JOIN [dbo].[work_orders] AS p ON c.[work_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[work_orders] AS p2 WHERE p2.[id] = c.[work_order_id])
  AND c.[work_order_id] IN (SELECT p3.[id] FROM [dbo].[work_orders] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[material_issues] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[issued_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[issued_by]) AS [child_count]
FROM [dbo].[material_issues] AS c
INNER JOIN [dbo].[employees] AS p ON c.[issued_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[issued_by])
  AND c.[issued_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[issue_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[issue_id]) AS [child_count]
FROM [dbo].[material_issue_items] AS c
INNER JOIN [dbo].[material_issues] AS p ON c.[issue_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[material_issues] AS p2 WHERE p2.[id] = c.[issue_id])
  AND c.[issue_id] IN (SELECT p3.[id] FROM [dbo].[material_issues] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[material_issue_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[material_issue_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[work_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[work_order_id]) AS [child_count]
FROM [dbo].[finished_goods_receipts] AS c
INNER JOIN [dbo].[work_orders] AS p ON c.[work_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[work_orders] AS p2 WHERE p2.[id] = c.[work_order_id])
  AND c.[work_order_id] IN (SELECT p3.[id] FROM [dbo].[work_orders] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[finished_goods_receipts] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[finished_goods_receipts] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/09-real-world-scenarios.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[finished_goods_receipts] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[received_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[received_by]) AS [child_count]
FROM [dbo].[finished_goods_receipts] AS c
INNER JOIN [dbo].[employees] AS p ON c.[received_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[received_by])
  AND c.[received_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[standard_costs] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[standard_costs] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inventory_cost_layers] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[inventory_cost_layers] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[inventory_cost_layers] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inventory_valuation_snapshots] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[inventory_valuation_snapshots] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[work_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[work_order_id]) AS [child_count]
FROM [dbo].[work_order_costs] AS c
INNER JOIN [dbo].[work_orders] AS p ON c.[work_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[work_orders] AS p2 WHERE p2.[id] = c.[work_order_id])
  AND c.[work_order_id] IN (SELECT p3.[id] FROM [dbo].[work_orders] AS p3);

SELECT c.[sales_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[sales_order_id]) AS [child_count]
FROM [dbo].[cogs_entries] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[sales_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[sales_order_id])
  AND c.[sales_order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[sales_order_item_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[sales_order_item_id]) AS [child_count]
FROM [dbo].[cogs_entries] AS c
INNER JOIN [dbo].[sales_order_items] AS p ON c.[sales_order_item_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_order_items] AS p2 WHERE p2.[id] = c.[sales_order_item_id])
  AND c.[sales_order_item_id] IN (SELECT p3.[id] FROM [dbo].[sales_order_items] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[cogs_entries] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[cogs_entries] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[voucher_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[voucher_id]) AS [child_count]
FROM [dbo].[cogs_entries] AS c
INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[vouchers] AS p2 WHERE p2.[id] = c.[voucher_id])
  AND c.[voucher_id] IN (SELECT p3.[id] FROM [dbo].[vouchers] AS p3);

SELECT c.[parent_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[parent_id]) AS [child_count]
FROM [dbo].[account_subjects] AS c
INNER JOIN [dbo].[account_subjects] AS p ON c.[parent_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[account_subjects] AS p2 WHERE p2.[id] = c.[parent_id])
  AND c.[parent_id] IN (SELECT p3.[id] FROM [dbo].[account_subjects] AS p3);

SELECT c.[ledger_book_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[ledger_book_id]) AS [child_count]
FROM [dbo].[opening_balances] AS c
INNER JOIN [dbo].[ledger_books] AS p ON c.[ledger_book_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[ledger_books] AS p2 WHERE p2.[id] = c.[ledger_book_id])
  AND c.[ledger_book_id] IN (SELECT p3.[id] FROM [dbo].[ledger_books] AS p3);

SELECT c.[subject_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[subject_id]) AS [child_count]
FROM [dbo].[opening_balances] AS c
INNER JOIN [dbo].[account_subjects] AS p ON c.[subject_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[account_subjects] AS p2 WHERE p2.[id] = c.[subject_id])
  AND c.[subject_id] IN (SELECT p3.[id] FROM [dbo].[account_subjects] AS p3);

SELECT c.[ledger_book_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[ledger_book_id]) AS [child_count]
FROM [dbo].[account_balances] AS c
INNER JOIN [dbo].[ledger_books] AS p ON c.[ledger_book_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[ledger_books] AS p2 WHERE p2.[id] = c.[ledger_book_id])
  AND c.[ledger_book_id] IN (SELECT p3.[id] FROM [dbo].[ledger_books] AS p3);

SELECT c.[subject_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[subject_id]) AS [child_count]
FROM [dbo].[account_balances] AS c
INNER JOIN [dbo].[account_subjects] AS p ON c.[subject_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[account_subjects] AS p2 WHERE p2.[id] = c.[subject_id])
  AND c.[subject_id] IN (SELECT p3.[id] FROM [dbo].[account_subjects] AS p3);

SELECT c.[ledger_book_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[ledger_book_id]) AS [child_count]
FROM [dbo].[budget_versions] AS c
INNER JOIN [dbo].[ledger_books] AS p ON c.[ledger_book_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[ledger_books] AS p2 WHERE p2.[id] = c.[ledger_book_id])
  AND c.[ledger_book_id] IN (SELECT p3.[id] FROM [dbo].[ledger_books] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[budget_versions] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[version_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[version_id]) AS [child_count]
FROM [dbo].[budget_items] AS c
INNER JOIN [dbo].[budget_versions] AS p ON c.[version_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[budget_versions] AS p2 WHERE p2.[id] = c.[version_id])
  AND c.[version_id] IN (SELECT p3.[id] FROM [dbo].[budget_versions] AS p3);

SELECT c.[department_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[department_id]) AS [child_count]
FROM [dbo].[budget_items] AS c
INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[departments] AS p2 WHERE p2.[id] = c.[department_id])
  AND c.[department_id] IN (SELECT p3.[id] FROM [dbo].[departments] AS p3);

SELECT c.[subject_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[subject_id]) AS [child_count]
FROM [dbo].[budget_items] AS c
INNER JOIN [dbo].[account_subjects] AS p ON c.[subject_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[account_subjects] AS p2 WHERE p2.[id] = c.[subject_id])
  AND c.[subject_id] IN (SELECT p3.[id] FROM [dbo].[account_subjects] AS p3);

SELECT c.[sales_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[sales_order_id]) AS [child_count]
FROM [dbo].[ar_invoices] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[sales_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[sales_order_id])
  AND c.[sales_order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[ar_invoices] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/10-enterprise-extension-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[purchase_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[purchase_order_id]) AS [child_count]
FROM [dbo].[ap_invoices] AS c
INNER JOIN [dbo].[purchase_orders] AS p ON c.[purchase_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_orders] AS p2 WHERE p2.[id] = c.[purchase_order_id])
  AND c.[purchase_order_id] IN (SELECT p3.[id] FROM [dbo].[purchase_orders] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[ap_invoices] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[supplier_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[supplier_id]) AS [child_count]
FROM [dbo].[payment_requests] AS c
INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[suppliers] AS p2 WHERE p2.[id] = c.[supplier_id])
  AND c.[supplier_id] IN (SELECT p3.[id] FROM [dbo].[suppliers] AS p3);

SELECT c.[requested_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[requested_by]) AS [child_count]
FROM [dbo].[payment_requests] AS c
INNER JOIN [dbo].[employees] AS p ON c.[requested_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[requested_by])
  AND c.[requested_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[request_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[request_id]) AS [child_count]
FROM [dbo].[payment_request_items] AS c
INNER JOIN [dbo].[payment_requests] AS p ON c.[request_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[payment_requests] AS p2 WHERE p2.[id] = c.[request_id])
  AND c.[request_id] IN (SELECT p3.[id] FROM [dbo].[payment_requests] AS p3);

SELECT c.[ap_invoice_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[ap_invoice_id]) AS [child_count]
FROM [dbo].[payment_request_items] AS c
INNER JOIN [dbo].[ap_invoices] AS p ON c.[ap_invoice_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[ap_invoices] AS p2 WHERE p2.[id] = c.[ap_invoice_id])
  AND c.[ap_invoice_id] IN (SELECT p3.[id] FROM [dbo].[ap_invoices] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[warehouse_zones] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[zone_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[zone_id]) AS [child_count]
FROM [dbo].[warehouse_locations] AS c
INNER JOIN [dbo].[warehouse_zones] AS p ON c.[zone_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouse_zones] AS p2 WHERE p2.[id] = c.[zone_id])
  AND c.[zone_id] IN (SELECT p3.[id] FROM [dbo].[warehouse_zones] AS p3);

SELECT c.[location_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[location_id]) AS [child_count]
FROM [dbo].[inventory_location_balances] AS c
INNER JOIN [dbo].[warehouse_locations] AS p ON c.[location_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouse_locations] AS p2 WHERE p2.[id] = c.[location_id])
  AND c.[location_id] IN (SELECT p3.[id] FROM [dbo].[warehouse_locations] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[inventory_location_balances] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[inventory_location_balances] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[receipt_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[receipt_id]) AS [child_count]
FROM [dbo].[putaway_tasks] AS c
INNER JOIN [dbo].[purchase_receipts] AS p ON c.[receipt_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[purchase_receipts] AS p2 WHERE p2.[id] = c.[receipt_id])
  AND c.[receipt_id] IN (SELECT p3.[id] FROM [dbo].[purchase_receipts] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[putaway_tasks] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[putaway_tasks] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[to_location_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[to_location_id]) AS [child_count]
FROM [dbo].[putaway_tasks] AS c
INNER JOIN [dbo].[warehouse_locations] AS p ON c.[to_location_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouse_locations] AS p2 WHERE p2.[id] = c.[to_location_id])
  AND c.[to_location_id] IN (SELECT p3.[id] FROM [dbo].[warehouse_locations] AS p3);

SELECT c.[assigned_to] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[assigned_to]) AS [child_count]
FROM [dbo].[putaway_tasks] AS c
INNER JOIN [dbo].[employees] AS p ON c.[assigned_to] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[assigned_to])
  AND c.[assigned_to] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[sales_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[sales_order_id]) AS [child_count]
FROM [dbo].[picking_tasks] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[sales_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[sales_order_id])
  AND c.[sales_order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[picking_tasks] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[assigned_to] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[assigned_to]) AS [child_count]
FROM [dbo].[picking_tasks] AS c
INNER JOIN [dbo].[employees] AS p ON c.[assigned_to] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[assigned_to])
  AND c.[assigned_to] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[picking_task_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[picking_task_id]) AS [child_count]
FROM [dbo].[picking_task_items] AS c
INNER JOIN [dbo].[picking_tasks] AS p ON c.[picking_task_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[picking_tasks] AS p2 WHERE p2.[id] = c.[picking_task_id])
  AND c.[picking_task_id] IN (SELECT p3.[id] FROM [dbo].[picking_tasks] AS p3);

SELECT c.[sales_order_item_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[sales_order_item_id]) AS [child_count]
FROM [dbo].[picking_task_items] AS c
INNER JOIN [dbo].[sales_order_items] AS p ON c.[sales_order_item_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_order_items] AS p2 WHERE p2.[id] = c.[sales_order_item_id])
  AND c.[sales_order_item_id] IN (SELECT p3.[id] FROM [dbo].[sales_order_items] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[picking_task_items] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[picking_task_items] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[location_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[location_id]) AS [child_count]
FROM [dbo].[picking_task_items] AS c
INNER JOIN [dbo].[warehouse_locations] AS p ON c.[location_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouse_locations] AS p2 WHERE p2.[id] = c.[location_id])
  AND c.[location_id] IN (SELECT p3.[id] FROM [dbo].[warehouse_locations] AS p3);

SELECT c.[service_ticket_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[service_ticket_id]) AS [child_count]
FROM [dbo].[repair_orders] AS c
INNER JOIN [dbo].[service_tickets] AS p ON c.[service_ticket_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[service_tickets] AS p2 WHERE p2.[id] = c.[service_ticket_id])
  AND c.[service_ticket_id] IN (SELECT p3.[id] FROM [dbo].[service_tickets] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[repair_orders] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[repair_orders] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);


-- ============================================================
-- Source sample-data/sqlserver/2025/04-queries/11-erp-deep-scenario-queries.sql
-- Relation-probe benchmark: dense JOIN + EXISTS + IN predicates.
-- ============================================================

-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

SELECT c.[serial_number_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[serial_number_id]) AS [child_count]
FROM [dbo].[repair_orders] AS c
INNER JOIN [dbo].[serial_numbers] AS p ON c.[serial_number_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[serial_numbers] AS p2 WHERE p2.[id] = c.[serial_number_id])
  AND c.[serial_number_id] IN (SELECT p3.[id] FROM [dbo].[serial_numbers] AS p3);

SELECT c.[technician_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[technician_id]) AS [child_count]
FROM [dbo].[repair_orders] AS c
INNER JOIN [dbo].[employees] AS p ON c.[technician_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[technician_id])
  AND c.[technician_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[repair_order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[repair_order_id]) AS [child_count]
FROM [dbo].[repair_order_parts] AS c
INNER JOIN [dbo].[repair_orders] AS p ON c.[repair_order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[repair_orders] AS p2 WHERE p2.[id] = c.[repair_order_id])
  AND c.[repair_order_id] IN (SELECT p3.[id] FROM [dbo].[repair_orders] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[repair_order_parts] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[batch_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[batch_id]) AS [child_count]
FROM [dbo].[repair_order_parts] AS c
INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_batches] AS p2 WHERE p2.[id] = c.[batch_id])
  AND c.[batch_id] IN (SELECT p3.[id] FROM [dbo].[product_batches] AS p3);

SELECT c.[issued_from_warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[issued_from_warehouse_id]) AS [child_count]
FROM [dbo].[repair_order_parts] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[issued_from_warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[issued_from_warehouse_id])
  AND c.[issued_from_warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[requested_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[requested_by]) AS [child_count]
FROM [dbo].[master_data_change_requests] AS c
INNER JOIN [dbo].[employees] AS p ON c.[requested_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[requested_by])
  AND c.[requested_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[approved_by] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[approved_by]) AS [child_count]
FROM [dbo].[master_data_change_requests] AS c
INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[approved_by])
  AND c.[approved_by] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[request_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[request_id]) AS [child_count]
FROM [dbo].[master_data_change_items] AS c
INNER JOIN [dbo].[master_data_change_requests] AS p ON c.[request_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[master_data_change_requests] AS p2 WHERE p2.[id] = c.[request_id])
  AND c.[request_id] IN (SELECT p3.[id] FROM [dbo].[master_data_change_requests] AS p3);

SELECT c.[role_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[role_id]) AS [child_count]
FROM [dbo].[data_permission_scopes] AS c
INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[roles] AS p2 WHERE p2.[id] = c.[role_id])
  AND c.[role_id] IN (SELECT p3.[id] FROM [dbo].[roles] AS p3);

SELECT c.[employee_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[employee_id]) AS [child_count]
FROM [dbo].[sensitive_access_logs] AS c
INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[employees] AS p2 WHERE p2.[id] = c.[employee_id])
  AND c.[employee_id] IN (SELECT p3.[id] FROM [dbo].[employees] AS p3);

SELECT c.[accounting_period_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[accounting_period_id]) AS [child_count]
FROM [dbo].[fiscal_calendar] AS c
INNER JOIN [dbo].[accounting_periods] AS p ON c.[accounting_period_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[accounting_periods] AS p2 WHERE p2.[id] = c.[accounting_period_id])
  AND c.[accounting_period_id] IN (SELECT p3.[id] FROM [dbo].[accounting_periods] AS p3);

SELECT c.[source_category_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[source_category_id]) AS [child_count]
FROM [dbo].[category_dim] AS c
INNER JOIN [dbo].[product_categories] AS p ON c.[source_category_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[product_categories] AS p2 WHERE p2.[id] = c.[source_category_id])
  AND c.[source_category_id] IN (SELECT p3.[id] FROM [dbo].[product_categories] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[payments] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[payments] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[receipt_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[receipt_id]) AS [child_count]
FROM [dbo].[payments] AS c
INNER JOIN [dbo].[payment_receipts] AS p ON c.[receipt_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[payment_receipts] AS p2 WHERE p2.[id] = c.[receipt_id])
  AND c.[receipt_id] IN (SELECT p3.[id] FROM [dbo].[payment_receipts] AS p3);

SELECT c.[journal_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[journal_id]) AS [child_count]
FROM [dbo].[payments] AS c
INNER JOIN [dbo].[cashier_journals] AS p ON c.[journal_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[cashier_journals] AS p2 WHERE p2.[id] = c.[journal_id])
  AND c.[journal_id] IN (SELECT p3.[id] FROM [dbo].[cashier_journals] AS p3);

SELECT c.[order_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_orders] AS p2 WHERE p2.[id] = c.[order_id])
  AND c.[order_id] IN (SELECT p3.[id] FROM [dbo].[sales_orders] AS p3);

SELECT c.[order_item_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[order_item_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[sales_order_items] AS p ON c.[order_item_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[sales_order_items] AS p2 WHERE p2.[id] = c.[order_item_id])
  AND c.[order_item_id] IN (SELECT p3.[id] FROM [dbo].[sales_order_items] AS p3);

SELECT c.[customer_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[customer_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[customers] AS p2 WHERE p2.[id] = c.[customer_id])
  AND c.[customer_id] IN (SELECT p3.[id] FROM [dbo].[customers] AS p3);

SELECT c.[product_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[product_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[products] AS p2 WHERE p2.[id] = c.[product_id])
  AND c.[product_id] IN (SELECT p3.[id] FROM [dbo].[products] AS p3);

SELECT c.[category_dim_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[category_dim_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[category_dim] AS p ON c.[category_dim_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[category_dim] AS p2 WHERE p2.[id] = c.[category_dim_id])
  AND c.[category_dim_id] IN (SELECT p3.[id] FROM [dbo].[category_dim] AS p3);

SELECT c.[warehouse_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[warehouse_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[warehouses] AS p2 WHERE p2.[id] = c.[warehouse_id])
  AND c.[warehouse_id] IN (SELECT p3.[id] FROM [dbo].[warehouses] AS p3);

SELECT c.[region_dim_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[region_dim_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[region_dim] AS p ON c.[region_dim_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[region_dim] AS p2 WHERE p2.[id] = c.[region_dim_id])
  AND c.[region_dim_id] IN (SELECT p3.[id] FROM [dbo].[region_dim] AS p3);

SELECT c.[fiscal_date] AS [child_id],
       p.[calendar_date] AS [parent_id],
       CASE WHEN p.[calendar_date] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[fiscal_date]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[fiscal_calendar] AS p ON c.[fiscal_date] = p.[calendar_date]
WHERE EXISTS (SELECT 1 FROM [dbo].[fiscal_calendar] AS p2 WHERE p2.[calendar_date] = c.[fiscal_date])
  AND c.[fiscal_date] IN (SELECT p3.[calendar_date] FROM [dbo].[fiscal_calendar] AS p3);

SELECT c.[payment_id] AS [child_id],
       p.[id] AS [parent_id],
       CASE WHEN p.[id] IS NULL THEN 0 ELSE 1 END AS [has_parent],
       COUNT(*) OVER (PARTITION BY c.[payment_id]) AS [child_count]
FROM [dbo].[sales_fact] AS c
INNER JOIN [dbo].[payments] AS p ON c.[payment_id] = p.[id]
WHERE EXISTS (SELECT 1 FROM [dbo].[payments] AS p2 WHERE p2.[id] = c.[payment_id])
  AND c.[payment_id] IN (SELECT p3.[id] FROM [dbo].[payments] AS p3);
