-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_1
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_1]
AS
BEGIN
    INSERT INTO [dbo].[inventory] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[inventory] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[inventory] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[inventory] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[inventory] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_2
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_2]
AS
BEGIN
    INSERT INTO [dbo].[inventory] ([batch_id])
    SELECT p.[id]
    FROM [dbo].[product_batches] AS p
    INNER JOIN [dbo].[inventory] AS c ON c.[batch_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_batches] AS p2);

    UPDATE c
    SET [batch_id] = p.[id]
    FROM [dbo].[inventory] AS c
    INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id];

    MERGE INTO [dbo].[inventory] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_batches] AS p
        INNER JOIN [dbo].[inventory] AS c2 ON c2.[batch_id] = p.[id]
    ) AS src
    ON c.[batch_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[batch_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_3
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_3]
AS
BEGIN
    INSERT INTO [dbo].[inventory] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[inventory] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[inventory] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[inventory] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[inventory] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_4
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_4]
AS
BEGIN
    INSERT INTO [dbo].[inventory_transactions] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[inventory_transactions] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[inventory_transactions] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[inventory_transactions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[inventory_transactions] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_5
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_5]
AS
BEGIN
    INSERT INTO [dbo].[inventory_transactions] ([batch_id])
    SELECT p.[id]
    FROM [dbo].[product_batches] AS p
    INNER JOIN [dbo].[inventory_transactions] AS c ON c.[batch_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_batches] AS p2);

    UPDATE c
    SET [batch_id] = p.[id]
    FROM [dbo].[inventory_transactions] AS c
    INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id];

    MERGE INTO [dbo].[inventory_transactions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_batches] AS p
        INNER JOIN [dbo].[inventory_transactions] AS c2 ON c2.[batch_id] = p.[id]
    ) AS src
    ON c.[batch_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[batch_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_6
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_6]
AS
BEGIN
    INSERT INTO [dbo].[inventory_transactions] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[inventory_transactions] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[inventory_transactions] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[inventory_transactions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[inventory_transactions] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_7
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_7]
AS
BEGIN
    INSERT INTO [dbo].[purchase_requisitions] ([department_id])
    SELECT p.[id]
    FROM [dbo].[departments] AS p
    INNER JOIN [dbo].[purchase_requisitions] AS c ON c.[department_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[departments] AS p2);

    UPDATE c
    SET [department_id] = p.[id]
    FROM [dbo].[purchase_requisitions] AS c
    INNER JOIN [dbo].[departments] AS p ON c.[department_id] = p.[id];

    MERGE INTO [dbo].[purchase_requisitions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[departments] AS p
        INNER JOIN [dbo].[purchase_requisitions] AS c2 ON c2.[department_id] = p.[id]
    ) AS src
    ON c.[department_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[department_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_8
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_8]
AS
BEGIN
    INSERT INTO [dbo].[purchase_requisitions] ([requester_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[purchase_requisitions] AS c ON c.[requester_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [requester_id] = p.[id]
    FROM [dbo].[purchase_requisitions] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[requester_id] = p.[id];

    MERGE INTO [dbo].[purchase_requisitions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[purchase_requisitions] AS c2 ON c2.[requester_id] = p.[id]
    ) AS src
    ON c.[requester_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[requester_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_9
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_9]
AS
BEGIN
    INSERT INTO [dbo].[purchase_requisition_items] ([requisition_id])
    SELECT p.[id]
    FROM [dbo].[purchase_requisitions] AS p
    INNER JOIN [dbo].[purchase_requisition_items] AS c ON c.[requisition_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_requisitions] AS p2);

    UPDATE c
    SET [requisition_id] = p.[id]
    FROM [dbo].[purchase_requisition_items] AS c
    INNER JOIN [dbo].[purchase_requisitions] AS p ON c.[requisition_id] = p.[id];

    MERGE INTO [dbo].[purchase_requisition_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_requisitions] AS p
        INNER JOIN [dbo].[purchase_requisition_items] AS c2 ON c2.[requisition_id] = p.[id]
    ) AS src
    ON c.[requisition_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[requisition_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_04_procedures_supplement_10
CREATE OR ALTER PROCEDURE [dbo].[sp_04_procedures_supplement_10]
AS
BEGIN
    INSERT INTO [dbo].[purchase_requisition_items] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[purchase_requisition_items] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[purchase_requisition_items] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[purchase_requisition_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[purchase_requisition_items] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
