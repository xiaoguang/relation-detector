-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.tr_departments_1_audit
CREATE OR ALTER TRIGGER [dbo].[tr_departments_1_audit] ON [dbo].[departments]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[parent_id], 'insert_departments'
    FROM inserted AS i
    INNER JOIN [dbo].[departments] AS p ON i.[parent_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_positions_2_audit
CREATE OR ALTER TRIGGER [dbo].[tr_positions_2_audit] ON [dbo].[positions]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[department_id], 'insert_positions'
    FROM inserted AS i
    INNER JOIN [dbo].[departments] AS p ON i.[department_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_employees_3_audit
CREATE OR ALTER TRIGGER [dbo].[tr_employees_3_audit] ON [dbo].[employees]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[department_id], 'insert_employees'
    FROM inserted AS i
    INNER JOIN [dbo].[departments] AS p ON i.[department_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_employees_4_audit
CREATE OR ALTER TRIGGER [dbo].[tr_employees_4_audit] ON [dbo].[employees]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[position_id], 'insert_employees'
    FROM inserted AS i
    INNER JOIN [dbo].[positions] AS p ON i.[position_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_employees_5_audit
CREATE OR ALTER TRIGGER [dbo].[tr_employees_5_audit] ON [dbo].[employees]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[manager_id], 'insert_employees'
    FROM inserted AS i
    INNER JOIN [dbo].[employees] AS p ON i.[manager_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_employee_salary_log_6_audit
CREATE OR ALTER TRIGGER [dbo].[tr_employee_salary_log_6_audit] ON [dbo].[employee_salary_log]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[employee_id], 'insert_employee_salary_log'
    FROM inserted AS i
    INNER JOIN [dbo].[employees] AS p ON i.[employee_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_attendance_7_audit
CREATE OR ALTER TRIGGER [dbo].[tr_attendance_7_audit] ON [dbo].[attendance]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[employee_id], 'insert_attendance'
    FROM inserted AS i
    INNER JOIN [dbo].[employees] AS p ON i.[employee_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_leave_records_8_audit
CREATE OR ALTER TRIGGER [dbo].[tr_leave_records_8_audit] ON [dbo].[leave_records]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[employee_id], 'insert_leave_records'
    FROM inserted AS i
    INNER JOIN [dbo].[employees] AS p ON i.[employee_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_permissions_9_audit
CREATE OR ALTER TRIGGER [dbo].[tr_permissions_9_audit] ON [dbo].[permissions]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[parent_id], 'insert_permissions'
    FROM inserted AS i
    INNER JOIN [dbo].[permissions] AS p ON i.[parent_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_role_permissions_10_audit
CREATE OR ALTER TRIGGER [dbo].[tr_role_permissions_10_audit] ON [dbo].[role_permissions]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[role_id], 'insert_role_permissions'
    FROM inserted AS i
    INNER JOIN [dbo].[roles] AS p ON i.[role_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_role_permissions_11_audit
CREATE OR ALTER TRIGGER [dbo].[tr_role_permissions_11_audit] ON [dbo].[role_permissions]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[permission_id], 'insert_role_permissions'
    FROM inserted AS i
    INNER JOIN [dbo].[permissions] AS p ON i.[permission_id] = p.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.tr_employee_roles_12_audit
CREATE OR ALTER TRIGGER [dbo].[tr_employee_roles_12_audit] ON [dbo].[employee_roles]
AFTER INSERT
AS
BEGIN
    INSERT INTO [dbo].[audit_log] ([target_id], [action])
    SELECT i.[employee_id], 'insert_employee_roles'
    FROM inserted AS i
    INNER JOIN [dbo].[employees] AS p ON i.[employee_id] = p.[id];
END;
-- relation-detector-fixture-end
