-- ============================================================
-- SQL Server natural business table-valued functions.
-- These are business reporting functions, not relation coverage fixtures.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.fn_department_hierarchy
CREATE OR ALTER FUNCTION [dbo].[fn_department_hierarchy]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[id] AS [department_id], c.[name] AS [department_name], p.[id] AS [parent_department_id], p.[name] AS [parent_department_name] FROM [dbo].[departments] AS c LEFT JOIN [dbo].[departments] AS p ON c.[parent_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_position_staffing
CREATE OR ALTER FUNCTION [dbo].[fn_position_staffing]()
RETURNS TABLE
AS
RETURN (
    SELECT p.[id] AS [position_id], p.[department_id], d.[name] AS [department_name], COUNT(e.[id]) AS [employee_count] FROM [dbo].[positions] AS p INNER JOIN [dbo].[departments] AS d ON d.[id] = p.[department_id] LEFT JOIN [dbo].[employees] AS e ON e.[position_id] = p.[id] GROUP BY p.[id], p.[department_id], d.[name]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_employee_department_position
CREATE OR ALTER FUNCTION [dbo].[fn_employee_department_position]()
RETURNS TABLE
AS
RETURN (
    SELECT e.[id] AS [employee_id], e.[department_id], d.[name] AS [department_name], e.[position_id], p.[name] AS [position_name] FROM [dbo].[employees] AS e INNER JOIN [dbo].[departments] AS d ON d.[id] = e.[department_id] LEFT JOIN [dbo].[positions] AS p ON p.[id] = e.[position_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_manager_chain
CREATE OR ALTER FUNCTION [dbo].[fn_manager_chain]()
RETURNS TABLE
AS
RETURN (
    SELECT e.[id] AS [employee_id], e.[manager_id], m.[name] AS [manager_name], e.[department_id] FROM [dbo].[employees] AS e LEFT JOIN [dbo].[employees] AS m ON m.[id] = e.[manager_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_employee_attendance_summary
CREATE OR ALTER FUNCTION [dbo].[fn_employee_attendance_summary]()
RETURNS TABLE
AS
RETURN (
    SELECT e.[id] AS [employee_id], e.[department_id], COUNT(a.[id]) AS [attendance_days], SUM(a.[late_minutes]) AS [late_minutes] FROM [dbo].[employees] AS e LEFT JOIN [dbo].[attendance] AS a ON a.[employee_id] = e.[id] GROUP BY e.[id], e.[department_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_role_permission_matrix
CREATE OR ALTER FUNCTION [dbo].[fn_role_permission_matrix]()
RETURNS TABLE
AS
RETURN (
    SELECT r.[id] AS [role_id], p.[id] AS [permission_id], rp.[id] AS [role_permission_id] FROM [dbo].[role_permissions] AS rp INNER JOIN [dbo].[roles] AS r ON r.[id] = rp.[role_id] INNER JOIN [dbo].[permissions] AS p ON p.[id] = rp.[permission_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_product_category_tree
CREATE OR ALTER FUNCTION [dbo].[fn_product_category_tree]()
RETURNS TABLE
AS
RETURN (
    SELECT p.[id] AS [product_id], p.[category_id], c.[parent_id] AS [parent_category_id], c.[name] AS [category_name] FROM [dbo].[products] AS p INNER JOIN [dbo].[product_categories] AS c ON c.[id] = p.[category_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_supplier_product_catalog
CREATE OR ALTER FUNCTION [dbo].[fn_supplier_product_catalog]()
RETURNS TABLE
AS
RETURN (
    SELECT sp.[supplier_id], s.[name] AS [supplier_name], sp.[product_id], p.[sku], sp.[supplier_price] FROM [dbo].[supplier_products] AS sp INNER JOIN [dbo].[suppliers] AS s ON s.[id] = sp.[supplier_id] INNER JOIN [dbo].[products] AS p ON p.[id] = sp.[product_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_batch_inventory_health
CREATE OR ALTER FUNCTION [dbo].[fn_batch_inventory_health]()
RETURNS TABLE
AS
RETURN (
    SELECT i.[product_id], i.[batch_id], pb.[expiry_date], i.[warehouse_id], i.[quantity], i.[available_quantity] FROM [dbo].[inventory] AS i INNER JOIN [dbo].[product_batches] AS pb ON pb.[id] = i.[batch_id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_warehouse_manager_roster
CREATE OR ALTER FUNCTION [dbo].[fn_warehouse_manager_roster]()
RETURNS TABLE
AS
RETURN (
    SELECT w.[id] AS [warehouse_id], w.[manager_id], e.[name] AS [manager_name], w.[status] FROM [dbo].[warehouses] AS w LEFT JOIN [dbo].[employees] AS e ON e.[id] = w.[manager_id]
);
-- relation-detector-fixture-end
