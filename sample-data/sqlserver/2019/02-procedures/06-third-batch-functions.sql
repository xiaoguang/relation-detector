-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_1
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_1]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[permission_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[role_permissions] AS c
    INNER JOIN [dbo].[permissions] AS p ON c.[permission_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_2
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_2]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[employee_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[employee_roles] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_3
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_3]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[role_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[employee_roles] AS c
    INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_4
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_4]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[parent_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[product_categories] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[parent_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_5
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_5]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[category_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[products] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[category_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_6
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_6]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[supplier_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[supplier_products] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_7
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_7]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[product_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[supplier_products] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_8
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_8]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[product_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[product_batches] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_9
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_9]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[supplier_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[product_batches] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_extra_10
CREATE OR ALTER FUNCTION [dbo].[fn_relation_extra_10]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[manager_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[warehouses] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id]
);
-- relation-detector-fixture-end
