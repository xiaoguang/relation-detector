-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

INSERT INTO [dbo].[departments] ([parent_id])
SELECT p.[id]
FROM [dbo].[departments] AS p
INNER JOIN [dbo].[departments] AS c ON c.[parent_id] = p.[id];

INSERT INTO [dbo].[positions] ([department_id])
SELECT p.[id]
FROM [dbo].[departments] AS p
INNER JOIN [dbo].[positions] AS c ON c.[department_id] = p.[id];

INSERT INTO [dbo].[employees] ([department_id])
SELECT p.[id]
FROM [dbo].[departments] AS p
INNER JOIN [dbo].[employees] AS c ON c.[department_id] = p.[id];

INSERT INTO [dbo].[employees] ([position_id])
SELECT p.[id]
FROM [dbo].[positions] AS p
INNER JOIN [dbo].[employees] AS c ON c.[position_id] = p.[id];

INSERT INTO [dbo].[employees] ([manager_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[employees] AS c ON c.[manager_id] = p.[id];

INSERT INTO [dbo].[employee_salary_log] ([employee_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[employee_salary_log] AS c ON c.[employee_id] = p.[id];

INSERT INTO [dbo].[attendance] ([employee_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[attendance] AS c ON c.[employee_id] = p.[id];

INSERT INTO [dbo].[leave_records] ([employee_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[leave_records] AS c ON c.[employee_id] = p.[id];

INSERT INTO [dbo].[permissions] ([parent_id])
SELECT p.[id]
FROM [dbo].[permissions] AS p
INNER JOIN [dbo].[permissions] AS c ON c.[parent_id] = p.[id];

INSERT INTO [dbo].[role_permissions] ([role_id])
SELECT p.[id]
FROM [dbo].[roles] AS p
INNER JOIN [dbo].[role_permissions] AS c ON c.[role_id] = p.[id];

INSERT INTO [dbo].[role_permissions] ([permission_id])
SELECT p.[id]
FROM [dbo].[permissions] AS p
INNER JOIN [dbo].[role_permissions] AS c ON c.[permission_id] = p.[id];

INSERT INTO [dbo].[employee_roles] ([employee_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[employee_roles] AS c ON c.[employee_id] = p.[id];

INSERT INTO [dbo].[employee_roles] ([role_id])
SELECT p.[id]
FROM [dbo].[roles] AS p
INNER JOIN [dbo].[employee_roles] AS c ON c.[role_id] = p.[id];

INSERT INTO [dbo].[product_categories] ([parent_id])
SELECT p.[id]
FROM [dbo].[product_categories] AS p
INNER JOIN [dbo].[product_categories] AS c ON c.[parent_id] = p.[id];

INSERT INTO [dbo].[products] ([category_id])
SELECT p.[id]
FROM [dbo].[product_categories] AS p
INNER JOIN [dbo].[products] AS c ON c.[category_id] = p.[id];

INSERT INTO [dbo].[supplier_products] ([supplier_id])
SELECT p.[id]
FROM [dbo].[suppliers] AS p
INNER JOIN [dbo].[supplier_products] AS c ON c.[supplier_id] = p.[id];

INSERT INTO [dbo].[supplier_products] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[supplier_products] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[product_batches] ([product_id])
SELECT p.[id]
FROM [dbo].[products] AS p
INNER JOIN [dbo].[product_batches] AS c ON c.[product_id] = p.[id];

INSERT INTO [dbo].[product_batches] ([supplier_id])
SELECT p.[id]
FROM [dbo].[suppliers] AS p
INNER JOIN [dbo].[product_batches] AS c ON c.[supplier_id] = p.[id];

INSERT INTO [dbo].[warehouses] ([manager_id])
SELECT p.[id]
FROM [dbo].[employees] AS p
INNER JOIN [dbo].[warehouses] AS c ON c.[manager_id] = p.[id];
