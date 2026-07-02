-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[purchase_orders] ([supplier_id])
    SELECT p.[id]
    FROM [dbo].[suppliers] AS p
    INNER JOIN [dbo].[purchase_orders] AS c ON c.[supplier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[suppliers] AS p2);

    UPDATE c
    SET [supplier_id] = p.[id]
    FROM [dbo].[purchase_orders] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id];

    MERGE INTO [dbo].[purchase_orders] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[suppliers] AS p
        INNER JOIN [dbo].[purchase_orders] AS c2 ON c2.[supplier_id] = p.[id]
    ) AS src
    ON c.[supplier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[supplier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[purchase_orders] ([requisition_id])
    SELECT p.[id]
    FROM [dbo].[purchase_requisitions] AS p
    INNER JOIN [dbo].[purchase_orders] AS c ON c.[requisition_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_requisitions] AS p2);

    UPDATE c
    SET [requisition_id] = p.[id]
    FROM [dbo].[purchase_orders] AS c
    INNER JOIN [dbo].[purchase_requisitions] AS p ON c.[requisition_id] = p.[id];

    MERGE INTO [dbo].[purchase_orders] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_requisitions] AS p
        INNER JOIN [dbo].[purchase_orders] AS c2 ON c2.[requisition_id] = p.[id]
    ) AS src
    ON c.[requisition_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[requisition_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[purchase_orders] ([purchaser_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[purchase_orders] AS c ON c.[purchaser_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [purchaser_id] = p.[id]
    FROM [dbo].[purchase_orders] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[purchaser_id] = p.[id];

    MERGE INTO [dbo].[purchase_orders] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[purchase_orders] AS c2 ON c2.[purchaser_id] = p.[id]
    ) AS src
    ON c.[purchaser_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchaser_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[purchase_order_items] ([order_id])
    SELECT p.[id]
    FROM [dbo].[purchase_orders] AS p
    INNER JOIN [dbo].[purchase_order_items] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[purchase_order_items] AS c
    INNER JOIN [dbo].[purchase_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[purchase_order_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_orders] AS p
        INNER JOIN [dbo].[purchase_order_items] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[purchase_order_items] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[purchase_order_items] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[purchase_order_items] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[purchase_order_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[purchase_order_items] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[purchase_receipts] ([order_id])
    SELECT p.[id]
    FROM [dbo].[purchase_orders] AS p
    INNER JOIN [dbo].[purchase_receipts] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[purchase_receipts] AS c
    INNER JOIN [dbo].[purchase_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[purchase_receipts] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_orders] AS p
        INNER JOIN [dbo].[purchase_receipts] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[purchase_receipts] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[purchase_receipts] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[purchase_receipts] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[purchase_receipts] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[purchase_receipts] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[purchase_receipts] ([receiver_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[purchase_receipts] AS c ON c.[receiver_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [receiver_id] = p.[id]
    FROM [dbo].[purchase_receipts] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[receiver_id] = p.[id];

    MERGE INTO [dbo].[purchase_receipts] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[purchase_receipts] AS c2 ON c2.[receiver_id] = p.[id]
    ) AS src
    ON c.[receiver_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[receiver_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[purchase_receipt_items] ([receipt_id])
    SELECT p.[id]
    FROM [dbo].[purchase_receipts] AS p
    INNER JOIN [dbo].[purchase_receipt_items] AS c ON c.[receipt_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_receipts] AS p2);

    UPDATE c
    SET [receipt_id] = p.[id]
    FROM [dbo].[purchase_receipt_items] AS c
    INNER JOIN [dbo].[purchase_receipts] AS p ON c.[receipt_id] = p.[id];

    MERGE INTO [dbo].[purchase_receipt_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_receipts] AS p
        INNER JOIN [dbo].[purchase_receipt_items] AS c2 ON c2.[receipt_id] = p.[id]
    ) AS src
    ON c.[receipt_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[receipt_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_05_third_batch_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_05_third_batch_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[purchase_receipt_items] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[purchase_receipt_items] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[purchase_receipt_items] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[purchase_receipt_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[purchase_receipt_items] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
