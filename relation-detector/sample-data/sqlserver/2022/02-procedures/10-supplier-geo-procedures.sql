-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[damage_reports] ([voucher_id])
    SELECT p.[id]
    FROM [dbo].[vouchers] AS p
    INNER JOIN [dbo].[damage_reports] AS c ON c.[voucher_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[vouchers] AS p2);

    UPDATE c
    SET [voucher_id] = p.[id]
    FROM [dbo].[damage_reports] AS c
    INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id];

    MERGE INTO [dbo].[damage_reports] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[vouchers] AS p
        INNER JOIN [dbo].[damage_reports] AS c2 ON c2.[voucher_id] = p.[id]
    ) AS src
    ON c.[voucher_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[voucher_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[damage_report_items] ([report_id])
    SELECT p.[id]
    FROM [dbo].[damage_reports] AS p
    INNER JOIN [dbo].[damage_report_items] AS c ON c.[report_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[damage_reports] AS p2);

    UPDATE c
    SET [report_id] = p.[id]
    FROM [dbo].[damage_report_items] AS c
    INNER JOIN [dbo].[damage_reports] AS p ON c.[report_id] = p.[id];

    MERGE INTO [dbo].[damage_report_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[damage_reports] AS p
        INNER JOIN [dbo].[damage_report_items] AS c2 ON c2.[report_id] = p.[id]
    ) AS src
    ON c.[report_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[report_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[damage_report_items] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[damage_report_items] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[damage_report_items] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[damage_report_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[damage_report_items] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[damage_report_items] ([batch_id])
    SELECT p.[id]
    FROM [dbo].[product_batches] AS p
    INNER JOIN [dbo].[damage_report_items] AS c ON c.[batch_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_batches] AS p2);

    UPDATE c
    SET [batch_id] = p.[id]
    FROM [dbo].[damage_report_items] AS c
    INNER JOIN [dbo].[product_batches] AS p ON c.[batch_id] = p.[id];

    MERGE INTO [dbo].[damage_report_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_batches] AS p
        INNER JOIN [dbo].[damage_report_items] AS c2 ON c2.[batch_id] = p.[id]
    ) AS src
    ON c.[batch_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[batch_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[accounts] ([parent_id])
    SELECT p.[id]
    FROM [dbo].[accounts] AS p
    INNER JOIN [dbo].[accounts] AS c ON c.[parent_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[accounts] AS p2);

    UPDATE c
    SET [parent_id] = p.[id]
    FROM [dbo].[accounts] AS c
    INNER JOIN [dbo].[accounts] AS p ON c.[parent_id] = p.[id];

    MERGE INTO [dbo].[accounts] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[accounts] AS p
        INNER JOIN [dbo].[accounts] AS c2 ON c2.[parent_id] = p.[id]
    ) AS src
    ON c.[parent_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[parent_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[vouchers] ([prepared_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[vouchers] AS c ON c.[prepared_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [prepared_by] = p.[id]
    FROM [dbo].[vouchers] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id];

    MERGE INTO [dbo].[vouchers] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[vouchers] AS c2 ON c2.[prepared_by] = p.[id]
    ) AS src
    ON c.[prepared_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[prepared_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[vouchers] ([reviewed_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[vouchers] AS c ON c.[reviewed_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [reviewed_by] = p.[id]
    FROM [dbo].[vouchers] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[reviewed_by] = p.[id];

    MERGE INTO [dbo].[vouchers] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[vouchers] AS c2 ON c2.[reviewed_by] = p.[id]
    ) AS src
    ON c.[reviewed_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[reviewed_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[vouchers] ([posted_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[vouchers] AS c ON c.[posted_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [posted_by] = p.[id]
    FROM [dbo].[vouchers] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[posted_by] = p.[id];

    MERGE INTO [dbo].[vouchers] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[vouchers] AS c2 ON c2.[posted_by] = p.[id]
    ) AS src
    ON c.[posted_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[posted_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[voucher_items] ([voucher_id])
    SELECT p.[id]
    FROM [dbo].[vouchers] AS p
    INNER JOIN [dbo].[voucher_items] AS c ON c.[voucher_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[vouchers] AS p2);

    UPDATE c
    SET [voucher_id] = p.[id]
    FROM [dbo].[voucher_items] AS c
    INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id];

    MERGE INTO [dbo].[voucher_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[vouchers] AS p
        INNER JOIN [dbo].[voucher_items] AS c2 ON c2.[voucher_id] = p.[id]
    ) AS src
    ON c.[voucher_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[voucher_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_10_supplier_geo_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_10_supplier_geo_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[voucher_items] ([account_id])
    SELECT p.[id]
    FROM [dbo].[accounts] AS p
    INNER JOIN [dbo].[voucher_items] AS c ON c.[account_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[accounts] AS p2);

    UPDATE c
    SET [account_id] = p.[id]
    FROM [dbo].[voucher_items] AS c
    INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id];

    MERGE INTO [dbo].[voucher_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[accounts] AS p
        INNER JOIN [dbo].[voucher_items] AS c2 ON c2.[account_id] = p.[id]
    ) AS src
    ON c.[account_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[account_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
