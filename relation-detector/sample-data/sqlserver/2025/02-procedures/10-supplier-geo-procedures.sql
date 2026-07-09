-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_refresh_supplier_geo_quality
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_supplier_geo_quality]
AS
BEGIN
    UPDATE s
    SET [credit_level] = CASE WHEN scorecard.[avg_quality] >= 95 THEN 'A' WHEN scorecard.[avg_quality] >= 85 THEN 'B' ELSE 'C' END,
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[suppliers] AS s
    INNER JOIN (
        SELECT sp.[supplier_id], AVG(sp.[quality_score]) AS [avg_quality]
        FROM [dbo].[supplier_products] AS sp
        GROUP BY sp.[supplier_id]
    ) AS scorecard ON scorecard.[supplier_id] = s.[id];

    UPDATE sp
    SET [shipping_cost_per_km] = CASE WHEN s.[province] = w.[province] THEN 1.20 ELSE 2.50 END
    FROM [dbo].[supplier_products] AS sp
    INNER JOIN [dbo].[suppliers] AS s ON s.[id] = sp.[supplier_id]
    INNER JOIN [dbo].[inventory] AS i ON i.[product_id] = sp.[product_id]
    INNER JOIN [dbo].[warehouses] AS w ON w.[id] = i.[warehouse_id];
END;
-- relation-detector-fixture-end
