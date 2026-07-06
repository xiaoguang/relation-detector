-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[departments] ([parent_id])
    SELECT p.[id]
    FROM [dbo].[departments] AS p
    INNER JOIN [dbo].[departments] AS c ON c.[parent_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[departments] AS p2);

    UPDATE c
    SET [parent_id] = p.[id]
    FROM [dbo].[departments] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[parent_id] = p.[id];

    MERGE INTO [dbo].[departments] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[departments] AS p
        INNER JOIN [dbo].[departments] AS c2 ON c2.[parent_id] = p.[id]
    ) AS src
    ON c.[parent_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[parent_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[positions] ([department_id])
    SELECT p.[id]
    FROM [dbo].[departments] AS p
    INNER JOIN [dbo].[positions] AS c ON c.[department_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[departments] AS p2);

    UPDATE c
    SET [department_id] = p.[id]
    FROM [dbo].[positions] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id];

    MERGE INTO [dbo].[positions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[departments] AS p
        INNER JOIN [dbo].[positions] AS c2 ON c2.[department_id] = p.[id]
    ) AS src
    ON c.[department_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[department_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[employees] ([department_id])
    SELECT p.[id]
    FROM [dbo].[departments] AS p
    INNER JOIN [dbo].[employees] AS c ON c.[department_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[departments] AS p2);

    UPDATE c
    SET [department_id] = p.[id]
    FROM [dbo].[employees] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id];

    MERGE INTO [dbo].[employees] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[departments] AS p
        INNER JOIN [dbo].[employees] AS c2 ON c2.[department_id] = p.[id]
    ) AS src
    ON c.[department_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[department_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[employees] ([position_id])
    SELECT p.[id]
    FROM [dbo].[positions] AS p
    INNER JOIN [dbo].[employees] AS c ON c.[position_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[positions] AS p2);

    UPDATE c
    SET [position_id] = p.[id]
    FROM [dbo].[employees] AS c
    INNER JOIN [dbo].[positions] AS p ON c.[position_id] = p.[id];

    MERGE INTO [dbo].[employees] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[positions] AS p
        INNER JOIN [dbo].[employees] AS c2 ON c2.[position_id] = p.[id]
    ) AS src
    ON c.[position_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[position_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[employees] ([manager_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[employees] AS c ON c.[manager_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [manager_id] = p.[id]
    FROM [dbo].[employees] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id];

    MERGE INTO [dbo].[employees] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[employees] AS c2 ON c2.[manager_id] = p.[id]
    ) AS src
    ON c.[manager_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[manager_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[employee_salary_log] ([employee_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[employee_salary_log] AS c ON c.[employee_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [employee_id] = p.[id]
    FROM [dbo].[employee_salary_log] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id];

    MERGE INTO [dbo].[employee_salary_log] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[employee_salary_log] AS c2 ON c2.[employee_id] = p.[id]
    ) AS src
    ON c.[employee_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[employee_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[attendance] ([employee_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[attendance] AS c ON c.[employee_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [employee_id] = p.[id]
    FROM [dbo].[attendance] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id];

    MERGE INTO [dbo].[attendance] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[attendance] AS c2 ON c2.[employee_id] = p.[id]
    ) AS src
    ON c.[employee_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[employee_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[leave_records] ([employee_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[leave_records] AS c ON c.[employee_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [employee_id] = p.[id]
    FROM [dbo].[leave_records] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id];

    MERGE INTO [dbo].[leave_records] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[leave_records] AS c2 ON c2.[employee_id] = p.[id]
    ) AS src
    ON c.[employee_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[employee_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[permissions] ([parent_id])
    SELECT p.[id]
    FROM [dbo].[permissions] AS p
    INNER JOIN [dbo].[permissions] AS c ON c.[parent_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[permissions] AS p2);

    UPDATE c
    SET [parent_id] = p.[id]
    FROM [dbo].[permissions] AS c
    INNER JOIN [dbo].[permissions] AS p ON c.[parent_id] = p.[id];

    MERGE INTO [dbo].[permissions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[permissions] AS p
        INNER JOIN [dbo].[permissions] AS c2 ON c2.[parent_id] = p.[id]
    ) AS src
    ON c.[parent_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[parent_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_01_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_01_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[role_permissions] ([role_id])
    SELECT p.[id]
    FROM [dbo].[roles] AS p
    INNER JOIN [dbo].[role_permissions] AS c ON c.[role_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[roles] AS p2);

    UPDATE c
    SET [role_id] = p.[id]
    FROM [dbo].[role_permissions] AS c
    INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id];

    MERGE INTO [dbo].[role_permissions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[roles] AS p
        INNER JOIN [dbo].[role_permissions] AS c2 ON c2.[role_id] = p.[id]
    ) AS src
    ON c.[role_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[role_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
