-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[purchase_receipt_items] ([batch_id])
    SELECT p.[id]
    FROM [dbo].[product_batches] AS p
    INNER JOIN [dbo].[purchase_receipt_items] AS c ON c.[batch_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_batches] AS p2);

    UPDATE c
    SET [batch_id] = p.[id]
    FROM [dbo].[purchase_receipt_items] AS c
    INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id];

    MERGE INTO [dbo].[purchase_receipt_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_batches] AS p
        INNER JOIN [dbo].[purchase_receipt_items] AS c2 ON c2.[batch_id] = p.[id]
    ) AS src
    ON c.[batch_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[batch_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[sales_orders] ([customer_id])
    SELECT p.[id]
    FROM [dbo].[customers] AS p
    INNER JOIN [dbo].[sales_orders] AS c ON c.[customer_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[customers] AS p2);

    UPDATE c
    SET [customer_id] = p.[id]
    FROM [dbo].[sales_orders] AS c
    INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id];

    MERGE INTO [dbo].[sales_orders] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[customers] AS p
        INNER JOIN [dbo].[sales_orders] AS c2 ON c2.[customer_id] = p.[id]
    ) AS src
    ON c.[customer_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[customer_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[sales_orders] ([salesperson_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[sales_orders] AS c ON c.[salesperson_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [salesperson_id] = p.[id]
    FROM [dbo].[sales_orders] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[salesperson_id] = p.[id];

    MERGE INTO [dbo].[sales_orders] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[sales_orders] AS c2 ON c2.[salesperson_id] = p.[id]
    ) AS src
    ON c.[salesperson_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[salesperson_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[sales_orders] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[sales_orders] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[sales_orders] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[sales_orders] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[sales_orders] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[sales_order_items] ([order_id])
    SELECT p.[id]
    FROM [dbo].[sales_orders] AS p
    INNER JOIN [dbo].[sales_order_items] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[sales_order_items] AS c
    INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[sales_order_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_orders] AS p
        INNER JOIN [dbo].[sales_order_items] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[sales_order_items] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[sales_order_items] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[sales_order_items] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[sales_order_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[sales_order_items] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[sales_order_items] ([batch_id])
    SELECT p.[id]
    FROM [dbo].[product_batches] AS p
    INNER JOIN [dbo].[sales_order_items] AS c ON c.[batch_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_batches] AS p2);

    UPDATE c
    SET [batch_id] = p.[id]
    FROM [dbo].[sales_order_items] AS c
    INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id];

    MERGE INTO [dbo].[sales_order_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_batches] AS p
        INNER JOIN [dbo].[sales_order_items] AS c2 ON c2.[batch_id] = p.[id]
    ) AS src
    ON c.[batch_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[batch_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[sales_returns] ([order_id])
    SELECT p.[id]
    FROM [dbo].[sales_orders] AS p
    INNER JOIN [dbo].[sales_returns] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[sales_returns] AS c
    INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[sales_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_orders] AS p
        INNER JOIN [dbo].[sales_returns] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[sales_returns] ([customer_id])
    SELECT p.[id]
    FROM [dbo].[customers] AS p
    INNER JOIN [dbo].[sales_returns] AS c ON c.[customer_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[customers] AS p2);

    UPDATE c
    SET [customer_id] = p.[id]
    FROM [dbo].[sales_returns] AS c
    INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id];

    MERGE INTO [dbo].[sales_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[customers] AS p
        INNER JOIN [dbo].[sales_returns] AS c2 ON c2.[customer_id] = p.[id]
    ) AS src
    ON c.[customer_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[customer_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_07_store_customer_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_07_store_customer_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[sales_returns] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[sales_returns] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[sales_returns] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[sales_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[sales_returns] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
