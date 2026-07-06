-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.fn_relation_1
CREATE OR ALTER FUNCTION [dbo].[fn_relation_1]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[parent_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[departments] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[parent_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_2
CREATE OR ALTER FUNCTION [dbo].[fn_relation_2]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[department_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[positions] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_3
CREATE OR ALTER FUNCTION [dbo].[fn_relation_3]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[department_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[employees] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_4
CREATE OR ALTER FUNCTION [dbo].[fn_relation_4]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[position_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[employees] AS c
    INNER JOIN [dbo].[positions] AS p ON c.[position_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_5
CREATE OR ALTER FUNCTION [dbo].[fn_relation_5]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[manager_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[employees] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_6
CREATE OR ALTER FUNCTION [dbo].[fn_relation_6]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[employee_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[employee_salary_log] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_7
CREATE OR ALTER FUNCTION [dbo].[fn_relation_7]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[employee_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[attendance] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_8
CREATE OR ALTER FUNCTION [dbo].[fn_relation_8]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[employee_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[leave_records] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_9
CREATE OR ALTER FUNCTION [dbo].[fn_relation_9]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[parent_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[permissions] AS c
    INNER JOIN [dbo].[permissions] AS p ON c.[parent_id] = p.[id]
);
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.fn_relation_10
CREATE OR ALTER FUNCTION [dbo].[fn_relation_10]()
RETURNS TABLE
AS
RETURN (
    SELECT c.[role_id] AS [child_id], p.[id] AS [parent_id]
    FROM [dbo].[role_permissions] AS c
    INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id]
);
-- relation-detector-fixture-end
