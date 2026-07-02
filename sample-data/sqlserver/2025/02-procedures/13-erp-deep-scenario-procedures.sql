-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[promotion_products] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[promotion_products] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[promotion_products] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[promotion_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[promotion_products] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[promotion_products] ([category_id])
    SELECT p.[id]
    FROM [dbo].[product_categories] AS p
    INNER JOIN [dbo].[promotion_products] AS c ON c.[category_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_categories] AS p2);

    UPDATE c
    SET [category_id] = p.[id]
    FROM [dbo].[promotion_products] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[category_id] = p.[id];

    MERGE INTO [dbo].[promotion_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_categories] AS p
        INNER JOIN [dbo].[promotion_products] AS c2 ON c2.[category_id] = p.[id]
    ) AS src
    ON c.[category_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[category_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[promotion_usages] ([promotion_id])
    SELECT p.[id]
    FROM [dbo].[promotions] AS p
    INNER JOIN [dbo].[promotion_usages] AS c ON c.[promotion_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[promotions] AS p2);

    UPDATE c
    SET [promotion_id] = p.[id]
    FROM [dbo].[promotion_usages] AS c
    INNER JOIN [dbo].[promotions] AS p ON c.[promotion_id] = p.[id];

    MERGE INTO [dbo].[promotion_usages] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[promotions] AS p
        INNER JOIN [dbo].[promotion_usages] AS c2 ON c2.[promotion_id] = p.[id]
    ) AS src
    ON c.[promotion_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[promotion_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[promotion_usages] ([order_id])
    SELECT p.[id]
    FROM [dbo].[sales_orders] AS p
    INNER JOIN [dbo].[promotion_usages] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[promotion_usages] AS c
    INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[promotion_usages] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_orders] AS p
        INNER JOIN [dbo].[promotion_usages] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[promotion_usages] ([customer_id])
    SELECT p.[id]
    FROM [dbo].[customers] AS p
    INNER JOIN [dbo].[promotion_usages] AS c ON c.[customer_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[customers] AS p2);

    UPDATE c
    SET [customer_id] = p.[id]
    FROM [dbo].[promotion_usages] AS c
    INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id];

    MERGE INTO [dbo].[promotion_usages] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[customers] AS p
        INNER JOIN [dbo].[promotion_usages] AS c2 ON c2.[customer_id] = p.[id]
    ) AS src
    ON c.[customer_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[customer_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[invoices] ([supplier_id])
    SELECT p.[id]
    FROM [dbo].[suppliers] AS p
    INNER JOIN [dbo].[invoices] AS c ON c.[supplier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[suppliers] AS p2);

    UPDATE c
    SET [supplier_id] = p.[id]
    FROM [dbo].[invoices] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id];

    MERGE INTO [dbo].[invoices] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[suppliers] AS p
        INNER JOIN [dbo].[invoices] AS c2 ON c2.[supplier_id] = p.[id]
    ) AS src
    ON c.[supplier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[supplier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[invoices] ([customer_id])
    SELECT p.[id]
    FROM [dbo].[customers] AS p
    INNER JOIN [dbo].[invoices] AS c ON c.[customer_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[customers] AS p2);

    UPDATE c
    SET [customer_id] = p.[id]
    FROM [dbo].[invoices] AS c
    INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id];

    MERGE INTO [dbo].[invoices] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[customers] AS p
        INNER JOIN [dbo].[invoices] AS c2 ON c2.[customer_id] = p.[id]
    ) AS src
    ON c.[customer_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[customer_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[three_way_matching] ([invoice_id])
    SELECT p.[id]
    FROM [dbo].[invoices] AS p
    INNER JOIN [dbo].[three_way_matching] AS c ON c.[invoice_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[invoices] AS p2);

    UPDATE c
    SET [invoice_id] = p.[id]
    FROM [dbo].[three_way_matching] AS c
    INNER JOIN [dbo].[invoices] AS p ON c.[invoice_id] = p.[id];

    MERGE INTO [dbo].[three_way_matching] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[invoices] AS p
        INNER JOIN [dbo].[three_way_matching] AS c2 ON c2.[invoice_id] = p.[id]
    ) AS src
    ON c.[invoice_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[invoice_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[three_way_matching] ([purchase_order_id])
    SELECT p.[id]
    FROM [dbo].[purchase_orders] AS p
    INNER JOIN [dbo].[three_way_matching] AS c ON c.[purchase_order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_orders] AS p2);

    UPDATE c
    SET [purchase_order_id] = p.[id]
    FROM [dbo].[three_way_matching] AS c
    INNER JOIN [dbo].[purchase_orders] AS p ON c.[purchase_order_id] = p.[id];

    MERGE INTO [dbo].[three_way_matching] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_orders] AS p
        INNER JOIN [dbo].[three_way_matching] AS c2 ON c2.[purchase_order_id] = p.[id]
    ) AS src
    ON c.[purchase_order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchase_order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[three_way_matching] ([purchase_receipt_id])
    SELECT p.[id]
    FROM [dbo].[purchase_receipts] AS p
    INNER JOIN [dbo].[three_way_matching] AS c ON c.[purchase_receipt_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_receipts] AS p2);

    UPDATE c
    SET [purchase_receipt_id] = p.[id]
    FROM [dbo].[three_way_matching] AS c
    INNER JOIN [dbo].[purchase_receipts] AS p ON c.[purchase_receipt_id] = p.[id];

    MERGE INTO [dbo].[three_way_matching] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_receipts] AS p
        INNER JOIN [dbo].[three_way_matching] AS c2 ON c2.[purchase_receipt_id] = p.[id]
    ) AS src
    ON c.[purchase_receipt_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchase_receipt_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
