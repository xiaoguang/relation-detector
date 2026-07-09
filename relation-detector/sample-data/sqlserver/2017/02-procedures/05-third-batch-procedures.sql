-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_rebuild_work_order_material_plan
CREATE OR ALTER PROCEDURE [dbo].[sp_rebuild_work_order_material_plan]
AS
BEGIN
    INSERT INTO [dbo].[work_order_materials] ([work_order_id], [product_id], [required_qty], [issued_qty], [returned_qty], [actual_consumed], [unit], [status])
    SELECT wo.[id], b.[child_product_id],
           wo.[planned_quantity] * b.[quantity],
           0,
           0,
           0,
           b.[unit],
           'planned'
    FROM [dbo].[work_orders] AS wo
    INNER JOIN [dbo].[boms] AS b ON b.[parent_product_id] = wo.[product_id]
    WHERE wo.[status] IN ('planned', 'released');

    UPDATE wo
    SET [status] = CASE WHEN material_summary.[required_qty] > 0 THEN 'materials_planned' ELSE wo.[status] END,
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[work_orders] AS wo
    INNER JOIN (
        SELECT wom.[work_order_id], SUM(wom.[required_qty]) AS [required_qty]
        FROM [dbo].[work_order_materials] AS wom
        GROUP BY wom.[work_order_id]
    ) AS material_summary ON material_summary.[work_order_id] = wo.[id];
END;
-- relation-detector-fixture-end
