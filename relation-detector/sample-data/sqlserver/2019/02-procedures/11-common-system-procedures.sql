-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_build_cashier_reconciliation_items
CREATE OR ALTER PROCEDURE [dbo].[sp_build_cashier_reconciliation_items]
AS
BEGIN
    INSERT INTO [dbo].[reconciliation_items] ([reconciliation_id], [journal_id], [transaction_date], [description], [debit_amount], [credit_amount], [is_matched])
    SELECT r.[id], cj.[id], cj.[journal_date], cj.[remark],
           CASE WHEN cj.[journal_type] = 'receipt' THEN cj.[amount] ELSE 0 END,
           CASE WHEN cj.[journal_type] = 'payment' THEN cj.[amount] ELSE 0 END,
           0
    FROM [dbo].[reconciliations] AS r
    INNER JOIN [dbo].[cashier_journals] AS cj ON cj.[account_id] = r.[account_id]
    WHERE cj.[journal_date] BETWEEN r.[period_start] AND r.[period_end];

    UPDATE r
    SET [adjusted_balance] = r.[book_balance] + r.[unreconciled_income] - r.[unreconciled_expense],
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[reconciliations] AS r;
END;
-- relation-detector-fixture-end
