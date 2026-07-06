-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_1
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_1]
AS
BEGIN
    INSERT INTO [dbo].[role_permissions] ([permission_id])
    SELECT p.[id]
    FROM [dbo].[permissions] AS p
    INNER JOIN [dbo].[role_permissions] AS c ON c.[permission_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[permissions] AS p2);

    UPDATE c
    SET [permission_id] = p.[id]
    FROM [dbo].[role_permissions] AS c
    INNER JOIN [dbo].[permissions] AS p ON c.[permission_id] = p.[id];

    MERGE INTO [dbo].[role_permissions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[permissions] AS p
        INNER JOIN [dbo].[role_permissions] AS c2 ON c2.[permission_id] = p.[id]
    ) AS src
    ON c.[permission_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[permission_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_2
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_2]
AS
BEGIN
    INSERT INTO [dbo].[employee_roles] ([employee_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[employee_roles] AS c ON c.[employee_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [employee_id] = p.[id]
    FROM [dbo].[employee_roles] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id];

    MERGE INTO [dbo].[employee_roles] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[employee_roles] AS c2 ON c2.[employee_id] = p.[id]
    ) AS src
    ON c.[employee_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[employee_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_3
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_3]
AS
BEGIN
    INSERT INTO [dbo].[employee_roles] ([role_id])
    SELECT p.[id]
    FROM [dbo].[roles] AS p
    INNER JOIN [dbo].[employee_roles] AS c ON c.[role_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[roles] AS p2);

    UPDATE c
    SET [role_id] = p.[id]
    FROM [dbo].[employee_roles] AS c
    INNER JOIN [dbo].[roles] AS p ON c.[role_id] = p.[id];

    MERGE INTO [dbo].[employee_roles] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[roles] AS p
        INNER JOIN [dbo].[employee_roles] AS c2 ON c2.[role_id] = p.[id]
    ) AS src
    ON c.[role_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[role_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_4
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_4]
AS
BEGIN
    INSERT INTO [dbo].[product_categories] ([parent_id])
    SELECT p.[id]
    FROM [dbo].[product_categories] AS p
    INNER JOIN [dbo].[product_categories] AS c ON c.[parent_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_categories] AS p2);

    UPDATE c
    SET [parent_id] = p.[id]
    FROM [dbo].[product_categories] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[parent_id] = p.[id];

    MERGE INTO [dbo].[product_categories] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_categories] AS p
        INNER JOIN [dbo].[product_categories] AS c2 ON c2.[parent_id] = p.[id]
    ) AS src
    ON c.[parent_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[parent_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_5
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_5]
AS
BEGIN
    INSERT INTO [dbo].[products] ([category_id])
    SELECT p.[id]
    FROM [dbo].[product_categories] AS p
    INNER JOIN [dbo].[products] AS c ON c.[category_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_categories] AS p2);

    UPDATE c
    SET [category_id] = p.[id]
    FROM [dbo].[products] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[category_id] = p.[id];

    MERGE INTO [dbo].[products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_categories] AS p
        INNER JOIN [dbo].[products] AS c2 ON c2.[category_id] = p.[id]
    ) AS src
    ON c.[category_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[category_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_6
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_6]
AS
BEGIN
    INSERT INTO [dbo].[supplier_products] ([supplier_id])
    SELECT p.[id]
    FROM [dbo].[suppliers] AS p
    INNER JOIN [dbo].[supplier_products] AS c ON c.[supplier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[suppliers] AS p2);

    UPDATE c
    SET [supplier_id] = p.[id]
    FROM [dbo].[supplier_products] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id];

    MERGE INTO [dbo].[supplier_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[suppliers] AS p
        INNER JOIN [dbo].[supplier_products] AS c2 ON c2.[supplier_id] = p.[id]
    ) AS src
    ON c.[supplier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[supplier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_7
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_7]
AS
BEGIN
    INSERT INTO [dbo].[supplier_products] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[supplier_products] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[supplier_products] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[supplier_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[supplier_products] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_8
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_8]
AS
BEGIN
    INSERT INTO [dbo].[product_batches] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[product_batches] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[product_batches] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[product_batches] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[product_batches] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_9
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_9]
AS
BEGIN
    INSERT INTO [dbo].[product_batches] ([supplier_id])
    SELECT p.[id]
    FROM [dbo].[suppliers] AS p
    INNER JOIN [dbo].[product_batches] AS c ON c.[supplier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[suppliers] AS p2);

    UPDATE c
    SET [supplier_id] = p.[id]
    FROM [dbo].[product_batches] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id];

    MERGE INTO [dbo].[product_batches] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[suppliers] AS p
        INNER JOIN [dbo].[product_batches] AS c2 ON c2.[supplier_id] = p.[id]
    ) AS src
    ON c.[supplier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[supplier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_02_procedures_supplement_10
CREATE OR ALTER PROCEDURE [dbo].[sp_02_procedures_supplement_10]
AS
BEGIN
    INSERT INTO [dbo].[warehouses] ([manager_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[warehouses] AS c ON c.[manager_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [manager_id] = p.[id]
    FROM [dbo].[warehouses] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[manager_id] = p.[id];

    MERGE INTO [dbo].[warehouses] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[warehouses] AS c2 ON c2.[manager_id] = p.[id]
    ) AS src
    ON c.[manager_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[manager_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
