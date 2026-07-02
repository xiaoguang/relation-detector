-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[cashier_journals] ([account_id])
    SELECT p.[id]
    FROM [dbo].[accounts] AS p
    INNER JOIN [dbo].[cashier_journals] AS c ON c.[account_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[accounts] AS p2);

    UPDATE c
    SET [account_id] = p.[id]
    FROM [dbo].[cashier_journals] AS c
    INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id];

    MERGE INTO [dbo].[cashier_journals] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[accounts] AS p
        INNER JOIN [dbo].[cashier_journals] AS c2 ON c2.[account_id] = p.[id]
    ) AS src
    ON c.[account_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[account_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[cashier_journals] ([cashier_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[cashier_journals] AS c ON c.[cashier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [cashier_id] = p.[id]
    FROM [dbo].[cashier_journals] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[cashier_id] = p.[id];

    MERGE INTO [dbo].[cashier_journals] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[cashier_journals] AS c2 ON c2.[cashier_id] = p.[id]
    ) AS src
    ON c.[cashier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[cashier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[cashier_journals] ([voucher_id])
    SELECT p.[id]
    FROM [dbo].[vouchers] AS p
    INNER JOIN [dbo].[cashier_journals] AS c ON c.[voucher_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[vouchers] AS p2);

    UPDATE c
    SET [voucher_id] = p.[id]
    FROM [dbo].[cashier_journals] AS c
    INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id];

    MERGE INTO [dbo].[cashier_journals] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[vouchers] AS p
        INNER JOIN [dbo].[cashier_journals] AS c2 ON c2.[voucher_id] = p.[id]
    ) AS src
    ON c.[voucher_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[voucher_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[salary_payments] ([employee_id])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[salary_payments] AS c ON c.[employee_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [employee_id] = p.[id]
    FROM [dbo].[salary_payments] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[employee_id] = p.[id];

    MERGE INTO [dbo].[salary_payments] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[salary_payments] AS c2 ON c2.[employee_id] = p.[id]
    ) AS src
    ON c.[employee_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[employee_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[salary_payments] ([voucher_id])
    SELECT p.[id]
    FROM [dbo].[vouchers] AS p
    INNER JOIN [dbo].[salary_payments] AS c ON c.[voucher_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[vouchers] AS p2);

    UPDATE c
    SET [voucher_id] = p.[id]
    FROM [dbo].[salary_payments] AS c
    INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id];

    MERGE INTO [dbo].[salary_payments] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[vouchers] AS p
        INNER JOIN [dbo].[salary_payments] AS c2 ON c2.[voucher_id] = p.[id]
    ) AS src
    ON c.[voucher_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[voucher_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[reconciliations] ([account_id])
    SELECT p.[id]
    FROM [dbo].[accounts] AS p
    INNER JOIN [dbo].[reconciliations] AS c ON c.[account_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[accounts] AS p2);

    UPDATE c
    SET [account_id] = p.[id]
    FROM [dbo].[reconciliations] AS c
    INNER JOIN [dbo].[accounts] AS p ON c.[account_id] = p.[id];

    MERGE INTO [dbo].[reconciliations] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[accounts] AS p
        INNER JOIN [dbo].[reconciliations] AS c2 ON c2.[account_id] = p.[id]
    ) AS src
    ON c.[account_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[account_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[reconciliations] ([prepared_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[reconciliations] AS c ON c.[prepared_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [prepared_by] = p.[id]
    FROM [dbo].[reconciliations] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[prepared_by] = p.[id];

    MERGE INTO [dbo].[reconciliations] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[reconciliations] AS c2 ON c2.[prepared_by] = p.[id]
    ) AS src
    ON c.[prepared_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[prepared_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[reconciliations] ([reviewed_by])
    SELECT p.[id]
    FROM [dbo].[employees] AS p
    INNER JOIN [dbo].[reconciliations] AS c ON c.[reviewed_by] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[employees] AS p2);

    UPDATE c
    SET [reviewed_by] = p.[id]
    FROM [dbo].[reconciliations] AS c
    INNER JOIN [dbo].[employees] AS p ON c.[reviewed_by] = p.[id];

    MERGE INTO [dbo].[reconciliations] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[employees] AS p
        INNER JOIN [dbo].[reconciliations] AS c2 ON c2.[reviewed_by] = p.[id]
    ) AS src
    ON c.[reviewed_by] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[reviewed_by] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[reconciliation_items] ([reconciliation_id])
    SELECT p.[id]
    FROM [dbo].[reconciliations] AS p
    INNER JOIN [dbo].[reconciliation_items] AS c ON c.[reconciliation_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[reconciliations] AS p2);

    UPDATE c
    SET [reconciliation_id] = p.[id]
    FROM [dbo].[reconciliation_items] AS c
    INNER JOIN [dbo].[reconciliations] AS p ON c.[reconciliation_id] = p.[id];

    MERGE INTO [dbo].[reconciliation_items] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[reconciliations] AS p
        INNER JOIN [dbo].[reconciliation_items] AS c2 ON c2.[reconciliation_id] = p.[id]
    ) AS src
    ON c.[reconciliation_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[reconciliation_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_11_common_system_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_11_common_system_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[settlements] ([voucher_id])
    SELECT p.[id]
    FROM [dbo].[vouchers] AS p
    INNER JOIN [dbo].[settlements] AS c ON c.[voucher_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[vouchers] AS p2);

    UPDATE c
    SET [voucher_id] = p.[id]
    FROM [dbo].[settlements] AS c
    INNER JOIN [dbo].[vouchers] AS p ON c.[voucher_id] = p.[id];

    MERGE INTO [dbo].[settlements] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[vouchers] AS p
        INNER JOIN [dbo].[settlements] AS c2 ON c2.[voucher_id] = p.[id]
    ) AS src
    ON c.[voucher_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[voucher_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
