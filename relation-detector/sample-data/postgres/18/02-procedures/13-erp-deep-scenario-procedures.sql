-- ============================================================
-- ERP deep scenario procedures - PostgreSQL 16/17/18 compatible
-- ============================================================

-- relation-detector-fixture-source: ROUTINE:public.sp_run_mrp_for_plan
CREATE OR REPLACE PROCEDURE sp_run_mrp_for_plan(
    IN p_plan_id BIGINT,
    IN p_created_by BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_run_id BIGINT;
BEGIN
    INSERT INTO mrp_runs (run_no, plan_id, run_date, demand_source, status, created_by)
    SELECT
        'MRP-' || REPLACE(pp.plan_month, '-', '') || '-' || pp.id || '-' || TO_CHAR(CURRENT_TIMESTAMP, 'HH24MISS'),
        pp.id,
        CURRENT_DATE,
        'forecast',
        'running',
        p_created_by
    FROM production_plans pp
    WHERE pp.id = p_plan_id
    RETURNING id INTO v_run_id;

    INSERT INTO mrp_run_items (
        run_id, parent_product_id, component_product_id, gross_requirement,
        on_hand_qty, reserved_qty, planned_receipt_qty, net_requirement,
        suggested_order_qty, suggested_supplier_id, suggested_due_date
    )
    SELECT
        v_run_id,
        pp.product_id,
        bom.child_product_id,
        ROUND(pp.planned_production_qty * bom.quantity * (1 + bom.scrap_rate), 4),
        COALESCE(inv.on_hand_qty, 0.0000),
        COALESCE(res.reserved_qty, 0.0000),
        COALESCE(po.open_receipt_qty, 0.0000),
        GREATEST(
            ROUND(pp.planned_production_qty * bom.quantity * (1 + bom.scrap_rate), 4)
            - COALESCE(inv.on_hand_qty, 0.0000)
            + COALESCE(res.reserved_qty, 0.0000)
            - COALESCE(po.open_receipt_qty, 0.0000),
            0.0000
        ),
        CEIL(GREATEST(
            ROUND(pp.planned_production_qty * bom.quantity * (1 + bom.scrap_rate), 4)
            - COALESCE(inv.on_hand_qty, 0.0000)
            + COALESCE(res.reserved_qty, 0.0000)
            - COALESCE(po.open_receipt_qty, 0.0000),
            0.0000
        )),
        pref.supplier_id,
        CURRENT_DATE + COALESCE(pref.lead_time_days, 7)
    FROM production_plans pp
    JOIN boms bom ON bom.parent_product_id = pp.product_id
        AND bom.status = 'active'
        AND bom.effective_date <= CURRENT_DATE
        AND (bom.expiry_date IS NULL OR bom.expiry_date >= CURRENT_DATE)
    LEFT JOIN (
        SELECT product_id, SUM(quantity - locked_quantity) AS on_hand_qty
        FROM inventory
        GROUP BY product_id
    ) inv ON inv.product_id = bom.child_product_id
    LEFT JOIN (
        SELECT product_id, SUM(reserved_quantity - released_quantity) AS reserved_qty
        FROM inventory_reservations
        WHERE status IN ('reserved', 'partially_released')
        GROUP BY product_id
    ) res ON res.product_id = bom.child_product_id
    LEFT JOIN (
        SELECT poi.product_id, SUM(poi.quantity - poi.received_qty) AS open_receipt_qty
        FROM purchase_order_items poi
        JOIN purchase_orders po ON po.id = poi.order_id
        WHERE po.status IN ('approved', 'ordered', 'partially_received')
        GROUP BY poi.product_id
    ) po ON po.product_id = bom.child_product_id
    LEFT JOIN (
        SELECT product_id, MIN(supplier_id) AS supplier_id, MIN(lead_time_days) AS lead_time_days
        FROM supplier_products
        WHERE is_preferred = TRUE
        GROUP BY product_id
    ) pref ON pref.product_id = bom.child_product_id
    WHERE pp.id = p_plan_id;

    UPDATE mrp_runs
    SET status = 'completed',
        completed_at = CURRENT_TIMESTAMP
    WHERE id = v_run_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_refresh_semantic_dimensions
CREATE OR REPLACE PROCEDURE sp_refresh_semantic_dimensions()
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO region_dim (id, region_code, region_name, province, city, district, sales_region, region_level, is_active)
    VALUES
    (1, 'EAST-SH', '上海销售区', '上海市', '上海市', NULL, '华东', 'city', TRUE),
    (2, 'EAST-HZ', '杭州销售区', '浙江省', '杭州市', NULL, '华东', 'city', TRUE),
    (3, 'EAST-SZ', '苏州销售区', '江苏省', '苏州市', NULL, '华东', 'city', TRUE),
    (4, 'SOUTH-GZ', '广州销售区', '广东省', '广州市', NULL, '华南', 'city', TRUE),
    (5, 'NORTH-BJ', '北京销售区', '北京市', '北京市', NULL, '华北', 'city', TRUE)
    ON CONFLICT (id) DO UPDATE
    SET region_name = EXCLUDED.region_name,
        sales_region = EXCLUDED.sales_region,
        is_active = EXCLUDED.is_active;

    INSERT INTO fiscal_calendar (
        calendar_date, fiscal_year, fiscal_quarter, fiscal_month, fiscal_month_name,
        period_code, period_start, period_end, is_current_fiscal_year, accounting_period_id
    )
    SELECT DISTINCT
        d.calendar_date,
        EXTRACT(YEAR FROM d.calendar_date)::INTEGER,
        EXTRACT(QUARTER FROM d.calendar_date)::SMALLINT,
        EXTRACT(MONTH FROM d.calendar_date)::SMALLINT,
        to_char(d.calendar_date, 'YYYY-MM'),
        to_char(d.calendar_date, 'YYYY-MM'),
        date_trunc('month', d.calendar_date)::DATE,
        (date_trunc('month', d.calendar_date) + INTERVAL '1 month - 1 day')::DATE,
        EXTRACT(YEAR FROM d.calendar_date)::INTEGER = 2026,
        ap.id
    FROM (
        SELECT order_date AS calendar_date FROM sales_orders
        UNION SELECT receipt_date FROM payment_receipts
        UNION SELECT return_date FROM sales_returns
        UNION SELECT DATE '2026-01-01'
        UNION SELECT DATE '2026-02-01'
        UNION SELECT DATE '2026-03-01'
        UNION SELECT DATE '2026-04-01'
        UNION SELECT DATE '2026-05-01'
        UNION SELECT DATE '2026-06-01'
    ) d
    LEFT JOIN accounting_periods ap ON ap.period_code = to_char(d.calendar_date, 'YYYY-MM')
    ON CONFLICT (calendar_date) DO UPDATE
    SET fiscal_year = EXCLUDED.fiscal_year,
        fiscal_quarter = EXCLUDED.fiscal_quarter,
        fiscal_month = EXCLUDED.fiscal_month,
        is_current_fiscal_year = EXCLUDED.is_current_fiscal_year,
        accounting_period_id = EXCLUDED.accounting_period_id;

    INSERT INTO category_dim (
        source_category_id, category_code, level1_name, level2_name, leaf_name,
        is_womenwear, effective_from, status
    )
    SELECT
        pc.id,
        pc.code,
        COALESCE(grand.name, parent.name, pc.name),
        CASE WHEN grand.id IS NOT NULL THEN parent.name ELSE NULL END,
        pc.name,
        pc.name = '女装' OR parent.name = '女装' OR grand.name = '女装',
        DATE '2026-01-01',
        'active'
    FROM product_categories pc
    LEFT JOIN product_categories parent ON parent.id = pc.parent_id
    LEFT JOIN product_categories grand ON grand.id = parent.parent_id
    ON CONFLICT (source_category_id) DO UPDATE
    SET category_code = EXCLUDED.category_code,
        level1_name = EXCLUDED.level1_name,
        level2_name = EXCLUDED.level2_name,
        leaf_name = EXCLUDED.leaf_name,
        is_womenwear = EXCLUDED.is_womenwear,
        status = EXCLUDED.status;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_record_customer_payment
CREATE OR REPLACE PROCEDURE sp_record_customer_payment(
    IN p_order_id BIGINT,
    IN p_receipt_no VARCHAR,
    IN p_payment_date DATE,
    IN p_amount DECIMAL,
    IN p_account_id BIGINT,
    IN p_cashier_id BIGINT,
    IN p_method VARCHAR,
    IN p_status VARCHAR
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_customer_id BIGINT;
    v_receipt_id BIGINT;
    v_journal_id BIGINT;
BEGIN
    SELECT customer_id INTO v_customer_id
    FROM sales_orders
    WHERE id = p_order_id;

    INSERT INTO payment_receipts (
        receipt_no, receipt_type, party_type, party_id, account_id,
        receipt_date, amount, currency, status, handled_by, confirmed_at, remark
    )
    VALUES (
        p_receipt_no, 'customer_receipt', 'customer', v_customer_id, p_account_id,
        p_payment_date, p_amount, 'CNY',
        CASE WHEN p_status = 'paid' THEN 'confirmed' ELSE 'draft' END,
        p_cashier_id,
        CASE WHEN p_status = 'paid' THEN CURRENT_TIMESTAMP ELSE NULL END,
        '语义层样例客户收款'
    )
    RETURNING id INTO v_receipt_id;

    INSERT INTO payment_receipt_allocations (receipt_id, reference_type, reference_id, allocated_amount)
    VALUES (v_receipt_id, 'sales_order', p_order_id, p_amount);

    INSERT INTO cashier_journals (
        journal_no, journal_date, account_id, cashier_id, journal_type,
        amount, counterparty, reference_type, reference_id, voucher_id,
        bank_account, status, remark
    )
    SELECT
        'CJ-' || p_receipt_no,
        p_payment_date,
        p_account_id,
        p_cashier_id,
        'receipt',
        p_amount,
        c.name,
        'sales_order',
        so.id,
        NULL,
        NULL,
        CASE WHEN p_status = 'paid' THEN 'confirmed' ELSE 'draft' END,
        '语义层样例收款日记账'
    FROM sales_orders so
    JOIN customers c ON c.id = so.customer_id
    WHERE so.id = p_order_id
    RETURNING id INTO v_journal_id;

    INSERT INTO payments (
        payment_no, customer_id, order_id, receipt_id, journal_id, payment_date,
        amount, currency, payment_method, payment_status, failure_reason
    )
    VALUES (
        'PAY-' || p_receipt_no, v_customer_id, p_order_id, v_receipt_id, v_journal_id,
        p_payment_date, p_amount, 'CNY', p_method, p_status,
        CASE WHEN p_status = 'failed' THEN '银行返回失败' ELSE NULL END
    );

    UPDATE sales_orders
    SET paid_amount = paid_amount + CASE WHEN p_status = 'paid' THEN p_amount ELSE 0.00 END
    WHERE id = p_order_id;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_rebuild_sales_fact
CREATE OR REPLACE PROCEDURE sp_rebuild_sales_fact()
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO sales_fact (
        order_id, order_item_id, customer_id, product_id, category_dim_id,
        warehouse_id, region_dim_id, fiscal_date, payment_id, quantity_sold,
        sales_amount, paid_amount, refund_amount, net_sales_amount,
        gross_margin_amount, order_status, sales_channel
    )
    SELECT
        so.id,
        soi.id,
        so.customer_id,
        soi.product_id,
        cd.id,
        so.warehouse_id,
        rd.id,
        so.order_date,
        pay.id,
        soi.quantity,
        soi.amount,
        COALESCE(pay.amount, so.paid_amount, 0.00),
        COALESCE(refunds.refund_amount, 0.00),
        soi.amount - COALESCE(refunds.refund_amount, 0.00),
        soi.amount - (soi.quantity * COALESCE(p.purchase_price, 0.00)),
        so.status,
        CASE WHEN c.type = 'company' THEN 'b2b' ELSE 'retail' END
    FROM sales_order_items soi
    JOIN sales_orders so ON so.id = soi.order_id
    JOIN customers c ON c.id = so.customer_id
    JOIN products p ON p.id = soi.product_id
    JOIN category_dim cd ON cd.source_category_id = p.category_id
    JOIN warehouses w ON w.id = so.warehouse_id
    JOIN region_dim rd ON rd.province = w.province AND (rd.city = w.city OR rd.city IS NULL)
    JOIN fiscal_calendar fc ON fc.calendar_date = so.order_date
    LEFT JOIN payments pay ON pay.order_id = so.id AND pay.payment_status IN ('paid','refunded')
    LEFT JOIN (
        SELECT sri.order_item_id, SUM(sr.refund_amount) AS refund_amount
        FROM sales_return_items sri
        JOIN sales_returns sr ON sr.id = sri.return_id
        WHERE sr.status IN ('refunded','closed','received','inspected')
        GROUP BY sri.order_item_id
    ) refunds ON refunds.order_item_id = soi.id
    ON CONFLICT (order_item_id) DO UPDATE
    SET paid_amount = EXCLUDED.paid_amount,
        refund_amount = EXCLUDED.refund_amount,
        net_sales_amount = EXCLUDED.net_sales_amount,
        gross_margin_amount = EXCLUDED.gross_margin_amount,
        order_status = EXCLUDED.order_status;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_onboard_employee_full
CREATE OR REPLACE PROCEDURE sp_onboard_employee_full(
    IN p_employee_no VARCHAR,
    IN p_name VARCHAR,
    IN p_department_id BIGINT,
    IN p_position_id BIGINT,
    IN p_role_id BIGINT,
    IN p_manager_id BIGINT,
    IN p_operator_id BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_employee_id BIGINT;
BEGIN
    INSERT INTO employees (
        employee_no, name, gender, id_card, phone, email, birth_date, hire_date,
        department_id, position_id, manager_id, salary, social_security_base,
        housing_fund_base, bank_name, bank_account, status, address,
        emergency_contact, emergency_phone
    )
    SELECT
        p_employee_no,
        p_name,
        'F',
        '31010119900101' || lpad(p_position_id::TEXT, 4, '0'),
        '139' || lpad(p_position_id::TEXT, 8, '0'),
        lower(p_employee_no) || '@erp.example.com',
        DATE '1990-01-01',
        CURRENT_DATE,
        d.id,
        pos.id,
        p_manager_id,
        (pos.min_salary + pos.max_salary) / 2,
        (pos.min_salary + pos.max_salary) / 2,
        (pos.min_salary + pos.max_salary) / 2,
        '工商银行',
        '622202' || lpad(p_position_id::TEXT, 12, '0'),
        'probation',
        '上海市浦东新区',
        '紧急联系人',
        '13900000000'
    FROM departments d
    JOIN positions pos ON pos.id = p_position_id
    WHERE d.id = p_department_id
    RETURNING id INTO v_employee_id;

    INSERT INTO employee_roles (employee_id, role_id)
    VALUES (v_employee_id, p_role_id);

    INSERT INTO employee_shift_assignments (employee_id, shift_id, work_date, status)
    SELECT v_employee_id, es.id, CURRENT_DATE, 'planned'
    FROM employee_shifts es
    WHERE es.department_id = p_department_id
    LIMIT 1;

    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (p_operator_id, 'onboard_employee_full', 'employee', v_employee_id,
            jsonb_build_object('employee_no', p_employee_no, 'department_id', p_department_id));
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_calculate_work_order_actual_cost
CREATE OR REPLACE PROCEDURE sp_calculate_work_order_actual_cost(
    IN p_work_order_id BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO work_order_costs (
        work_order_id, material_cost, labor_cost, overhead_cost,
        finished_qty, unit_cost, variance_amount, calculated_at
    )
    SELECT
        wo.id,
        COALESCE(mat.material_cost, 0.00),
        COALESCE(lab.labor_cost, 0.00),
        COALESCE(lab.labor_cost, 0.00) * 0.60,
        COALESCE(fg.finished_qty, wo.completed_quantity, 0),
        ROUND(
            (COALESCE(mat.material_cost, 0.00)
             + COALESCE(lab.labor_cost, 0.00)
             + COALESCE(lab.labor_cost, 0.00) * 0.60)
            / NULLIF(COALESCE(fg.finished_qty, wo.completed_quantity, 0), 0),
            4
        ),
        ROUND(
            (COALESCE(mat.material_cost, 0.00)
             + COALESCE(lab.labor_cost, 0.00)
             + COALESCE(lab.labor_cost, 0.00) * 0.60)
            - COALESCE(std.standard_amount, 0.00),
            2
        ),
        CURRENT_TIMESTAMP
    FROM work_orders wo
    LEFT JOIN (
        SELECT mi.work_order_id, SUM(mii.issued_qty * mii.unit_cost) AS material_cost
        FROM material_issues mi
        JOIN material_issue_items mii ON mii.issue_id = mi.id
        WHERE mi.status = 'posted'
        GROUP BY mi.work_order_id
    ) mat ON mat.work_order_id = wo.id
    LEFT JOIN (
        SELECT woo.work_order_id, SUM(opr.labor_minutes * 1.20) AS labor_cost
        FROM work_order_operations woo
        JOIN operation_reports opr ON opr.work_order_operation_id = woo.id
        GROUP BY woo.work_order_id
    ) lab ON lab.work_order_id = wo.id
    LEFT JOIN (
        SELECT work_order_id, SUM(received_qty) AS finished_qty
        FROM finished_goods_receipts
        WHERE status = 'posted'
        GROUP BY work_order_id
    ) fg ON fg.work_order_id = wo.id
    LEFT JOIN (
        SELECT wo2.id AS work_order_id,
               wo2.planned_quantity * (sc.material_cost + sc.labor_cost + sc.overhead_cost) AS standard_amount
        FROM work_orders wo2
        JOIN standard_costs sc ON sc.product_id = wo2.product_id
            AND sc.status = 'active'
            AND sc.effective_from <= CURRENT_DATE
            AND (sc.effective_to IS NULL OR sc.effective_to >= CURRENT_DATE)
    ) std ON std.work_order_id = wo.id
    WHERE wo.id = p_work_order_id
    ON CONFLICT (work_order_id) DO UPDATE SET
        material_cost = EXCLUDED.material_cost,
        labor_cost = EXCLUDED.labor_cost,
        overhead_cost = EXCLUDED.overhead_cost,
        finished_qty = EXCLUDED.finished_qty,
        unit_cost = EXCLUDED.unit_cost,
        variance_amount = EXCLUDED.variance_amount,
        calculated_at = EXCLUDED.calculated_at;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_post_finished_goods_receipt
CREATE OR REPLACE PROCEDURE sp_post_finished_goods_receipt(
    IN p_receipt_id BIGINT,
    IN p_operator_id BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO inventory_cost_layers (
        product_id, batch_id, warehouse_id, source_type, source_id, receipt_date,
        original_qty, remaining_qty, unit_cost, currency
    )
    SELECT
        fgr.product_id,
        fgr.batch_id,
        fgr.warehouse_id,
        'work_order_receipt',
        fgr.id,
        fgr.receipt_date,
        fgr.received_qty,
        fgr.received_qty,
        COALESCE(woc.unit_cost, fgr.unit_cost),
        'CNY'
    FROM finished_goods_receipts fgr
    LEFT JOIN work_order_costs woc ON woc.work_order_id = fgr.work_order_id
    WHERE fgr.id = p_receipt_id
      AND fgr.status = 'posted';

    INSERT INTO inventory_transactions (
        product_id, batch_id, warehouse_id, transaction_type, quantity_change,
        before_qty, after_qty, reference_type, reference_id, operator_id, remark
    )
    SELECT
        fgr.product_id,
        fgr.batch_id,
        fgr.warehouse_id,
        'production_in',
        fgr.received_qty,
        COALESCE(inv.quantity, 0),
        COALESCE(inv.quantity, 0) + fgr.received_qty,
        'finished_goods_receipt',
        fgr.id,
        p_operator_id,
        '完工入库: ' || fgr.receipt_no
    FROM finished_goods_receipts fgr
    LEFT JOIN inventory inv ON inv.product_id = fgr.product_id
        AND inv.warehouse_id = fgr.warehouse_id
        AND inv.batch_id IS NOT DISTINCT FROM fgr.batch_id
    WHERE fgr.id = p_receipt_id
      AND fgr.status = 'posted';

    INSERT INTO inventory (
        product_id, batch_id, warehouse_id, shelf_location, quantity,
        locked_quantity, last_stocktake_date
    )
    SELECT
        fgr.product_id,
        fgr.batch_id,
        fgr.warehouse_id,
        'FG-AUTO',
        fgr.received_qty,
        0,
        fgr.receipt_date
    FROM finished_goods_receipts fgr
    WHERE fgr.id = p_receipt_id
      AND fgr.status = 'posted'
    ON CONFLICT (product_id, batch_id, warehouse_id) DO UPDATE SET
        quantity = inventory.quantity + EXCLUDED.quantity,
        last_stocktake_date = EXCLUDED.last_stocktake_date;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_create_ar_invoice_from_sales_order
CREATE OR REPLACE PROCEDURE sp_create_ar_invoice_from_sales_order(
    IN p_sales_order_id BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO ar_invoices (
        ar_no, sales_order_id, customer_id, invoice_date, due_date,
        invoice_amount, paid_amount, writeoff_amount, status
    )
    SELECT
        'AR-' || so.order_no,
        so.id,
        so.customer_id,
        so.order_date,
        so.order_date + c.credit_days,
        so.total_amount,
        so.paid_amount,
        0.00,
        CASE
            WHEN so.paid_amount >= so.total_amount THEN 'paid'
            WHEN so.paid_amount > 0 THEN 'partially_paid'
            ELSE 'open'
        END
    FROM sales_orders so
    JOIN customers c ON c.id = so.customer_id
    WHERE so.id = p_sales_order_id
    ON CONFLICT (ar_no) DO UPDATE SET
        invoice_amount = EXCLUDED.invoice_amount,
        paid_amount = EXCLUDED.paid_amount,
        status = EXCLUDED.status;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_create_ap_invoice_from_purchase_order
CREATE OR REPLACE PROCEDURE sp_create_ap_invoice_from_purchase_order(
    IN p_purchase_order_id BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO ap_invoices (
        ap_no, purchase_order_id, supplier_id, invoice_date, due_date,
        invoice_amount, paid_amount, status
    )
    SELECT
        'AP-' || po.order_no,
        po.id,
        po.supplier_id,
        COALESCE(po.actual_delivery_date, po.order_date),
        COALESCE(po.actual_delivery_date, po.order_date) + INTERVAL '30 days',
        po.total_amount,
        po.paid_amount,
        CASE
            WHEN po.paid_amount >= po.total_amount THEN 'paid'
            WHEN po.paid_amount > 0 THEN 'partially_paid'
            ELSE 'open'
        END
    FROM purchase_orders po
    WHERE po.id = p_purchase_order_id
    ON CONFLICT (ap_no) DO UPDATE SET
        invoice_amount = EXCLUDED.invoice_amount,
        paid_amount = EXCLUDED.paid_amount,
        status = EXCLUDED.status;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_post_cogs_for_sales_order
CREATE OR REPLACE PROCEDURE sp_post_cogs_for_sales_order(
    IN p_sales_order_id BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO cogs_entries (
        sales_order_id, sales_order_item_id, product_id, batch_id,
        quantity, unit_cost, cogs_amount, voucher_id, posted_at
    )
    SELECT
        so.id,
        soi.id,
        soi.product_id,
        soi.batch_id,
        soi.quantity,
        COALESCE(cost.unit_cost, p.purchase_price, 0.00),
        ROUND(soi.quantity * COALESCE(cost.unit_cost, p.purchase_price, 0.00), 2),
        NULL,
        CURRENT_TIMESTAMP
    FROM sales_orders so
    JOIN sales_order_items soi ON soi.order_id = so.id
    JOIN products p ON p.id = soi.product_id
    LEFT JOIN (
        SELECT product_id, batch_id, MIN(unit_cost) AS unit_cost
        FROM inventory_cost_layers
        WHERE remaining_qty > 0
        GROUP BY product_id, batch_id
    ) cost ON cost.product_id = soi.product_id
        AND cost.batch_id IS NOT DISTINCT FROM soi.batch_id
    WHERE so.id = p_sales_order_id
      AND NOT EXISTS (
          SELECT 1
          FROM cogs_entries ce
          WHERE ce.sales_order_item_id = soi.id
      );
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_generate_picking_task_for_order
CREATE OR REPLACE PROCEDURE sp_generate_picking_task_for_order(
    IN p_sales_order_id BIGINT,
    IN p_assigned_to BIGINT
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_task_id BIGINT;
BEGIN
    INSERT INTO picking_tasks (task_no, sales_order_id, warehouse_id, wave_no, assigned_to, status)
    SELECT
        'PICK-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD') || '-' || so.id,
        so.id,
        so.warehouse_id,
        'WAVE-' || TO_CHAR(CURRENT_DATE, 'YYYYMMDD'),
        p_assigned_to,
        'allocated'
    FROM sales_orders so
    WHERE so.id = p_sales_order_id
      AND NOT EXISTS (
          SELECT 1
          FROM picking_tasks pt
          WHERE pt.sales_order_id = so.id
            AND pt.status IN ('pending', 'allocated', 'picked')
      )
    RETURNING id INTO v_task_id;

    IF v_task_id IS NOT NULL THEN
        INSERT INTO picking_task_items (
            picking_task_id, sales_order_item_id, product_id, batch_id,
            location_id, required_qty, picked_qty
        )
        SELECT
            v_task_id,
            soi.id,
            soi.product_id,
            soi.batch_id,
            loc.location_id,
            soi.quantity - soi.returned_qty,
            0.0000
        FROM sales_order_items soi
        JOIN sales_orders so ON so.id = soi.order_id
        JOIN (
            SELECT ilb.product_id, ilb.batch_id, MIN(ilb.location_id) AS location_id
            FROM inventory_location_balances ilb
            JOIN warehouse_locations wl ON wl.id = ilb.location_id
            JOIN warehouse_zones wz ON wz.id = wl.zone_id
            WHERE ilb.quantity - ilb.locked_quantity > 0
              AND wz.zone_type IN ('picking', 'storage')
            GROUP BY ilb.product_id, ilb.batch_id
        ) loc ON loc.product_id = soi.product_id
            AND loc.batch_id IS NOT DISTINCT FROM soi.batch_id
        WHERE so.id = p_sales_order_id;

        UPDATE inventory_location_balances ilb
        SET locked_quantity = ilb.locked_quantity + pti.required_qty
        FROM picking_task_items pti
        JOIN picking_tasks pt ON pt.id = pti.picking_task_id
        WHERE pti.location_id = ilb.location_id
          AND pti.product_id = ilb.product_id
          AND pti.batch_id IS NOT DISTINCT FROM ilb.batch_id
          AND pt.id = v_task_id;
    END IF;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_refresh_budget_usage
CREATE OR REPLACE PROCEDURE sp_refresh_budget_usage(
    IN p_period_code VARCHAR
)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE budget_items bi
    SET used_amount = COALESCE(usage_by_subject.used_amount, 0.00)
    FROM account_subjects subj
    LEFT JOIN (
        SELECT
            a.code AS subject_code,
            TO_CHAR(v.voucher_date, 'YYYY-MM') AS period_code,
            SUM(CASE WHEN vi.direction = 'debit' THEN vi.amount ELSE 0.00 END) AS used_amount
        FROM vouchers v
        JOIN voucher_items vi ON vi.voucher_id = v.id
        JOIN accounts a ON a.id = vi.account_id
        WHERE v.status = 'posted'
        GROUP BY a.code, TO_CHAR(v.voucher_date, 'YYYY-MM')
    ) usage_by_subject ON usage_by_subject.subject_code = subj.subject_code
        AND usage_by_subject.period_code = bi.period_code
    WHERE subj.id = bi.subject_id
      AND bi.period_code = p_period_code;
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_apply_customer_master_data_change
CREATE OR REPLACE PROCEDURE sp_apply_customer_master_data_change(
    IN p_request_id BIGINT,
    IN p_applied_by BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE customers c
    SET address = CASE WHEN i.field_name IN ('registered_address', 'address') THEN i.new_value ELSE c.address END,
        contact_person = CASE WHEN i.field_name = 'contact_person' THEN i.new_value ELSE c.contact_person END,
        phone = CASE WHEN i.field_name = 'phone' THEN i.new_value ELSE c.phone END,
        email = CASE WHEN i.field_name = 'email' THEN i.new_value ELSE c.email END
    FROM master_data_change_requests r
    JOIN master_data_change_items i ON i.request_id = r.id
    WHERE r.master_type = 'customer'
      AND r.master_id = c.id
      AND r.id = p_request_id
      AND r.status = 'approved';

    UPDATE master_data_change_requests
    SET status = 'applied',
        approved_by = COALESCE(approved_by, p_applied_by),
        approved_at = COALESCE(approved_at, CURRENT_TIMESTAMP)
    WHERE id = p_request_id
      AND master_type = 'customer'
      AND status = 'approved';

    INSERT INTO audit_log (employee_id, action, target_type, target_id, new_value)
    VALUES (p_applied_by, 'apply_customer_master_data_change', 'master_data_change', p_request_id,
            jsonb_build_object('status', 'applied'));
END;
$$;
-- relation-detector-fixture-end

-- relation-detector-fixture-source: ROUTINE:public.sp_issue_repair_order_parts
CREATE OR REPLACE PROCEDURE sp_issue_repair_order_parts(
    IN p_repair_order_id BIGINT,
    IN p_operator_id BIGINT
)
LANGUAGE plpgsql
AS $$
BEGIN
    INSERT INTO inventory_transactions (
        product_id, batch_id, warehouse_id, transaction_type, quantity_change,
        before_qty, after_qty, reference_type, reference_id, operator_id, remark
    )
    SELECT
        rop.product_id,
        rop.batch_id,
        rop.issued_from_warehouse_id,
        'damage_out',
        (-rop.quantity)::INTEGER,
        COALESCE(inv.quantity, 0),
        COALESCE(inv.quantity, 0) - rop.quantity,
        'repair_order',
        rop.repair_order_id,
        p_operator_id,
        '维修备件出库: ' || ro.repair_no
    FROM repair_order_parts rop
    JOIN repair_orders ro ON ro.id = rop.repair_order_id
    LEFT JOIN inventory inv ON inv.product_id = rop.product_id
        AND inv.warehouse_id = rop.issued_from_warehouse_id
        AND inv.batch_id IS NOT DISTINCT FROM rop.batch_id
    WHERE rop.repair_order_id = p_repair_order_id;

    UPDATE inventory inv
    SET quantity = inv.quantity - rop.quantity
    FROM repair_order_parts rop
    WHERE rop.product_id = inv.product_id
      AND rop.issued_from_warehouse_id = inv.warehouse_id
      AND rop.batch_id IS NOT DISTINCT FROM inv.batch_id
      AND rop.repair_order_id = p_repair_order_id;

    UPDATE repair_orders ro
    SET actual_cost = cost.parts_cost,
        status = 'repairing'
    FROM (
        SELECT repair_order_id, SUM(quantity * unit_cost) AS parts_cost
        FROM repair_order_parts
        GROUP BY repair_order_id
    ) cost
    WHERE cost.repair_order_id = ro.id
      AND ro.id = p_repair_order_id;
END;
$$;
-- relation-detector-fixture-end
