-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[sales_returns] ([handler_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[sales_returns] AS c ON c.[handler_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [handler_id] = p.[id]
    FROM [dbo].[sales_returns] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[handler_id] = p.[id];

    MERGE INTO [dbo].[sales_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[sales_returns] AS c2 ON c2.[handler_id] = p.[id]
    ) AS src
    ON c.[handler_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[handler_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[sales_returns] ([approved_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[sales_returns] AS c ON c.[approved_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [approved_by] = p.[id]
    FROM [dbo].[sales_returns] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id];

    MERGE INTO [dbo].[sales_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[sales_returns] AS c2 ON c2.[approved_by] = p.[id]
    ) AS src
    ON c.[approved_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[approved_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[sales_returns] ([refund_voucher_id])
    SELECT p.[id]
    FROM [dbo].[vouchers] AS p
    INNER JOIN [dbo].[sales_returns] AS c ON c.[refund_voucher_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[vouchers] AS p2);

    UPDATE c
    SET [refund_voucher_id] = p.[id]
    FROM [dbo].[sales_returns] AS c
    INNER JOIN [dbo].[vouchers] AS p ON c.[refund_voucher_id] = p.[id];

    MERGE INTO [dbo].[sales_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[vouchers] AS p
        INNER JOIN [dbo].[sales_returns] AS c2 ON c2.[refund_voucher_id] = p.[id]
    ) AS src
    ON c.[refund_voucher_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[refund_voucher_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[sales_return_items] ([return_id])
    SELECT p.[id]
    FROM [dbo].[sales_returns] AS p
    INNER JOIN [dbo].[sales_return_items] AS c ON c.[return_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_returns] AS p2);

    UPDATE c
    SET [return_id] = p.[id]
    FROM [dbo].[sales_return_items] AS c
    INNER JOIN [dbo].[sales_returns] AS p ON c.[return_id] = p.[id];

    MERGE INTO [dbo].[sales_return_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_returns] AS p
        INNER JOIN [dbo].[sales_return_items] AS c2 ON c2.[return_id] = p.[id]
    ) AS src
    ON c.[return_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[return_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[sales_return_items] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[sales_return_items] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[sales_return_items] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[sales_return_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[sales_return_items] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[sales_return_items] ([batch_id])
    SELECT p.[id]
    FROM [dbo].[product_batches] AS p
    INNER JOIN [dbo].[sales_return_items] AS c ON c.[batch_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_batches] AS p2);

    UPDATE c
    SET [batch_id] = p.[id]
    FROM [dbo].[sales_return_items] AS c
    INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id];

    MERGE INTO [dbo].[sales_return_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_batches] AS p
        INNER JOIN [dbo].[sales_return_items] AS c2 ON c2.[batch_id] = p.[id]
    ) AS src
    ON c.[batch_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[batch_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[purchase_returns] ([purchase_order_id])
    SELECT p.[id]
    FROM [dbo].[purchase_orders] AS p
    INNER JOIN [dbo].[purchase_returns] AS c ON c.[purchase_order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_orders] AS p2);

    UPDATE c
    SET [purchase_order_id] = p.[id]
    FROM [dbo].[purchase_returns] AS c
    INNER JOIN [dbo].[purchase_orders] AS p ON c.[purchase_order_id] = p.[id];

    MERGE INTO [dbo].[purchase_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_orders] AS p
        INNER JOIN [dbo].[purchase_returns] AS c2 ON c2.[purchase_order_id] = p.[id]
    ) AS src
    ON c.[purchase_order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchase_order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[purchase_returns] ([purchase_receipt_id])
    SELECT p.[id]
    FROM [dbo].[purchase_receipts] AS p
    INNER JOIN [dbo].[purchase_returns] AS c ON c.[purchase_receipt_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_receipts] AS p2);

    UPDATE c
    SET [purchase_receipt_id] = p.[id]
    FROM [dbo].[purchase_returns] AS c
    INNER JOIN [dbo].[purchase_receipts] AS p ON c.[purchase_receipt_id] = p.[id];

    MERGE INTO [dbo].[purchase_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_receipts] AS p
        INNER JOIN [dbo].[purchase_returns] AS c2 ON c2.[purchase_receipt_id] = p.[id]
    ) AS src
    ON c.[purchase_receipt_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchase_receipt_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[purchase_returns] ([supplier_id])
    SELECT p.[id]
    FROM [dbo].[suppliers] AS p
    INNER JOIN [dbo].[purchase_returns] AS c ON c.[supplier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[suppliers] AS p2);

    UPDATE c
    SET [supplier_id] = p.[id]
    FROM [dbo].[purchase_returns] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id];

    MERGE INTO [dbo].[purchase_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[suppliers] AS p
        INNER JOIN [dbo].[purchase_returns] AS c2 ON c2.[supplier_id] = p.[id]
    ) AS src
    ON c.[supplier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[supplier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_08_batch_expiry_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_08_batch_expiry_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[purchase_returns] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[purchase_returns] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[purchase_returns] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[purchase_returns] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[purchase_returns] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
