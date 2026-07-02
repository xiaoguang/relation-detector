-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[settlements] ([prepared_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[settlements] AS c ON c.[prepared_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [prepared_by] = p.[id]
    FROM [dbo].[settlements] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id];

    MERGE INTO [dbo].[settlements] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[settlements] AS c2 ON c2.[prepared_by] = p.[id]
    ) AS src
    ON c.[prepared_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[prepared_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[settlements] ([approved_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[settlements] AS c ON c.[approved_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [approved_by] = p.[id]
    FROM [dbo].[settlements] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[approved_by] = p.[id];

    MERGE INTO [dbo].[settlements] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[settlements] AS c2 ON c2.[approved_by] = p.[id]
    ) AS src
    ON c.[approved_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[approved_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[settlement_items] ([settlement_id])
    SELECT p.[id]
    FROM [dbo].[settlements] AS p
    INNER JOIN [dbo].[settlement_items] AS c ON c.[settlement_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[settlements] AS p2);

    UPDATE c
    SET [settlement_id] = p.[id]
    FROM [dbo].[settlement_items] AS c
    INNER JOIN [dbo].[settlements] AS p ON c.[settlement_id] = p.[id];

    MERGE INTO [dbo].[settlement_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[settlements] AS p
        INNER JOIN [dbo].[settlement_items] AS c2 ON c2.[settlement_id] = p.[id]
    ) AS src
    ON c.[settlement_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[settlement_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[shipments] ([order_id])
    SELECT p.[id]
    FROM [dbo].[sales_orders] AS p
    INNER JOIN [dbo].[shipments] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[shipments] AS c
    INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[shipments] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_orders] AS p
        INNER JOIN [dbo].[shipments] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[shipments] ([warehouse_id])
    SELECT p.[id]
    FROM [dbo].[warehouses] AS p
    INNER JOIN [dbo].[shipments] AS c ON c.[warehouse_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[warehouses] AS p2);

    UPDATE c
    SET [warehouse_id] = p.[id]
    FROM [dbo].[shipments] AS c
    INNER JOIN [dbo].[warehouses] AS p ON c.[warehouse_id] = p.[id];

    MERGE INTO [dbo].[shipments] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[warehouses] AS p
        INNER JOIN [dbo].[shipments] AS c2 ON c2.[warehouse_id] = p.[id]
    ) AS src
    ON c.[warehouse_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[warehouse_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[shipping_tracks] ([shipment_id])
    SELECT p.[id]
    FROM [dbo].[shipments] AS p
    INNER JOIN [dbo].[shipping_tracks] AS c ON c.[shipment_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[shipments] AS p2);

    UPDATE c
    SET [shipment_id] = p.[id]
    FROM [dbo].[shipping_tracks] AS c
    INNER JOIN [dbo].[shipments] AS p ON c.[shipment_id] = p.[id];

    MERGE INTO [dbo].[shipping_tracks] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[shipments] AS p
        INNER JOIN [dbo].[shipping_tracks] AS c2 ON c2.[shipment_id] = p.[id]
    ) AS src
    ON c.[shipment_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[shipment_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[commission_rules] ([product_category_id])
    SELECT p.[id]
    FROM [dbo].[product_categories] AS p
    INNER JOIN [dbo].[commission_rules] AS c ON c.[product_category_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_categories] AS p2);

    UPDATE c
    SET [product_category_id] = p.[id]
    FROM [dbo].[commission_rules] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[product_category_id] = p.[id];

    MERGE INTO [dbo].[commission_rules] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_categories] AS p
        INNER JOIN [dbo].[commission_rules] AS c2 ON c2.[product_category_id] = p.[id]
    ) AS src
    ON c.[product_category_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_category_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[sales_commissions] ([employee_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[sales_commissions] AS c ON c.[employee_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [employee_id] = p.[id]
    FROM [dbo].[sales_commissions] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id];

    MERGE INTO [dbo].[sales_commissions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[sales_commissions] AS c2 ON c2.[employee_id] = p.[id]
    ) AS src
    ON c.[employee_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[employee_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[sales_commissions] ([order_id])
    SELECT p.[id]
    FROM [dbo].[sales_orders] AS p
    INNER JOIN [dbo].[sales_commissions] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[sales_commissions] AS c
    INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[sales_commissions] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_orders] AS p
        INNER JOIN [dbo].[sales_commissions] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_12_enterprise_extension_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_12_enterprise_extension_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[promotion_products] ([promotion_id])
    SELECT p.[id]
    FROM [dbo].[promotions] AS p
    INNER JOIN [dbo].[promotion_products] AS c ON c.[promotion_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[promotions] AS p2);

    UPDATE c
    SET [promotion_id] = p.[id]
    FROM [dbo].[promotion_products] AS c
    INNER JOIN [dbo].[promotions] AS p ON c.[promotion_id] = p.[id];

    MERGE INTO [dbo].[promotion_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[promotions] AS p
        INNER JOIN [dbo].[promotion_products] AS c2 ON c2.[promotion_id] = p.[id]
    ) AS src
    ON c.[promotion_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[promotion_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
