-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_post_sales_return_refunds
CREATE OR ALTER PROCEDURE [dbo].[sp_post_sales_return_refunds]
AS
BEGIN
    INSERT INTO [dbo].[cashier_journals] ([journal_no], [journal_date], [account_id], [cashier_id], [journal_type], [amount], [counterparty], [reference_type], [reference_id], [voucher_id], [status], [remark], [created_at])
    SELECT CONCAT('RF-', sr.[return_no]), sr.[return_date], a.[id], sr.[handler_id],
           'refund', sr.[refund_amount], c.[name], 'sales_return', sr.[id], sr.[refund_voucher_id], 'posted', sr.[return_reason], CURRENT_TIMESTAMP
    FROM [dbo].[sales_returns] AS sr
    INNER JOIN [dbo].[customers] AS c ON c.[id] = sr.[customer_id]
    INNER JOIN [dbo].[accounts] AS a ON a.[is_cash] = 1
    WHERE sr.[refund_amount] > 0;

    UPDATE sr
    SET [status] = 'refunded',
        [approved_at] = CURRENT_TIMESTAMP
    FROM [dbo].[sales_returns] AS sr
    WHERE sr.[refund_voucher_id] IS NOT NULL;
END;
-- relation-detector-fixture-end
