-- ============================================================
-- SQL Server ERP natural, set-based triggers.
-- This file uses a SQL Server 2016-compatible baseline shared by all versions.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.tr_audit_employee_insert
CREATE OR ALTER TRIGGER [dbo].[tr_audit_employee_insert]
ON [dbo].[employees]
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO [dbo].[audit_log]
        ([employee_id], [action], [target_type], [target_id], [new_value], [created_at])
    SELECT i.[id], 'employee_created', 'employees', i.[id], i.[employee_no], SYSDATETIME()
    FROM inserted AS i;
END;
-- relation-detector-fixture-end
GO

-- relation-detector-fixture-source:sqlserver.tr_inventory_update_batch
CREATE OR ALTER TRIGGER [dbo].[tr_inventory_update_batch]
ON [dbo].[inventory]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE pb
    SET pb.[current_qty] = i.[quantity]
    FROM [dbo].[product_batches] AS pb
    JOIN inserted AS i ON i.[batch_id] = pb.[id];
END;
-- relation-detector-fixture-end
GO

-- relation-detector-fixture-source:sqlserver.tr_sales_order_delivered
CREATE OR ALTER TRIGGER [dbo].[tr_sales_order_delivered]
ON [dbo].[sales_orders]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE c
    SET c.[balance] = COALESCE(c.[balance], 0)
        + COALESCE(i.[total_amount], 0)
        - COALESCE(i.[paid_amount], 0),
        c.[updated_at] = SYSDATETIME()
    FROM [dbo].[customers] AS c
    JOIN inserted AS i ON i.[customer_id] = c.[id]
    WHERE i.[status] = 'delivered';
END;
-- relation-detector-fixture-end
GO

-- relation-detector-fixture-source:sqlserver.tr_purchase_order_received
CREATE OR ALTER TRIGGER [dbo].[tr_purchase_order_received]
ON [dbo].[purchase_orders]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    INSERT INTO [dbo].[audit_log]
        ([employee_id], [action], [target_type], [target_id], [new_value], [created_at])
    SELECT i.[purchaser_id], 'purchase_order_received', 'purchase_orders',
           i.[id], i.[order_no], SYSDATETIME()
    FROM inserted AS i
    WHERE i.[status] = 'received';
END;
-- relation-detector-fixture-end
GO

-- relation-detector-fixture-source:sqlserver.tr_sales_return_approved
CREATE OR ALTER TRIGGER [dbo].[tr_sales_return_approved]
ON [dbo].[sales_returns]
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE soi
    SET soi.[returned_qty] = COALESCE(soi.[returned_qty], 0) + sri.[return_qty]
    FROM [dbo].[sales_order_items] AS soi
    JOIN [dbo].[sales_return_items] AS sri ON sri.[order_item_id] = soi.[id]
    JOIN inserted AS i ON i.[id] = sri.[return_id]
    WHERE i.[status] = 'approved';
END;
-- relation-detector-fixture-end
GO

-- relation-detector-fixture-source:sqlserver.tr_inventory_transaction_after_insert
CREATE OR ALTER TRIGGER [dbo].[tr_inventory_transaction_after_insert]
ON [dbo].[inventory_transactions]
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE inv
    SET inv.[quantity] = it.[after_qty],
        inv.[updated_at] = SYSDATETIME()
    FROM [dbo].[inventory] AS inv
    JOIN inserted AS it
      ON inv.[product_id] = it.[product_id]
     AND inv.[warehouse_id] = it.[warehouse_id]
     AND (inv.[batch_id] = it.[batch_id]
          OR (inv.[batch_id] IS NULL AND it.[batch_id] IS NULL));
END;
-- relation-detector-fixture-end
GO
