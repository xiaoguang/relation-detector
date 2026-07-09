-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_refresh_supplier_product_metrics
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_supplier_product_metrics]
AS
BEGIN
    MERGE INTO [dbo].[supplier_products] AS target
    USING (
        SELECT po.[supplier_id], poi.[product_id],
               AVG(poi.[unit_price]) AS [avg_price],
               COUNT(*) AS [order_count],
               SUM(poi.[quantity]) AS [ordered_qty],
               MAX(po.[order_date]) AS [last_order_date]
        FROM [dbo].[purchase_orders] AS po
        INNER JOIN [dbo].[purchase_order_items] AS poi ON poi.[order_id] = po.[id]
        GROUP BY po.[supplier_id], poi.[product_id]
    ) AS src
    ON target.[supplier_id] = src.[supplier_id] AND target.[product_id] = src.[product_id]
    WHEN MATCHED THEN UPDATE SET
        target.[supplier_price] = src.[avg_price],
        target.[total_order_count] = src.[order_count],
        target.[total_order_qty] = src.[ordered_qty],
        target.[last_order_date] = src.[last_order_date];

    UPDATE p
    SET [purchase_price] = supplier_cost.[preferred_price],
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[products] AS p
    INNER JOIN (
        SELECT sp.[product_id], MIN(sp.[supplier_price]) AS [preferred_price]
        FROM [dbo].[supplier_products] AS sp
        WHERE sp.[is_preferred] = 1
        GROUP BY sp.[product_id]
    ) AS supplier_cost ON supplier_cost.[product_id] = p.[id];
END;
-- relation-detector-fixture-end
