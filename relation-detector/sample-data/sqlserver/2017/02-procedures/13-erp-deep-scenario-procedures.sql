-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_run_mrp_for_plan
CREATE OR ALTER PROCEDURE [dbo].[sp_run_mrp_for_plan]
AS
BEGIN
    INSERT INTO [dbo].[mrp_run_items] ([run_id], [parent_product_id], [component_product_id], [gross_requirement], [on_hand_qty], [reserved_qty], [planned_receipt_qty], [net_requirement], [suggested_order_qty], [suggested_supplier_id], [suggested_due_date])
    SELECT mr.[id], b.[parent_product_id], b.[child_product_id],
           pp.[planned_production_qty] * b.[quantity],
           COALESCE(SUM(i.[available_quantity]), 0),
           0,
           COALESCE(SUM(poi.[quantity] - poi.[received_qty]), 0),
           CASE WHEN pp.[planned_production_qty] * b.[quantity] - COALESCE(SUM(i.[available_quantity]), 0) > 0
                THEN pp.[planned_production_qty] * b.[quantity] - COALESCE(SUM(i.[available_quantity]), 0) ELSE 0 END,
           CASE WHEN pp.[planned_production_qty] * b.[quantity] - COALESCE(SUM(i.[available_quantity]), 0) > 0
                THEN pp.[planned_production_qty] * b.[quantity] - COALESCE(SUM(i.[available_quantity]), 0) ELSE 0 END,
           MIN(sp.[supplier_id]),
           DATEADD(day, COALESCE(MAX(sp.[lead_time_days]), 7), mr.[run_date])
    FROM [dbo].[mrp_runs] AS mr
    INNER JOIN [dbo].[production_plans] AS pp ON pp.[id] = mr.[plan_id]
    INNER JOIN [dbo].[boms] AS b ON b.[parent_product_id] = pp.[product_id]
    LEFT JOIN [dbo].[inventory] AS i ON i.[product_id] = b.[child_product_id]
    LEFT JOIN [dbo].[purchase_order_items] AS poi ON poi.[product_id] = b.[child_product_id]
    LEFT JOIN [dbo].[supplier_products] AS sp ON sp.[product_id] = b.[child_product_id] AND sp.[is_preferred] = 1
    GROUP BY mr.[id], mr.[run_date], b.[parent_product_id], b.[child_product_id], b.[quantity], pp.[planned_production_qty];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_calculate_work_order_actual_cost
CREATE OR ALTER PROCEDURE [dbo].[sp_calculate_work_order_actual_cost]
AS
BEGIN
    MERGE INTO [dbo].[work_order_costs] AS target
    USING (
        SELECT wo.[id] AS [work_order_id],
               SUM(wom.[actual_consumed] * COALESCE(pb.[purchase_price], 0)) AS [material_cost],
               wo.[completed_quantity] AS [finished_qty]
        FROM [dbo].[work_orders] AS wo
        INNER JOIN [dbo].[work_order_materials] AS wom ON wom.[work_order_id] = wo.[id]
        LEFT JOIN [dbo].[product_batches] AS pb ON pb.[id] = wom.[batch_id]
        GROUP BY wo.[id], wo.[completed_quantity]
    ) AS src
    ON target.[work_order_id] = src.[work_order_id]
    WHEN MATCHED THEN UPDATE SET
        target.[material_cost] = src.[material_cost],
        target.[finished_qty] = src.[finished_qty],
        target.[unit_cost] = CASE WHEN src.[finished_qty] > 0 THEN src.[material_cost] / src.[finished_qty] ELSE 0 END,
        target.[calculated_at] = CURRENT_TIMESTAMP
    WHEN NOT MATCHED THEN INSERT ([work_order_id], [material_cost], [finished_qty], [unit_cost], [calculated_at])
        VALUES (src.[work_order_id], src.[material_cost], src.[finished_qty], CASE WHEN src.[finished_qty] > 0 THEN src.[material_cost] / src.[finished_qty] ELSE 0 END, CURRENT_TIMESTAMP);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_post_finished_goods_receipt
