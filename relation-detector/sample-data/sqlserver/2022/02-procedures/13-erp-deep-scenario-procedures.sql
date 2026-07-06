-- ============================================================
-- SQL Server ERP sample data translated from MySQL 8.0 business sample.
-- This corpus is intentionally T-SQL 2016-compatible so the same business
-- semantics can be exercised by SQL Server 2016/2017/2019/2022/2025.
-- ============================================================

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_1
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_1]
AS
BEGIN
    INSERT INTO [dbo].[promotion_products] ([product_id])
    SELECT p.[id]
    FROM [dbo].[products] AS p
    INNER JOIN [dbo].[promotion_products] AS c ON c.[product_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[products] AS p2);

    UPDATE c
    SET [product_id] = p.[id]
    FROM [dbo].[promotion_products] AS c
    INNER JOIN [dbo].[products] AS p ON c.[product_id] = p.[id];

    MERGE INTO [dbo].[promotion_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[products] AS p
        INNER JOIN [dbo].[promotion_products] AS c2 ON c2.[product_id] = p.[id]
    ) AS src
    ON c.[product_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[product_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_run_mrp_for_plan
CREATE OR ALTER PROCEDURE [dbo].[sp_run_mrp_for_plan]
    @p_plan_id BIGINT,
    @p_created_by BIGINT
AS
BEGIN
    INSERT INTO [dbo].[mrp_runs] (
        [run_no], [plan_id], [run_date], [demand_source], [status], [created_by]
    )
    SELECT
        CONCAT(N'MRP-', REPLACE(pp.[plan_month], N'-', N''), N'-', pp.[id]),
        pp.[id],
        CAST(GETDATE() AS DATE),
        N'forecast',
        N'running',
        @p_created_by
    FROM [dbo].[production_plans] AS pp
    WHERE pp.[id] = @p_plan_id;

    INSERT INTO [dbo].[mrp_run_items] (
        [run_id], [parent_product_id], [component_product_id], [gross_requirement],
        [on_hand_qty], [reserved_qty], [planned_receipt_qty], [net_requirement],
        [suggested_order_qty], [suggested_supplier_id], [suggested_due_date]
    )
    SELECT
        pp.[id],
        pp.[product_id],
        bom.[child_product_id],
        ROUND(pp.[planned_production_qty] * bom.[quantity] * (1 + bom.[scrap_rate]), 4),
        ISNULL(inv.[on_hand_qty], 0),
        ISNULL(res.[reserved_qty], 0),
        ISNULL(po.[open_receipt_qty], 0),
        CASE
            WHEN ROUND(pp.[planned_production_qty] * bom.[quantity] * (1 + bom.[scrap_rate]), 4)
                 - ISNULL(inv.[on_hand_qty], 0)
                 + ISNULL(res.[reserved_qty], 0)
                 - ISNULL(po.[open_receipt_qty], 0) > 0
            THEN ROUND(pp.[planned_production_qty] * bom.[quantity] * (1 + bom.[scrap_rate]), 4)
                 - ISNULL(inv.[on_hand_qty], 0)
                 + ISNULL(res.[reserved_qty], 0)
                 - ISNULL(po.[open_receipt_qty], 0)
            ELSE 0
        END,
        CEILING(CASE
            WHEN ROUND(pp.[planned_production_qty] * bom.[quantity] * (1 + bom.[scrap_rate]), 4)
                 - ISNULL(inv.[on_hand_qty], 0)
                 + ISNULL(res.[reserved_qty], 0)
                 - ISNULL(po.[open_receipt_qty], 0) > 0
            THEN ROUND(pp.[planned_production_qty] * bom.[quantity] * (1 + bom.[scrap_rate]), 4)
                 - ISNULL(inv.[on_hand_qty], 0)
                 + ISNULL(res.[reserved_qty], 0)
                 - ISNULL(po.[open_receipt_qty], 0)
            ELSE 0
        END),
        pref.[supplier_id],
        DATEADD(DAY, ISNULL(pref.[lead_time_days], 7), CAST(GETDATE() AS DATE))
    FROM [dbo].[production_plans] AS pp
    INNER JOIN [dbo].[boms] AS bom ON bom.[parent_product_id] = pp.[product_id]
    LEFT JOIN (
        SELECT [product_id], SUM([quantity] - [locked_quantity]) AS [on_hand_qty]
        FROM [dbo].[inventory]
        GROUP BY [product_id]
    ) AS inv ON inv.[product_id] = bom.[child_product_id]
    LEFT JOIN (
        SELECT [product_id], SUM([reserved_quantity] - [released_quantity]) AS [reserved_qty]
        FROM [dbo].[inventory_reservations]
        GROUP BY [product_id]
    ) AS res ON res.[product_id] = bom.[child_product_id]
    LEFT JOIN (
        SELECT poi.[product_id], SUM(poi.[quantity] - poi.[received_qty]) AS [open_receipt_qty]
        FROM [dbo].[purchase_order_items] AS poi
        INNER JOIN [dbo].[purchase_orders] AS po ON po.[id] = poi.[order_id]
        GROUP BY poi.[product_id]
    ) AS po ON po.[product_id] = bom.[child_product_id]
    LEFT JOIN (
        SELECT [product_id], MIN([supplier_id]) AS [supplier_id], MIN([lead_time_days]) AS [lead_time_days]
        FROM [dbo].[supplier_products]
        GROUP BY [product_id]
    ) AS pref ON pref.[product_id] = bom.[child_product_id]
    WHERE pp.[id] = @p_plan_id;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_calculate_work_order_actual_cost
CREATE OR ALTER PROCEDURE [dbo].[sp_calculate_work_order_actual_cost]
    @p_work_order_id BIGINT
AS
BEGIN
    MERGE INTO [dbo].[work_order_costs] AS target
    USING (
        SELECT
            wo.[id] AS [work_order_id],
            ISNULL(mat.[material_cost], 0) AS [material_cost],
            ISNULL(lab.[labor_cost], 0) AS [labor_cost],
            ISNULL(lab.[labor_cost], 0) * 0.60 AS [overhead_cost],
            ISNULL(fg.[finished_qty], wo.[completed_quantity]) AS [finished_qty],
            ROUND((ISNULL(mat.[material_cost], 0) + ISNULL(lab.[labor_cost], 0) + ISNULL(lab.[labor_cost], 0) * 0.60)
                  / NULLIF(ISNULL(fg.[finished_qty], wo.[completed_quantity]), 0), 4) AS [unit_cost],
            ROUND((ISNULL(mat.[material_cost], 0) + ISNULL(lab.[labor_cost], 0) + ISNULL(lab.[labor_cost], 0) * 0.60)
                  - ISNULL(std.[standard_amount], 0), 2) AS [variance_amount]
        FROM [dbo].[work_orders] AS wo
        LEFT JOIN (
            SELECT mi.[work_order_id], SUM(mii.[issued_qty] * mii.[unit_cost]) AS [material_cost]
            FROM [dbo].[material_issues] AS mi
            INNER JOIN [dbo].[material_issue_items] AS mii ON mii.[issue_id] = mi.[id]
            GROUP BY mi.[work_order_id]
        ) AS mat ON mat.[work_order_id] = wo.[id]
        LEFT JOIN (
            SELECT woo.[work_order_id], SUM(opr.[labor_minutes] * 1.20) AS [labor_cost]
            FROM [dbo].[work_order_operations] AS woo
            INNER JOIN [dbo].[operation_reports] AS opr ON opr.[work_order_operation_id] = woo.[id]
            GROUP BY woo.[work_order_id]
        ) AS lab ON lab.[work_order_id] = wo.[id]
        LEFT JOIN (
            SELECT [work_order_id], SUM([received_qty]) AS [finished_qty]
            FROM [dbo].[finished_goods_receipts]
            GROUP BY [work_order_id]
        ) AS fg ON fg.[work_order_id] = wo.[id]
        LEFT JOIN (
            SELECT wo2.[id] AS [work_order_id],
                   wo2.[planned_quantity] * (sc.[material_cost] + sc.[labor_cost] + sc.[overhead_cost]) AS [standard_amount]
            FROM [dbo].[work_orders] AS wo2
            INNER JOIN [dbo].[standard_costs] AS sc ON sc.[product_id] = wo2.[product_id]
        ) AS std ON std.[work_order_id] = wo.[id]
        WHERE wo.[id] = @p_work_order_id
    ) AS src
    ON target.[work_order_id] = src.[work_order_id]
    WHEN MATCHED THEN UPDATE SET
        target.[material_cost] = src.[material_cost],
        target.[labor_cost] = src.[labor_cost],
        target.[overhead_cost] = src.[overhead_cost],
        target.[finished_qty] = src.[finished_qty],
        target.[unit_cost] = src.[unit_cost],
        target.[variance_amount] = src.[variance_amount];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_post_finished_goods_receipt
CREATE OR ALTER PROCEDURE [dbo].[sp_post_finished_goods_receipt]
    @p_receipt_id BIGINT,
    @p_operator_id BIGINT
AS
BEGIN
    INSERT INTO [dbo].[inventory_cost_layers] (
        [product_id], [batch_id], [warehouse_id], [source_type], [source_id], [receipt_date],
        [original_qty], [remaining_qty], [unit_cost], [currency]
    )
    SELECT
        fgr.[product_id],
        fgr.[batch_id],
        fgr.[warehouse_id],
        N'work_order_receipt',
        fgr.[id],
        fgr.[receipt_date],
        fgr.[received_qty],
        fgr.[received_qty],
        ISNULL(woc.[unit_cost], fgr.[unit_cost]),
        N'CNY'
    FROM [dbo].[finished_goods_receipts] AS fgr
    LEFT JOIN [dbo].[work_order_costs] AS woc ON woc.[work_order_id] = fgr.[work_order_id]
    WHERE fgr.[id] = @p_receipt_id;

    INSERT INTO [dbo].[inventory_transactions] (
        [product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change],
        [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark]
    )
    SELECT
        fgr.[product_id],
        fgr.[batch_id],
        fgr.[warehouse_id],
        N'production_in',
        fgr.[received_qty],
        ISNULL(inv.[quantity], 0),
        ISNULL(inv.[quantity], 0) + fgr.[received_qty],
        N'finished_goods_receipt',
        fgr.[id],
        @p_operator_id,
        CONCAT(N'finished receipt ', fgr.[receipt_no])
    FROM [dbo].[finished_goods_receipts] AS fgr
    LEFT JOIN [dbo].[inventory] AS inv ON inv.[product_id] = fgr.[product_id]
        AND inv.[warehouse_id] = fgr.[warehouse_id]
        AND inv.[batch_id] = fgr.[batch_id]
    WHERE fgr.[id] = @p_receipt_id;

    MERGE INTO [dbo].[inventory] AS inv
    USING (
        SELECT
            fgr.[product_id],
            fgr.[batch_id],
            fgr.[warehouse_id],
            fgr.[received_qty],
            fgr.[receipt_date]
        FROM [dbo].[finished_goods_receipts] AS fgr
        WHERE fgr.[id] = @p_receipt_id
    ) AS src
    ON inv.[product_id] = src.[product_id]
       AND inv.[warehouse_id] = src.[warehouse_id]
       AND inv.[batch_id] = src.[batch_id]
    WHEN MATCHED THEN UPDATE SET
        inv.[quantity] = inv.[quantity] + src.[received_qty],
        inv.[last_stocktake_date] = src.[receipt_date];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_post_cogs_for_sales_order
CREATE OR ALTER PROCEDURE [dbo].[sp_post_cogs_for_sales_order]
    @p_sales_order_id BIGINT
AS
BEGIN
    INSERT INTO [dbo].[cogs_entries] (
        [sales_order_id], [product_id], [batch_id], [quantity], [unit_cost], [cogs_amount]
    )
    SELECT
        so.[id],
        soi.[product_id],
        soi.[batch_id],
        soi.[quantity],
        ISNULL(icl.[unit_cost], p.[purchase_price]),
        soi.[quantity] * ISNULL(icl.[unit_cost], p.[purchase_price])
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = so.[id]
    INNER JOIN [dbo].[products] AS p ON p.[id] = soi.[product_id]
    LEFT JOIN [dbo].[inventory_cost_layers] AS icl ON icl.[product_id] = soi.[product_id]
        AND icl.[batch_id] = soi.[batch_id]
    WHERE so.[id] = @p_sales_order_id;
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_generate_picking_task_for_order
CREATE OR ALTER PROCEDURE [dbo].[sp_generate_picking_task_for_order]
    @p_sales_order_id BIGINT,
    @p_picker_id BIGINT
AS
BEGIN
    INSERT INTO [dbo].[picking_tasks] (
        [task_no], [sales_order_id], [warehouse_id], [picker_id], [status]
    )
    SELECT
        CONCAT(N'PICK-', so.[order_no]),
        so.[id],
        so.[warehouse_id],
        @p_picker_id,
        N'created'
    FROM [dbo].[sales_orders] AS so
    WHERE so.[id] = @p_sales_order_id;

    INSERT INTO [dbo].[picking_task_items] (
        [task_id], [sales_order_item_id], [product_id], [batch_id], [required_qty], [picked_qty]
    )
    SELECT
        so.[id],
        soi.[id],
        soi.[product_id],
        soi.[batch_id],
        soi.[quantity],
        0
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[sales_order_items] AS soi ON soi.[order_id] = so.[id]
    WHERE so.[id] = @p_sales_order_id;

    MERGE INTO [dbo].[inventory_location_balances] AS ilb
    USING (
        SELECT
            ilb2.[id] AS [balance_id],
            pti.[required_qty]
        FROM [dbo].[picking_task_items] AS pti
        INNER JOIN [dbo].[inventory_location_balances] AS ilb2
            ON ilb2.[product_id] = pti.[product_id]
           AND ilb2.[batch_id] = pti.[batch_id]
    ) AS src
    ON ilb.[id] = src.[balance_id]
    WHEN MATCHED THEN UPDATE SET
        ilb.[locked_quantity] = ilb.[locked_quantity] + src.[required_qty];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_issue_repair_order_parts
CREATE OR ALTER PROCEDURE [dbo].[sp_issue_repair_order_parts]
    @p_repair_order_id BIGINT,
    @p_operator_id BIGINT
AS
BEGIN
    INSERT INTO [dbo].[inventory_transactions] (
        [product_id], [batch_id], [warehouse_id], [transaction_type], [quantity_change],
        [before_qty], [after_qty], [reference_type], [reference_id], [operator_id], [remark]
    )
    SELECT
        rop.[product_id],
        inv.[batch_id],
        inv.[warehouse_id],
        N'repair_issue',
        -rop.[quantity],
        inv.[quantity],
        inv.[quantity] - rop.[quantity],
        N'repair_order',
        rop.[repair_order_id],
        @p_operator_id,
        CONCAT(N'repair issue ', rop.[id])
    FROM [dbo].[repair_order_parts] AS rop
    INNER JOIN [dbo].[inventory] AS inv ON inv.[product_id] = rop.[product_id]
    WHERE rop.[repair_order_id] = @p_repair_order_id;

    MERGE INTO [dbo].[inventory] AS inv
    USING (
        SELECT
            inv2.[id] AS [inventory_id],
            rop.[quantity]
        FROM [dbo].[repair_order_parts] AS rop
        INNER JOIN [dbo].[inventory] AS inv2 ON inv2.[product_id] = rop.[product_id]
        WHERE rop.[repair_order_id] = @p_repair_order_id
    ) AS src
    ON inv.[id] = src.[inventory_id]
    WHEN MATCHED THEN UPDATE SET
        inv.[quantity] = inv.[quantity] - src.[quantity];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_post_sales_cashier_journals_business
CREATE OR ALTER PROCEDURE [dbo].[sp_post_sales_cashier_journals_business]
AS
BEGIN
    INSERT INTO [dbo].[cashier_journals] (
        [account_id], [cashier_id], [journal_type], [amount],
        [counterparty], [reference_type], [reference_id], [remark]
    )
    SELECT
        a.[id],
        e.[id],
        N'bank_in',
        so.[paid_amount],
        c.[name],
        N'sales_order',
        so.[id],
        so.[order_no]
    FROM [dbo].[sales_orders] AS so
    INNER JOIN [dbo].[customers] AS c ON c.[id] = so.[customer_id]
    INNER JOIN [dbo].[accounts] AS a ON a.[account_no] = N'1001'
    INNER JOIN [dbo].[employees] AS e ON e.[id] = so.[salesperson_id]
    WHERE so.[status] IN (N'paid', N'completed');
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_post_reconciliation_items_business
CREATE OR ALTER PROCEDURE [dbo].[sp_post_reconciliation_items_business]
AS
BEGIN
    INSERT INTO [dbo].[reconciliation_items] (
        [reconciliation_id], [journal_id], [transaction_date],
        [description], [debit_amount], [credit_amount], [is_matched]
    )
    SELECT
        r.[id],
        cj.[id],
        cj.[journal_date],
        CONCAT(cj.[journal_type], N' - ', ISNULL(cj.[counterparty], N''), N' ', ISNULL(cj.[remark], N'')),
        CASE WHEN cj.[journal_type] IN (N'bank_in', N'cash_in') THEN cj.[amount] ELSE 0 END,
        CASE WHEN cj.[journal_type] IN (N'bank_out', N'cash_out') THEN cj.[amount] ELSE 0 END,
        0
    FROM [dbo].[cashier_journals] AS cj
    INNER JOIN [dbo].[reconciliations] AS r ON r.[account_id] = cj.[account_id]
        AND cj.[journal_date] >= r.[period_start]
        AND cj.[journal_date] <= r.[period_end];
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_2
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_2]
AS
BEGIN
    INSERT INTO [dbo].[promotion_products] ([category_id])
    SELECT p.[id]
    FROM [dbo].[product_categories] AS p
    INNER JOIN [dbo].[promotion_products] AS c ON c.[category_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[product_categories] AS p2);

    UPDATE c
    SET [category_id] = p.[id]
    FROM [dbo].[promotion_products] AS c
    INNER JOIN [dbo].[product_categories] AS p ON c.[category_id] = p.[id];

    MERGE INTO [dbo].[promotion_products] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[product_categories] AS p
        INNER JOIN [dbo].[promotion_products] AS c2 ON c2.[category_id] = p.[id]
    ) AS src
    ON c.[category_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[category_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_3
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_3]
AS
BEGIN
    INSERT INTO [dbo].[promotion_usages] ([promotion_id])
    SELECT p.[id]
    FROM [dbo].[promotions] AS p
    INNER JOIN [dbo].[promotion_usages] AS c ON c.[promotion_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[promotions] AS p2);

    UPDATE c
    SET [promotion_id] = p.[id]
    FROM [dbo].[promotion_usages] AS c
    INNER JOIN [dbo].[promotions] AS p ON c.[promotion_id] = p.[id];

    MERGE INTO [dbo].[promotion_usages] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[promotions] AS p
        INNER JOIN [dbo].[promotion_usages] AS c2 ON c2.[promotion_id] = p.[id]
    ) AS src
    ON c.[promotion_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[promotion_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_4
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_4]
AS
BEGIN
    INSERT INTO [dbo].[promotion_usages] ([order_id])
    SELECT p.[id]
    FROM [dbo].[sales_orders] AS p
    INNER JOIN [dbo].[promotion_usages] AS c ON c.[order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[sales_orders] AS p2);

    UPDATE c
    SET [order_id] = p.[id]
    FROM [dbo].[promotion_usages] AS c
    INNER JOIN [dbo].[sales_orders] AS p ON c.[order_id] = p.[id];

    MERGE INTO [dbo].[promotion_usages] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[sales_orders] AS p
        INNER JOIN [dbo].[promotion_usages] AS c2 ON c2.[order_id] = p.[id]
    ) AS src
    ON c.[order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_5
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_5]
AS
BEGIN
    INSERT INTO [dbo].[promotion_usages] ([customer_id])
    SELECT p.[id]
    FROM [dbo].[customers] AS p
    INNER JOIN [dbo].[promotion_usages] AS c ON c.[customer_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[customers] AS p2);

    UPDATE c
    SET [customer_id] = p.[id]
    FROM [dbo].[promotion_usages] AS c
    INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id];

    MERGE INTO [dbo].[promotion_usages] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[customers] AS p
        INNER JOIN [dbo].[promotion_usages] AS c2 ON c2.[customer_id] = p.[id]
    ) AS src
    ON c.[customer_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[customer_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_6
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_6]
AS
BEGIN
    INSERT INTO [dbo].[invoices] ([supplier_id])
    SELECT p.[id]
    FROM [dbo].[suppliers] AS p
    INNER JOIN [dbo].[invoices] AS c ON c.[supplier_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[suppliers] AS p2);

    UPDATE c
    SET [supplier_id] = p.[id]
    FROM [dbo].[invoices] AS c
    INNER JOIN [dbo].[suppliers] AS p ON c.[supplier_id] = p.[id];

    MERGE INTO [dbo].[invoices] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[suppliers] AS p
        INNER JOIN [dbo].[invoices] AS c2 ON c2.[supplier_id] = p.[id]
    ) AS src
    ON c.[supplier_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[supplier_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_7
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_7]
AS
BEGIN
    INSERT INTO [dbo].[invoices] ([customer_id])
    SELECT p.[id]
    FROM [dbo].[customers] AS p
    INNER JOIN [dbo].[invoices] AS c ON c.[customer_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[customers] AS p2);

    UPDATE c
    SET [customer_id] = p.[id]
    FROM [dbo].[invoices] AS c
    INNER JOIN [dbo].[customers] AS p ON c.[customer_id] = p.[id];

    MERGE INTO [dbo].[invoices] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[customers] AS p
        INNER JOIN [dbo].[invoices] AS c2 ON c2.[customer_id] = p.[id]
    ) AS src
    ON c.[customer_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[customer_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_8
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_8]
AS
BEGIN
    INSERT INTO [dbo].[three_way_matching] ([invoice_id])
    SELECT p.[id]
    FROM [dbo].[invoices] AS p
    INNER JOIN [dbo].[three_way_matching] AS c ON c.[invoice_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[invoices] AS p2);

    UPDATE c
    SET [invoice_id] = p.[id]
    FROM [dbo].[three_way_matching] AS c
    INNER JOIN [dbo].[invoices] AS p ON c.[invoice_id] = p.[id];

    MERGE INTO [dbo].[three_way_matching] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[invoices] AS p
        INNER JOIN [dbo].[three_way_matching] AS c2 ON c2.[invoice_id] = p.[id]
    ) AS src
    ON c.[invoice_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[invoice_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_9
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_9]
AS
BEGIN
    INSERT INTO [dbo].[three_way_matching] ([purchase_order_id])
    SELECT p.[id]
    FROM [dbo].[purchase_orders] AS p
    INNER JOIN [dbo].[three_way_matching] AS c ON c.[purchase_order_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_orders] AS p2);

    UPDATE c
    SET [purchase_order_id] = p.[id]
    FROM [dbo].[three_way_matching] AS c
    INNER JOIN [dbo].[purchase_orders] AS p ON c.[purchase_order_id] = p.[id];

    MERGE INTO [dbo].[three_way_matching] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_orders] AS p
        INNER JOIN [dbo].[three_way_matching] AS c2 ON c2.[purchase_order_id] = p.[id]
    ) AS src
    ON c.[purchase_order_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchase_order_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end

-- relation-detector-fixture-source:sqlserver.sp_13_erp_deep_scenario_procedures_10
CREATE OR ALTER PROCEDURE [dbo].[sp_13_erp_deep_scenario_procedures_10]
AS
BEGIN
    INSERT INTO [dbo].[three_way_matching] ([purchase_receipt_id])
    SELECT p.[id]
    FROM [dbo].[purchase_receipts] AS p
    INNER JOIN [dbo].[three_way_matching] AS c ON c.[purchase_receipt_id] = p.[id]
    WHERE p.[id] IN (SELECT p2.[id] FROM [dbo].[purchase_receipts] AS p2);

    UPDATE c
    SET [purchase_receipt_id] = p.[id]
    FROM [dbo].[three_way_matching] AS c
    INNER JOIN [dbo].[purchase_receipts] AS p ON c.[purchase_receipt_id] = p.[id];

    MERGE INTO [dbo].[three_way_matching] AS c
    USING (
        SELECT p.[id] AS [id],
               CASE WHEN p.[id] IS NULL THEN p.[id] ELSE p.[id] END AS [mapped_id],
               COUNT(*) OVER (PARTITION BY p.[id]) AS [row_rank]
        FROM [dbo].[purchase_receipts] AS p
        INNER JOIN [dbo].[three_way_matching] AS c2 ON c2.[purchase_receipt_id] = p.[id]
    ) AS src
    ON c.[purchase_receipt_id] = src.[id]
    WHEN MATCHED THEN UPDATE SET c.[purchase_receipt_id] = ISNULL(src.[mapped_id], src.[id]);
END;
-- relation-detector-fixture-end
