-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_refresh_org_security_relationships
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_org_security_relationships]
AS
BEGIN
    INSERT INTO [dbo].[employee_roles] ([employee_id], [role_id], [granted_by], [granted_at])
    SELECT e.[id], r.[id], d.[manager_id], CURRENT_TIMESTAMP
    FROM [dbo].[employees] AS e
    INNER JOIN [dbo].[departments] AS d ON e.[department_id] = d.[id]
    INNER JOIN [dbo].[roles] AS r ON r.[code] = 'EMPLOYEE'
    LEFT JOIN [dbo].[employee_roles] AS er
        ON er.[employee_id] = e.[id] AND er.[role_id] = r.[id]
    WHERE er.[id] IS NULL;

    UPDATE d
    SET [status] = CASE WHEN staffing.[active_headcount] < d.[headcount_plan] THEN 'understaffed' ELSE 'normal' END,
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[departments] AS d
    INNER JOIN (
        SELECT e.[department_id], COUNT(*) AS [active_headcount]
        FROM [dbo].[employees] AS e
        WHERE e.[status] IN ('active', 'probation')
        GROUP BY e.[department_id]
    ) AS staffing ON staffing.[department_id] = d.[id];

    INSERT INTO [dbo].[audit_log] ([employee_id], [action], [target_type], [target_id], [new_value], [created_at])
    SELECT d.[manager_id], 'refresh_org_security', 'department', d.[id], d.[code], CURRENT_TIMESTAMP
    FROM [dbo].[departments] AS d
    WHERE d.[manager_id] IS NOT NULL;
END;
-- relation-detector-fixture-end