CREATE OR ALTER PROCEDURE [dbo].[sp_post_finished_goods_receipt]
AS
BEGIN
    INSERT INTO [dbo].[inventory_transactions] ([product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change], [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [created_at])
    SELECT fgr.[product_id], fgr.[batch_id], fgr.[warehouse_id], 'finished_goods_receipt',
           fgr.[received_qty], COALESCE(i.[quantity], 0), COALESCE(i.[quantity], 0) + fgr.[received_qty],
           'finished_goods_receipt', fgr.[id], fgr.[received_by], CURRENT_TIMESTAMP
    FROM [dbo].[finished_goods_receipts] AS fgr
    LEFT JOIN [dbo].[inventory] AS i
        ON i.[product_id] = fgr.[product_id] AND i.[batch_id] = fgr.[batch_id] AND i.[warehouse_id] = fgr.[warehouse_id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_post_cogs_for_sales_order
CREATE OR ALTER PROCEDURE [dbo].[sp_post_cogs_for_sales_order]
AS
BEGIN
    INSERT INTO [dbo].[cogs_entries] ([sales_order_id], [sales_order_item_id], [product_id], [batch_id], [quantity], [unit_cost], [cogs_amount], [posted_at])
    SELECT so.[id], soi.[id], soi.[product_id], soi.[batch_id], soi.[quantity],
           COALESCE(icl.[unit_cost], 0),
           soi.[quantity] * COALESCE(icl.[unit_cost], 0),
           CURRENT_TIMESTAMP
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = so.[id]
    LEFT JOIN [dbo].[inventory_cost_layers] AS icl
        ON icl.[product_id] = soi.[product_id] AND icl.[batch_id] = soi.[batch_id]
    WHERE so.[status] = 'delivered';
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_generate_picking_task_for_order
CREATE OR ALTER PROCEDURE [dbo].[sp_generate_picking_task_for_order]
AS
BEGIN
    INSERT INTO [dbo].[picking_tasks] ([task_no], [sales_order_id], [warehouse_id], [wave_no], [assigned_to], [status])
    SELECT CONCAT('PICK-', so.[order_no]), so.[id], so.[warehouse_id], CONCAT('W-', CONVERT(NVARCHAR(8), so.[order_date], 112)), so.[salesperson_id], 'open'
    FROM [dbo].[sales_orders] AS so
    WHERE so.[status] IN ('approved', 'paid');

    INSERT INTO [dbo].[picking_task_items] ([picking_task_id], [sales_order_item_id], [product_id], [batch_id], [location_id], [required_qty], [picked_qty])
    SELECT pt.[id], soi.[id], soi.[product_id], soi.[batch_id], ilb.[location_id], soi.[quantity], 0
    FROM [dbo].[picking_tasks] AS pt
    INNER JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = pt.[sales_order_id]
    LEFT JOIN [dbo].[inventory_location_balances] AS ilb
        ON ilb.[product_id] = soi.[product_id] AND ilb.[batch_id] = soi.[batch_id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_issue_repair_order_parts
CREATE OR ALTER PROCEDURE [dbo].[sp_issue_repair_order_parts]
AS
BEGIN
    INSERT INTO [dbo].[inventory_transactions] ([product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change], [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [created_at])
    SELECT rop.[product_id], rop.[batch_id], rop.[issued_from_warehouse_id], 'repair_part_issue',
           0 - rop.[quantity], i.[quantity], i.[quantity] - rop.[quantity], 'repair_order', rop.[repair_order_id], ro.[technician_id], CURRENT_TIMESTAMP
    FROM [dbo].[repair_order_parts] AS rop
    INNER JOIN [dbo].[repair_orders] AS ro ON ro.[id] = rop.[repair_order_id]
    INNER JOIN [dbo].[inventory] AS i
        ON i.[product_id] = rop.[product_id] AND i.[batch_id] = rop.[batch_id] AND i.[warehouse_id] = rop.[issued_from_warehouse_id];

    UPDATE i
    SET [quantity] = i.[quantity] - rop.[quantity],
        [available_quantity] = i.[available_quantity] - rop.[quantity],
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[inventory] AS i
    INNER JOIN [dbo].[repair_order_parts] AS rop
        ON rop.[product_id] = i.[product_id] AND rop.[batch_id] = i.[batch_id] AND rop.[issued_from_warehouse_id] = i.[warehouse_id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_create_ar_invoice_from_sales_order
CREATE OR ALTER PROCEDURE [dbo].[sp_create_ar_invoice_from_sales_order]
AS
BEGIN
    INSERT INTO [dbo].[ar_invoices] ([ar_no], [sales_order_id], [customer_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [writeoff_amount], [status])
    SELECT CONCAT('AR-', so.[order_no]), so.[id], so.[customer_id], so.[order_date], DATEADD(day, c.[credit_days], so.[order_date]),
           so.[total_amount], so.[paid_amount], so.[discount_amount],
           CASE WHEN so.[paid_amount] >= so.[total_amount] THEN 'paid' ELSE 'open' END
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[customers] AS c ON c.[id] = so.[customer_id]
    WHERE so.[status] IN ('delivered', 'paid');
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_create_ap_invoice_from_purchase_order
CREATE OR ALTER PROCEDURE [dbo].[sp_create_ap_invoice_from_purchase_order]
AS
BEGIN
    INSERT INTO [dbo].[ap_invoices] ([ap_no], [purchase_order_id], [supplier_id], [invoice_date], [due_date], [invoice_amount], [paid_amount], [status])
    SELECT CONCAT('AP-', po.[order_no]), po.[id], po.[supplier_id], po.[order_date], DATEADD(day, 30, po.[order_date]),
           po.[total_amount], po.[paid_amount],
           CASE WHEN po.[paid_amount] >= po.[total_amount] THEN 'paid' ELSE 'open' END
    FROM [dbo].[purchase_orders] AS po
    INNER JOIN [dbo].[suppliers] AS s ON s.[id] = po.[supplier_id]
    WHERE po.[status] IN ('received', 'closed');
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_refresh_budget_usage
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_budget_usage]
AS
BEGIN
    UPDATE bi
    SET [used_amount] = COALESCE(usage_by_subject.[used_amount], 0)
    FROM [dbo].[budget_items] AS bi
    INNER JOIN [dbo].[account_subjects] AS subject
        ON subject.[id] = bi.[subject_id]
    LEFT JOIN (
        SELECT
            a.[code] AS [subject_code],
            CONVERT(NVARCHAR(7), v.[voucher_date], 120) AS [period_code],
            SUM(CASE WHEN vi.[direction] = 'debit' THEN vi.[amount] ELSE 0 END) AS [used_amount]
        FROM [dbo].[vouchers] AS v
        INNER JOIN [dbo].[voucher_items] AS vi ON vi.[voucher_id] = v.[id]
        INNER JOIN [dbo].[accounts] AS a ON a.[id] = vi.[account_id]
        WHERE v.[status] = 'posted'
        GROUP BY a.[code], CONVERT(NVARCHAR(7), v.[voucher_date], 120)
    ) AS usage_by_subject
        ON usage_by_subject.[subject_code] = subject.[subject_code]
       AND usage_by_subject.[period_code] = bi.[period_code];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_apply_customer_master_data_change
CREATE OR ALTER PROCEDURE [dbo].[sp_apply_customer_master_data_change]
AS
BEGIN
    UPDATE c
    SET [address] = CASE WHEN item.[field_name] IN ('registered_address', 'address') THEN item.[new_value] ELSE c.[address] END,
        [contact_person] = CASE WHEN item.[field_name] = 'contact_person' THEN item.[new_value] ELSE c.[contact_person] END,
        [phone] = CASE WHEN item.[field_name] = 'phone' THEN item.[new_value] ELSE c.[phone] END,
        [email] = CASE WHEN item.[field_name] = 'email' THEN item.[new_value] ELSE c.[email] END,
        [updated_at] = CURRENT_TIMESTAMP
    FROM [dbo].[customers] AS c
    INNER JOIN [dbo].[master_data_change_requests] AS request
        ON request.[master_id] = c.[id] AND request.[master_type] = 'customer'
    INNER JOIN [dbo].[master_data_change_items] AS item
        ON item.[request_id] = request.[id]
    WHERE request.[status] = 'approved';

    INSERT INTO [dbo].[audit_log] ([employee_id], [action], [target_type], [target_id], [new_value], [created_at])
    SELECT request.[approved_by], 'apply_customer_master_data_change', request.[master_type], request.[master_id], item.[new_value], CURRENT_TIMESTAMP
    FROM [dbo].[master_data_change_requests] AS request
    INNER JOIN [dbo].[master_data_change_items] AS item ON item.[request_id] = request.[id]
    WHERE request.[status] = 'approved';
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_refresh_semantic_dimensions
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_semantic_dimensions]
AS
BEGIN
    MERGE INTO [dbo].[region_dim] AS target
    USING (
        SELECT
            'REG-' + w.[code] AS [region_code],
            w.[city] + N'销售区' AS [region_name],
            w.[province],
            w.[city],
            w.[district],
            w.[province] AS [sales_region],
            'warehouse-city' AS [region_level]
        FROM [dbo].[warehouses] AS w
    ) AS src
    ON target.[province] = src.[province]
       AND COALESCE(target.[city], '') = COALESCE(src.[city], '')
       AND COALESCE(target.[district], '') = COALESCE(src.[district], '')
    WHEN MATCHED THEN UPDATE SET
        target.[region_code] = src.[region_code],
        target.[region_name] = src.[region_name],
        target.[sales_region] = src.[sales_region],
        target.[region_level] = src.[region_level],
        target.[is_active] = 1
    WHEN NOT MATCHED THEN INSERT ([region_code], [region_name], [province], [city], [district], [sales_region], [region_level], [is_active])
        VALUES (src.[region_code], src.[region_name], src.[province], src.[city], src.[district], src.[sales_region], src.[region_level], 1);

    MERGE INTO [dbo].[category_dim] AS target
    USING (
        SELECT pc.[id] AS [source_category_id], pc.[code] AS [category_code],
               COALESCE(parent.[name], pc.[name]) AS [level1_name],
               CASE WHEN parent.[id] IS NULL THEN NULL ELSE pc.[name] END AS [level2_name],
               pc.[name] AS [leaf_name], pc.[status], CAST(pc.[created_at] AS DATE) AS [effective_from]
        FROM [dbo].[product_categories] AS pc
        LEFT JOIN [dbo].[product_categories] AS parent ON parent.[id] = pc.[parent_id]
    ) AS src
    ON target.[source_category_id] = src.[source_category_id]
    WHEN MATCHED THEN UPDATE SET
        target.[category_code] = src.[category_code],
        target.[level1_name] = src.[level1_name],
        target.[level2_name] = src.[level2_name],
        target.[leaf_name] = src.[leaf_name],
        target.[status] = src.[status]
    WHEN NOT MATCHED THEN INSERT ([source_category_id], [category_code], [level1_name], [level2_name], [leaf_name], [is_womenwear], [effective_from], [status])
        VALUES (src.[source_category_id], src.[category_code], src.[level1_name], src.[level2_name], src.[leaf_name], 0, src.[effective_from], src.[status]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_record_customer_payment
CREATE OR ALTER PROCEDURE [dbo].[sp_record_customer_payment]
AS
BEGIN
    INSERT INTO [dbo].[payments] ([payment_no], [customer_id], [order_id], [journal_id], [payment_date], [amount], [currency], [payment_method], [payment_status], [created_at])
    SELECT CONCAT('PAY-', so.[order_no]), so.[customer_id], so.[id], cj.[id], cj.[journal_date], cj.[amount], 'CNY', so.[payment_method], cj.[status], cj.[created_at]
    FROM [dbo].[cashier_journals] AS cj
    INNER JOIN [dbo].[sales_orders] AS so
        ON so.[id] = cj.[reference_id] AND cj.[reference_type] = 'sales_order'
    WHERE cj.[journal_type] = 'receipt';

    UPDATE so
    SET [paid_amount] = so.[paid_amount] + p.[amount],
        [status] = CASE WHEN so.[paid_amount] + p.[amount] >= so.[total_amount] THEN 'paid' ELSE so.[status] END
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[payments] AS p ON p.[order_id] = so.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_rebuild_sales_fact
CREATE OR ALTER PROCEDURE [dbo].[sp_rebuild_sales_fact]
AS
BEGIN
    INSERT INTO [dbo].[sales_fact] ([order_id], [order_item_id], [customer_id], [product_id], [category_dim_id], [warehouse_id], [region_dim_id], [fiscal_date], [payment_id], [quantity_sold], [sales_amount], [paid_amount], [refund_amount], [net_sales_amount], [gross_margin_amount], [order_status], [sales_channel], [created_at])
    SELECT so.[id], soi.[id], so.[customer_id], soi.[product_id], cd.[id], so.[warehouse_id], rd.[id], so.[order_date], p.[id],
           soi.[quantity], soi.[amount], COALESCE(p.[amount], so.[paid_amount]), COALESCE(sr.[refund_amount], 0),
           soi.[amount] - COALESCE(sr.[refund_amount], 0), soi.[amount] - COALESCE(ce.[cogs_amount], 0),
           so.[status], so.[payment_method], so.[created_at]
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = so.[id]
    INNER JOIN [dbo].[products] AS pr ON pr.[id] = soi.[product_id]
    LEFT JOIN [dbo].[category_dim] AS cd ON cd.[source_category_id] = pr.[category_id]
    LEFT JOIN [dbo].[warehouses] AS w ON w.[id] = so.[warehouse_id]
    LEFT JOIN [dbo].[region_dim] AS rd ON rd.[province] = w.[province] AND rd.[city] = w.[city]
    LEFT JOIN [dbo].[payments] AS p ON p.[order_id] = so.[id]
    LEFT JOIN [dbo].[sales_returns] AS sr ON sr.[order_id] = so.[id]
    LEFT JOIN [dbo].[cogs_entries] AS ce ON ce.[sales_order_item_id] = soi.[id];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_onboard_employee_full
CREATE OR ALTER PROCEDURE [dbo].[sp_onboard_employee_full]
AS
BEGIN
    INSERT INTO [dbo].[employee_roles] ([employee_id], [role_id], [granted_by], [granted_at])
    SELECT e.[id], r.[id], e.[manager_id], CURRENT_TIMESTAMP
    FROM [dbo].[employees] AS e
    INNER JOIN [dbo].[roles] AS r ON r.[code] = 'EMPLOYEE'
    LEFT JOIN [dbo].[employee_roles] AS er ON er.[employee_id] = e.[id] AND er.[role_id] = r.[id]
    WHERE er.[id] IS NULL;

    INSERT INTO [dbo].[employee_shift_assignments] ([employee_id], [shift_id], [work_date], [status])
    SELECT e.[id], es.[id], e.[hire_date], 'scheduled'
    FROM [dbo].[employees] AS e
    CROSS APPLY (
        SELECT TOP (1) candidate.[id]
        FROM [dbo].[employee_shifts] AS candidate
        ORDER BY candidate.[start_time], candidate.[id]
    ) AS es
    WHERE e.[status] IN ('active', 'probation');
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_refresh_inventory_valuation_snapshot
CREATE OR ALTER PROCEDURE [dbo].[sp_refresh_inventory_valuation_snapshot]
AS
BEGIN
    INSERT INTO [dbo].[inventory_valuation_snapshots] ([snapshot_date], [product_id], [warehouse_id], [quantity], [unit_cost], [inventory_value], [valuation_method])
    SELECT CURRENT_TIMESTAMP, i.[product_id], i.[warehouse_id], i.[quantity], COALESCE(icl.[unit_cost], p.[purchase_price]),
           i.[quantity] * COALESCE(icl.[unit_cost], p.[purchase_price]), 'moving_average'
    FROM [dbo].[inventory] AS i
    INNER JOIN [dbo].[products] AS p ON p.[id] = i.[product_id]
    LEFT JOIN [dbo].[inventory_cost_layers] AS icl
        ON icl.[product_id] = i.[product_id] AND icl.[batch_id] = i.[batch_id] AND icl.[warehouse_id] = i.[warehouse_id];
END;
-- relation-detector-fixture-end
