-- ============================================================
-- ERP深业务场景验证数据
-- 目标: 每个新增业务域都有可 join、可聚合、可产生 lineage 的代表性数据
-- 数据库: MySQL 8.0
-- ============================================================


-- ============================================================
-- 1. 生产计划、MRP、工序执行和完工入库
-- ============================================================

INSERT INTO production_plans (id, plan_no, product_id, warehouse_id, plan_month,
    forecast_qty, confirmed_sales_qty, safety_stock_qty, planned_production_qty,
    status, planner_id, approved_by, approved_at)
VALUES
(9001, 'PP-202602-9001', 1, 1, '2026-02', 180, 92, 30, 160, 'released', 27, 26, '2026-02-01 09:30:00'),
(9002, 'PP-202602-9002', 2, 2, '2026-02', 120, 48, 20, 100, 'approved', 27, 26, '2026-02-01 10:00:00');

INSERT INTO mrp_runs (id, run_no, plan_id, run_date, demand_source, status, created_by, completed_at) VALUES
(9001, 'MRP-202602-9001', 9001, '2026-02-01', 'forecast', 'completed', 27, '2026-02-01 11:00:00'),
(9002, 'MRP-202602-9002', 9002, '2026-02-02', 'sales_order', 'completed', 27, '2026-02-02 11:20:00');

INSERT INTO mrp_run_items (run_id, parent_product_id, component_product_id,
    gross_requirement, on_hand_qty, reserved_qty, planned_receipt_qty,
    net_requirement, suggested_order_qty, suggested_supplier_id, suggested_due_date)
VALUES
(9001, 1, 2, 320.0000, 95.0000, 10.0000, 60.0000, 175.0000, 180.0000, 1, '2026-02-10'),
(9001, 1, 3, 160.0000, 42.0000, 8.0000, 20.0000, 106.0000, 110.0000, 2, '2026-02-11'),
(9002, 2, 4, 100.0000, 35.0000, 5.0000, 10.0000, 60.0000, 60.0000, 1, '2026-02-12');

INSERT INTO work_order_operations (id, work_order_id, operation_id, operation_seq,
    planned_start, planned_end, actual_start, actual_end, status, assigned_employee_id,
    qualified_qty, scrapped_qty, rework_qty)
VALUES
(9001, 1, 1, 10, '2026-02-03 08:30:00', '2026-02-03 09:00:00',
 '2026-02-03 08:35:00', '2026-02-03 09:02:00', 'completed', 45, 100, 0, 0),
(9002, 1, 2, 20, '2026-02-03 09:10:00', '2026-02-03 12:00:00',
 '2026-02-03 09:12:00', '2026-02-03 11:50:00', 'completed', 46, 98, 2, 4),
(9003, 1, 3, 30, '2026-02-03 13:00:00', '2026-02-03 14:00:00',
 '2026-02-03 13:05:00', '2026-02-03 13:55:00', 'completed', 51, 96, 2, 2);

INSERT INTO operation_reports (id, work_order_operation_id, report_no, employee_id,
    report_time, input_qty, qualified_qty, scrapped_qty, rework_qty, labor_minutes, machine_minutes)
VALUES
(9001, 9001, 'OPR-202602-9001', 45, '2026-02-03 09:05:00', 100, 100, 0, 0, 45.00, 30.00),
(9002, 9002, 'OPR-202602-9002', 46, '2026-02-03 11:55:00', 100, 98, 2, 4, 160.00, 155.00),
(9003, 9003, 'OPR-202602-9003', 51, '2026-02-03 14:00:00', 98, 96, 2, 2, 75.00, 50.00);

INSERT INTO material_issues (id, issue_no, work_order_id, warehouse_id, issue_date, issued_by, status) VALUES
(9001, 'MI-202602-9001', 1, 1, '2026-02-03', 45, 'posted'),
(9002, 'MI-202602-9002', 1, 1, '2026-02-04', 45, 'posted');

INSERT INTO material_issue_items (issue_id, product_id, batch_id, required_qty, issued_qty, unit_cost) VALUES
(9001, 2, 2, 200.0000, 198.0000, 36.5000),
(9001, 3, 3, 100.0000, 96.0000, 18.2000),
(9002, 4, NULL, 50.0000, 48.0000, 9.8000);

INSERT INTO finished_goods_receipts (id, receipt_no, work_order_id, product_id, batch_id,
    warehouse_id, receipt_date, received_qty, unit_cost, received_by, status)
VALUES
(9001, 'FGR-202602-9001', 1, 1, 1, 1, '2026-02-04', 96, 126.8500, 45, 'posted');

-- ============================================================
-- 2. 成本核算、库存估值和销售成本
-- ============================================================

INSERT INTO standard_costs (id, product_id, cost_version, material_cost, labor_cost,
    overhead_cost, effective_from, effective_to, status, approved_by)
VALUES
(9001, 1, 'STD-2026', 86.0000, 18.0000, 12.0000, '2026-01-01', NULL, 'active', 34),
(9002, 2, 'STD-2026', 28.0000, 6.0000, 4.0000, '2026-01-01', NULL, 'active', 34),
(9003, 3, 'STD-2026', 15.0000, 3.0000, 2.0000, '2026-01-01', NULL, 'active', 34);

INSERT INTO inventory_cost_layers (id, product_id, batch_id, warehouse_id, source_type,
    source_id, receipt_date, original_qty, remaining_qty, unit_cost, currency)
VALUES
(9001, 1, 1, 1, 'work_order_receipt', 9001, '2026-02-04', 96.0000, 82.0000, 126.8500, 'CNY'),
(9002, 2, 2, 1, 'purchase_receipt', 1, '2026-01-10', 240.0000, 140.0000, 36.5000, 'CNY'),
(9003, 3, 3, 1, 'purchase_receipt', 1, '2026-01-10', 180.0000, 84.0000, 18.2000, 'CNY');

INSERT INTO inventory_valuation_snapshots (snapshot_date, product_id, warehouse_id,
    quantity, unit_cost, inventory_value, valuation_method)
VALUES
('2026-02-28', 1, 1, 82.0000, 126.8500, 10401.70, 'moving_average'),
('2026-02-28', 2, 1, 140.0000, 36.5000, 5110.00, 'moving_average'),
('2026-02-28', 3, 1, 84.0000, 18.2000, 1528.80, 'moving_average');

INSERT INTO work_order_costs (id, work_order_id, material_cost, labor_cost, overhead_cost,
    finished_qty, unit_cost, variance_amount, calculated_at)
VALUES
(9001, 1, 9434.20, 1720.00, 1024.00, 96, 126.8500, 1042.20, '2026-02-04 18:00:00');

INSERT INTO vouchers (id, voucher_no, voucher_date, voucher_type, reference_type, reference_id,
    total_debit, total_credit, prepared_by, reviewed_by, posted_by, status, summary)
VALUES
(9101, 'V-COGS-202602-9001', '2026-02-12', 'journal', 'sales_order', 9001,
 2537.00, 2537.00, 36, 35, 34, 'posted', '结转样例销售订单成本'),
(9102, 'V-WOC-202602-9001', '2026-02-04', 'journal', 'work_order', 1,
 12178.20, 12178.20, 36, 35, 34, 'posted', '结转工单完工成本');

INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary) VALUES
(9101, 31, 1, 'debit', 2537.00, '借: 主营业务成本'),
(9101, 12, 2, 'credit', 2537.00, '贷: 库存商品'),
(9102, 12, 1, 'debit', 12178.20, '借: 库存商品'),
(9102, 41, 2, 'credit', 12178.20, '贷: 生产成本');

INSERT INTO cogs_entries (id, sales_order_id, sales_order_item_id, product_id, batch_id,
    quantity, unit_cost, cogs_amount, voucher_id, posted_at)
VALUES
(9001, 9001, 9001, 1, 1, 20.0000, 126.8500, 2537.00, 9101, '2026-02-12 18:30:00');

-- ============================================================
-- 3. 总账、预算、应收应付和付款申请
-- ============================================================

INSERT INTO account_subjects (id, parent_id, subject_code, subject_name, subject_type, balance_direction, is_leaf, status) VALUES
(9001, NULL, '1002', '银行存款', 'asset', 'debit', FALSE, 'active'),
(9002, NULL, '1122', '应收账款', 'asset', 'debit', TRUE, 'active'),
(9003, NULL, '2202', '应付账款', 'liability', 'credit', TRUE, 'active'),
(9004, NULL, '5001', '生产成本', 'cost', 'debit', TRUE, 'active'),
(9005, NULL, '6401', '主营业务成本', 'expense', 'debit', TRUE, 'active'),
(9006, NULL, '6001', '主营业务收入', 'revenue', 'credit', TRUE, 'active'),
(9007, NULL, '6602', '管理费用', 'expense', 'debit', TRUE, 'active');

INSERT INTO opening_balances (ledger_book_id, subject_id, period_code, debit_amount, credit_amount) VALUES
(1, 9001, '2026-01', 850000.00, 0.00),
(1, 9002, '2026-01', 216000.00, 0.00),
(1, 9003, '2026-01', 0.00, 178000.00),
(1, 9004, '2026-01', 42000.00, 0.00);

INSERT INTO account_balances (ledger_book_id, subject_id, period_code,
    begin_debit, begin_credit, current_debit, current_credit, ending_debit, ending_credit)
VALUES
(1, 9001, '2026-02', 850000.00, 0.00, 128000.00, 56000.00, 922000.00, 0.00),
(1, 9002, '2026-02', 216000.00, 0.00, 17980.00, 12000.00, 221980.00, 0.00),
(1, 9003, '2026-02', 0.00, 178000.00, 56000.00, 72000.00, 0.00, 194000.00),
(1, 9005, '2026-02', 0.00, 0.00, 2537.00, 0.00, 2537.00, 0.00);

INSERT INTO budget_versions (id, ledger_book_id, version_code, version_name, fiscal_year, status, approved_by) VALUES
(9001, 1, 'BUD-2026-FINAL', '2026年度正式预算', 2026, 'approved', 34);

INSERT INTO budget_items (version_id, department_id, subject_id, period_code, budget_amount, used_amount) VALUES
(9001, 5, 9007, '2026-02', 120000.00, 38400.00),
(9001, 3, 9007, '2026-02', 85000.00, 22000.00),
(9001, 4, 9007, '2026-02', 65000.00, 18000.00);

INSERT INTO ar_invoices (id, ar_no, sales_order_id, customer_id, invoice_date, due_date,
    invoice_amount, paid_amount, writeoff_amount, status)
VALUES
(9001, 'AR-202602-9001', 9001, 1, '2026-02-12', '2026-03-14', 17980.00, 12000.00, 0.00, 'partially_paid');

INSERT INTO ap_invoices (id, ap_no, purchase_order_id, supplier_id, invoice_date, due_date,
    invoice_amount, paid_amount, status)
VALUES
(9001, 'AP-202602-9001', 1, 1, '2026-02-05', '2026-03-07', 72000.00, 56000.00, 'partially_paid');

INSERT INTO payment_requests (id, request_no, supplier_id, requested_by, request_date,
    planned_pay_date, total_amount, status)
VALUES
(9001, 'PAYREQ-202602-9001', 1, 36, '2026-02-18', '2026-02-25', 16000.00, 'approved');

INSERT INTO payment_request_items (request_id, ap_invoice_id, requested_amount) VALUES
(9001, 9001, 16000.00);

-- ============================================================
-- 4. WMS库位、拣货、售后维修和主数据治理
-- ============================================================

INSERT INTO warehouse_zones (id, warehouse_id, zone_code, zone_name, zone_type) VALUES
(9001, 1, 'A', '主仓A区', 'storage'),
(9002, 1, 'PICK', '主仓拣货区', 'picking'),
(9003, 1, 'QC', '主仓质检区', 'qc'),
(9004, 2, 'R', '门店退货区', 'returns');

INSERT INTO warehouse_locations (id, zone_id, location_code, location_type, max_weight_kg, max_volume_m3, status) VALUES
(9001, 9001, 'A-01-01', 'shelf', 500.000, 12.000000, 'active'),
(9002, 9001, 'A-01-02', 'shelf', 500.000, 12.000000, 'active'),
(9003, 9002, 'P-01-01', 'bin', 120.000, 3.000000, 'active'),
(9004, 9003, 'QC-01', 'dock', 1000.000, 20.000000, 'active'),
(9005, 9004, 'R-01', 'pallet', 800.000, 16.000000, 'active');

INSERT INTO inventory_location_balances (location_id, product_id, batch_id, quantity, locked_quantity) VALUES
(9001, 1, 1, 62.0000, 12.0000),
(9002, 2, 2, 140.0000, 0.0000),
(9003, 1, 1, 20.0000, 0.0000),
(9004, 3, 3, 16.0000, 0.0000);

INSERT INTO putaway_tasks (id, task_no, receipt_id, product_id, batch_id, from_location_id,
    to_location_id, quantity, assigned_to, status)
VALUES
(9001, 'PUT-202602-9001', 1, 2, 2, 9004, 9002, 60.0000, 45, 'completed');

INSERT INTO picking_tasks (id, task_no, sales_order_id, warehouse_id, wave_no, assigned_to, status) VALUES
(9001, 'PICK-202602-9001', 9001, 1, 'WAVE-202602-01', 46, 'picked');

INSERT INTO picking_task_items (picking_task_id, sales_order_item_id, product_id, batch_id,
    location_id, required_qty, picked_qty)
VALUES
(9001, 9001, 1, 1, 9003, 20.0000, 20.0000);

INSERT INTO service_tickets (id, ticket_no, customer_id, order_id, product_id, ticket_type,
    priority, subject, description, status, assigned_to, resolution, resolved_at, satisfaction_score)
VALUES
(9001, 'ST-202602-9001', 1, 9001, 1, 'maintenance', 'high',
 '设备开机异常', '客户反馈批量销售商品开机后自动重启', 'processing', 53, NULL, NULL, NULL);

INSERT INTO repair_orders (id, repair_no, service_ticket_id, customer_id, product_id,
    serial_number_id, received_date, fault_desc, status, technician_id, estimated_cost, actual_cost)
VALUES
(9001, 'RMA-202602-9001', 9001, 1, 1, NULL, '2026-02-16',
 '主板供电模块异常，需要更换备件', 'repairing', 53, 420.00, 0.00);

INSERT INTO repair_order_parts (repair_order_id, product_id, batch_id, quantity, unit_cost, issued_from_warehouse_id) VALUES
(9001, 3, 3, 1.0000, 18.2000, 1),
(9001, 4, NULL, 2.0000, 9.8000, 1);

INSERT INTO numbering_rules (id, document_type, prefix, date_pattern, sequence_length, current_sequence) VALUES
(9001, 'production_plan', 'PP', '%Y%m', 4, 9001),
(9002, 'payment_request', 'PAYREQ', '%Y%m', 4, 9001),
(9003, 'repair_order', 'RMA', '%Y%m', 4, 9001);

INSERT INTO master_data_change_requests (id, request_no, master_type, master_id,
    change_reason, requested_by, approved_by, approved_at, status)
VALUES
(9001, 'MDC-202602-9001', 'customer', 1, '客户注册地址变更', 9, 5, '2026-02-12 10:30:00', 'approved'),
(9002, 'MDC-202602-9002', 'product', 1, '商品规格参数补充', 57, NULL, NULL, 'submitted');

INSERT INTO master_data_change_items (request_id, field_name, old_value, new_value) VALUES
(9001, 'registered_address', '世纪大道100号', '世纪大道188号'),
(9001, 'postal_code', '200120', '200122'),
(9002, 'spec', '标准款', '标准款-2026改良版');

INSERT INTO data_permission_scopes (role_id, scope_type, scope_id, can_read, can_write) VALUES
(1, 'department', 2, TRUE, TRUE),
(2, 'warehouse', 1, TRUE, FALSE),
(3, 'customer', 1, TRUE, FALSE);

INSERT INTO sensitive_access_logs (employee_id, object_type, object_id, field_name, access_reason, accessed_at) VALUES
(36, 'customer', 1, 'credit_limit', '审批客户信用额度调整', '2026-02-12 09:20:00'),
(39, 'employee', 9, 'bank_account', '工资付款复核', '2026-02-15 15:40:00');

-- ============================================================
-- 5. 深业务场景补充样本: 多订单、多计划、多成本、多库位
-- ============================================================

INSERT INTO sales_orders (id, order_no, customer_id, salesperson_id, warehouse_id, order_date, delivery_date,
    total_amount, discount_amount, paid_amount, tax_amount, payment_method, status, invoice_no, remark)
VALUES
(9010, 'SO-DEEP-9010', 2, 6, 1, '2026-02-18', '2026-02-21', 28460.00, 860.00, 18000.00, 3275.00, 'transfer', 'confirmed', 'INV-DEEP-9010', '深场景-渠道订单'),
(9011, 'SO-DEEP-9011', 3, 7, 1, '2026-02-19', '2026-02-23', 16880.00, 300.00, 16880.00, 1942.00, 'card', 'delivered', 'INV-DEEP-9011', '深场景-全额收款订单'),
(9012, 'SO-DEEP-9012', 4, 8, 2, '2026-03-02', '2026-03-06', 36200.00, 1200.00, 0.00, 4166.00, 'credit', 'confirmed', 'INV-DEEP-9012', '深场景-账期订单'),
(9013, 'SO-DEEP-9013', 5, 9, 2, '2026-03-05', '2026-03-08', 9340.00, 120.00, 3000.00, 1075.00, 'wechat', 'delivering', 'INV-DEEP-9013', '深场景-小批量订单');

INSERT INTO sales_order_items (id, order_id, product_id, batch_id, quantity, unit_price, discount, amount, returned_qty) VALUES
(9010, 9010, 10, NULL, 35, 380.00, 300.00, 13000.00, 0),
(9011, 9010, 11, NULL, 18, 720.00, 560.00, 12400.00, 0),
(9012, 9011, 31, NULL, 8, 1680.00, 300.00, 13140.00, 0),
(9013, 9011, 35, NULL, 5, 748.00, 0.00, 3740.00, 0),
(9014, 9012, 34, NULL, 12, 2580.00, 900.00, 30060.00, 0),
(9015, 9012, 33, NULL, 20, 307.00, 300.00, 5840.00, 0),
(9016, 9013, 22, NULL, 7, 980.00, 120.00, 6740.00, 0),
(9017, 9013, 18, NULL, 26, 100.00, 0.00, 2600.00, 0);

INSERT INTO purchase_orders (id, order_no, supplier_id, requisition_id, department_id, purchaser_id,
    order_date, expected_delivery_date, actual_delivery_date, total_amount, paid_amount, payment_terms, status, remark)
VALUES
(9010, 'PO-DEEP-9010', 1, NULL, 3, 32, '2026-02-08', '2026-02-18', '2026-02-17', 43600.00, 22000.00, '30天账期', 'partially_received', '深场景-MRP补货'),
(9011, 'PO-DEEP-9011', 2, NULL, 3, 32, '2026-02-10', '2026-02-22', '2026-02-22', 28600.00, 0.00, '货到票到付款', 'received', '深场景-生产辅料'),
(9012, 'PO-DEEP-9012', 3, NULL, 4, 33, '2026-03-01', '2026-03-12', NULL, 51200.00, 0.00, '45天账期', 'ordered', '深场景-春季补货');

INSERT INTO purchase_order_items (id, order_id, product_id, quantity, unit_price, received_qty, returned_qty, remark) VALUES
(9010, 9010, 47, 260, 42.00, 180, 0, '红枣原料补货'),
(9011, 9010, 9, 420, 18.00, 300, 0, '包装材料补货'),
(9012, 9011, 33, 90, 115.00, 90, 0, '真丝围巾备料'),
(9013, 9011, 36, 160, 88.00, 160, 0, '运动T恤备料'),
(9014, 9012, 34, 20, 1680.00, 0, 0, '女装大衣补货'),
(9015, 9012, 35, 40, 440.00, 0, 0, '运动跑鞋补货');

INSERT INTO purchase_receipts (id, receipt_no, order_id, warehouse_id, receiver_id, receipt_date,
    batch_no, total_qty, total_amount, status, inspection_result, remark)
VALUES
(9010, 'PRC-DEEP-9010', 9010, 1, 45, '2026-02-17', 'B-DEEP-9010', 480, 30200.00, 'received', '抽检合格', '深场景-MRP补货到货'),
(9011, 'PRC-DEEP-9011', 9011, 1, 45, '2026-02-22', 'B-DEEP-9011', 250, 24430.00, 'inspected', '全部合格', '深场景-辅料到货');

INSERT INTO purchase_receipt_items (id, receipt_id, order_item_id, product_id, batch_id,
    received_qty, accepted_qty, rejected_qty, unit_price, production_date, expiry_date, remark)
VALUES
(9010, 9010, 9010, 47, NULL, 180, 178, 2, 42.00, '2026-02-05', '2027-02-05', '红枣到货'),
(9011, 9010, 9011, 9, NULL, 300, 300, 0, 18.00, '2026-01-30', '2027-01-30', '包装材料到货'),
(9012, 9011, 9012, 33, NULL, 90, 90, 0, 115.00, '2026-02-18', NULL, '围巾辅料到货'),
(9013, 9011, 9013, 36, NULL, 160, 158, 2, 88.00, '2026-02-16', NULL, '运动T恤到货');

INSERT INTO production_plans (id, plan_no, product_id, warehouse_id, plan_month,
    forecast_qty, confirmed_sales_qty, safety_stock_qty, planned_production_qty,
    status, planner_id, approved_by, approved_at)
VALUES
(9010, 'PP-202603-9010', 10, 1, '2026-03', 240, 120, 50, 260, 'released', 27, 26, '2026-03-01 09:20:00'),
(9011, 'PP-202603-9011', 31, 1, '2026-03', 80, 36, 12, 90, 'approved', 27, 26, '2026-03-01 09:45:00'),
(9012, 'PP-202603-9012', 34, 2, '2026-03', 75, 42, 15, 88, 'approved', 28, 26, '2026-03-02 10:10:00'),
(9013, 'PP-202604-9013', 35, 2, '2026-04', 130, 61, 20, 148, 'draft', 28, NULL, NULL),
(9014, 'PP-202604-9014', 22, 1, '2026-04', 95, 50, 10, 105, 'released', 27, 26, '2026-04-01 08:30:00');

INSERT INTO mrp_runs (id, run_no, plan_id, run_date, demand_source, status, created_by, completed_at) VALUES
(9010, 'MRP-202603-9010', 9010, '2026-03-01', 'forecast', 'completed', 27, '2026-03-01 11:00:00'),
(9011, 'MRP-202603-9011', 9011, '2026-03-01', 'sales_order', 'completed', 27, '2026-03-01 11:25:00'),
(9012, 'MRP-202603-9012', 9012, '2026-03-02', 'forecast', 'completed', 28, '2026-03-02 15:10:00'),
(9013, 'MRP-202604-9013', 9013, '2026-04-01', 'manual', 'running', 28, NULL),
(9014, 'MRP-202604-9014', 9014, '2026-04-01', 'safety_stock', 'completed', 27, '2026-04-01 13:40:00');

INSERT INTO mrp_run_items (run_id, parent_product_id, component_product_id,
    gross_requirement, on_hand_qty, reserved_qty, planned_receipt_qty,
    net_requirement, suggested_order_qty, suggested_supplier_id, suggested_due_date)
VALUES
(9010, 10, 47, 53.0400, 18.0000, 4.0000, 180.0000, 0.0000, 0.0000, 1, '2026-03-08'),
(9010, 10, 9, 26.2600, 8.0000, 3.0000, 300.0000, 0.0000, 0.0000, 1, '2026-03-08'),
(9011, 31, 33, 4.5900, 12.0000, 2.0000, 90.0000, 0.0000, 0.0000, 2, '2026-03-10'),
(9011, 31, 36, 27.2700, 34.0000, 6.0000, 160.0000, 0.0000, 0.0000, 2, '2026-03-10'),
(9012, 34, 33, 8.9760, 12.0000, 4.0000, 90.0000, 0.0000, 0.0000, 2, '2026-03-11'),
(9012, 34, 32, 17.7760, 5.0000, 3.0000, 0.0000, 15.7760, 16.0000, 3, '2026-03-14'),
(9014, 22, 18, 52.5000, 18.0000, 6.0000, 0.0000, 40.5000, 41.0000, 1, '2026-04-07'),
(9014, 22, 19, 21.0000, 24.0000, 2.0000, 0.0000, 0.0000, 0.0000, 1, '2026-04-07');

INSERT INTO work_order_operations (id, work_order_id, operation_id, operation_seq,
    planned_start, planned_end, actual_start, actual_end, status, assigned_employee_id,
    qualified_qty, scrapped_qty, rework_qty)
VALUES
(9010, 2, 1, 10, '2026-03-03 08:30:00', '2026-03-03 09:10:00', '2026-03-03 08:35:00', '2026-03-03 09:08:00', 'completed', 45, 120, 1, 2),
(9011, 2, 2, 20, '2026-03-03 09:20:00', '2026-03-03 12:00:00', '2026-03-03 09:22:00', '2026-03-03 12:05:00', 'completed', 46, 118, 2, 3),
(9012, 2, 3, 30, '2026-03-03 13:00:00', '2026-03-03 14:10:00', '2026-03-03 13:05:00', '2026-03-03 14:20:00', 'completed', 51, 116, 2, 2),
(9013, 3, 1, 10, '2026-03-06 08:30:00', '2026-03-06 09:15:00', '2026-03-06 08:32:00', NULL, 'running', 45, 60, 0, 0),
(9014, 3, 2, 20, '2026-03-06 09:30:00', '2026-03-06 12:00:00', NULL, NULL, 'pending', 46, 0, 0, 0);

INSERT INTO operation_reports (id, work_order_operation_id, report_no, employee_id,
    report_time, input_qty, qualified_qty, scrapped_qty, rework_qty, labor_minutes, machine_minutes)
VALUES
(9010, 9010, 'OPR-202603-9010', 45, '2026-03-03 09:10:00', 121, 120, 1, 2, 52.00, 36.00),
(9011, 9011, 'OPR-202603-9011', 46, '2026-03-03 12:08:00', 120, 118, 2, 3, 172.00, 166.00),
(9012, 9012, 'OPR-202603-9012', 51, '2026-03-03 14:25:00', 118, 116, 2, 2, 82.00, 62.00),
(9013, 9013, 'OPR-202603-9013', 45, '2026-03-06 10:10:00', 60, 60, 0, 0, 96.00, 70.00);

INSERT INTO material_issues (id, issue_no, work_order_id, warehouse_id, issue_date, issued_by, status) VALUES
(9010, 'MI-202603-9010', 2, 1, '2026-03-03', 45, 'posted'),
(9011, 'MI-202603-9011', 2, 1, '2026-03-04', 45, 'posted'),
(9012, 'MI-202603-9012', 3, 1, '2026-03-06', 46, 'draft');

INSERT INTO material_issue_items (issue_id, product_id, batch_id, required_qty, issued_qty, unit_cost) VALUES
(9010, 47, NULL, 30.0000, 30.0000, 42.0000),
(9010, 9, NULL, 16.0000, 16.0000, 18.0000),
(9011, 33, NULL, 8.0000, 8.0000, 115.0000),
(9011, 36, NULL, 22.0000, 21.0000, 88.0000),
(9012, 32, NULL, 12.0000, 0.0000, 92.0000);

INSERT INTO finished_goods_receipts (id, receipt_no, work_order_id, product_id, batch_id,
    warehouse_id, receipt_date, received_qty, unit_cost, received_by, status)
VALUES
(9010, 'FGR-202603-9010', 2, 10, NULL, 1, '2026-03-04', 116, 148.2600, 45, 'posted'),
(9011, 'FGR-202603-9011', 3, 31, NULL, 1, '2026-03-07', 60, 615.0000, 46, 'draft'),
(9012, 'FGR-202604-9012', 4, 22, NULL, 1, '2026-04-05', 80, 322.5000, 45, 'posted');

INSERT INTO standard_costs (id, product_id, cost_version, material_cost, labor_cost,
    overhead_cost, effective_from, effective_to, status, approved_by)
VALUES
(9010, 4, 'STD-2026', 7.5000, 1.5000, 1.0000, '2026-01-01', NULL, 'active', 34),
(9011, 9, 'STD-2026', 13.0000, 2.0000, 1.5000, '2026-01-01', NULL, 'active', 34),
(9012, 10, 'STD-2026', 94.0000, 24.0000, 16.0000, '2026-01-01', NULL, 'active', 34),
(9013, 22, 'STD-2026', 255.0000, 42.0000, 25.0000, '2026-01-01', NULL, 'active', 34),
(9014, 31, 'STD-2026', 520.0000, 62.0000, 36.0000, '2026-01-01', NULL, 'active', 34),
(9015, 34, 'STD-2026', 1380.0000, 120.0000, 80.0000, '2026-01-01', NULL, 'active', 34),
(9016, 35, 'STD-2026', 330.0000, 48.0000, 28.0000, '2026-01-01', NULL, 'active', 34);

INSERT INTO inventory_cost_layers (id, product_id, batch_id, warehouse_id, source_type,
    source_id, receipt_date, original_qty, remaining_qty, unit_cost, currency)
VALUES
(9010, 10, NULL, 1, 'work_order_receipt', 9010, '2026-03-04', 116.0000, 100.0000, 148.2600, 'CNY'),
(9011, 47, NULL, 1, 'purchase_receipt', 9010, '2026-02-17', 180.0000, 160.0000, 42.0000, 'CNY'),
(9012, 9, NULL, 1, 'purchase_receipt', 9010, '2026-02-17', 300.0000, 260.0000, 18.0000, 'CNY'),
(9013, 33, NULL, 1, 'purchase_receipt', 9011, '2026-02-22', 90.0000, 82.0000, 115.0000, 'CNY'),
(9014, 36, NULL, 1, 'purchase_receipt', 9011, '2026-02-22', 160.0000, 139.0000, 88.0000, 'CNY'),
(9015, 22, NULL, 1, 'work_order_receipt', 9012, '2026-04-05', 80.0000, 80.0000, 322.5000, 'CNY');

INSERT INTO inventory_valuation_snapshots (snapshot_date, product_id, warehouse_id,
    quantity, unit_cost, inventory_value, valuation_method)
VALUES
('2026-03-31', 10, 1, 100.0000, 148.2600, 14826.00, 'moving_average'),
('2026-03-31', 47, 1, 160.0000, 42.0000, 6720.00, 'moving_average'),
('2026-03-31', 9, 1, 260.0000, 18.0000, 4680.00, 'moving_average'),
('2026-03-31', 33, 1, 82.0000, 115.0000, 9430.00, 'moving_average'),
('2026-03-31', 36, 1, 139.0000, 88.0000, 12232.00, 'moving_average'),
('2026-04-30', 22, 1, 80.0000, 322.5000, 25800.00, 'standard');

INSERT INTO work_order_costs (id, work_order_id, material_cost, labor_cost, overhead_cost,
    finished_qty, unit_cost, variance_amount, calculated_at)
VALUES
(9010, 2, 4316.00, 1836.00, 1101.60, 116, 62.5310, -992.40, '2026-03-04 18:00:00'),
(9011, 3, 1104.00, 115.20, 69.12, 60, 21.4720, -380.88, '2026-03-07 18:00:00'),
(9012, 4, 20400.00, 980.00, 588.00, 80, 274.6000, -3832.00, '2026-04-05 18:00:00');

INSERT INTO vouchers (id, voucher_no, voucher_date, voucher_type, reference_type, reference_id,
    total_debit, total_credit, prepared_by, reviewed_by, posted_by, status, summary)
VALUES
(9110, 'V-COGS-202603-9010', '2026-03-06', 'journal', 'sales_order', 9010, 5189.10, 5189.10, 36, 35, 34, 'posted', '结转渠道订单成本'),
(9111, 'V-COGS-202603-9011', '2026-03-08', 'journal', 'sales_order', 9011, 5690.00, 5690.00, 36, 35, 34, 'posted', '结转服饰订单成本'),
(9112, 'V-WOC-202604-9012', '2026-04-05', 'journal', 'work_order', 4, 25800.00, 25800.00, 36, 35, 34, 'posted', '结转办公椅完工成本');

INSERT INTO voucher_items (voucher_id, account_id, line_no, direction, amount, summary) VALUES
(9110, 31, 1, 'debit', 5189.10, '借: 主营业务成本'),
(9110, 12, 2, 'credit', 5189.10, '贷: 库存商品'),
(9111, 31, 1, 'debit', 5690.00, '借: 主营业务成本'),
(9111, 12, 2, 'credit', 5690.00, '贷: 库存商品'),
(9112, 12, 1, 'debit', 25800.00, '借: 库存商品'),
(9112, 41, 2, 'credit', 25800.00, '贷: 生产成本');

INSERT INTO cogs_entries (id, sales_order_id, sales_order_item_id, product_id, batch_id,
    quantity, unit_cost, cogs_amount, voucher_id, posted_at)
VALUES
(9010, 9010, 9010, 10, NULL, 35.0000, 148.2600, 5189.10, 9110, '2026-03-06 18:20:00'),
(9011, 9010, 9011, 11, NULL, 18.0000, 210.0000, 3780.00, 9110, '2026-03-06 18:20:00'),
(9012, 9011, 9012, 31, NULL, 8.0000, 615.0000, 4920.00, 9111, '2026-03-08 17:45:00'),
(9013, 9011, 9013, 35, NULL, 5.0000, 154.0000, 770.00, 9111, '2026-03-08 17:45:00'),
(9014, 9012, 9014, 34, NULL, 12.0000, 1580.0000, 18960.00, NULL, NULL),
(9015, 9012, 9015, 33, NULL, 20.0000, 115.0000, 2300.00, NULL, NULL);

INSERT INTO account_balances (ledger_book_id, subject_id, period_code,
    begin_debit, begin_credit, current_debit, current_credit, ending_debit, ending_credit)
VALUES
(1, 9001, '2026-03', 922000.00, 0.00, 218000.00, 164000.00, 976000.00, 0.00),
(1, 9002, '2026-03', 221980.00, 0.00, 81480.00, 34880.00, 268580.00, 0.00),
(1, 9003, '2026-03', 0.00, 194000.00, 76000.00, 122400.00, 0.00, 240400.00),
(1, 9005, '2026-03', 2537.00, 0.00, 10879.10, 0.00, 13416.10, 0.00),
(1, 9006, '2026-03', 0.00, 0.00, 0.00, 45340.00, 0.00, 45340.00);

INSERT INTO budget_items (version_id, department_id, subject_id, period_code, budget_amount, used_amount) VALUES
(9001, 5, 9007, '2026-03', 125000.00, 42900.00),
(9001, 3, 9007, '2026-03', 88000.00, 27400.00),
(9001, 4, 9007, '2026-03', 68000.00, 19600.00),
(9001, 5, 9005, '2026-03', 320000.00, 13416.10),
(9001, 3, 9005, '2026-04', 260000.00, 0.00);

INSERT INTO ar_invoices (id, ar_no, sales_order_id, customer_id, invoice_date, due_date,
    invoice_amount, paid_amount, writeoff_amount, status)
VALUES
(9010, 'AR-202602-9010', 9010, 2, '2026-02-18', '2026-03-20', 28460.00, 18000.00, 0.00, 'partially_paid'),
(9011, 'AR-202602-9011', 9011, 3, '2026-02-19', '2026-03-21', 16880.00, 16880.00, 0.00, 'paid'),
(9012, 'AR-202603-9012', 9012, 4, '2026-03-02', '2026-04-01', 36200.00, 0.00, 0.00, 'open'),
(9013, 'AR-202603-9013', 9013, 5, '2026-03-05', '2026-04-04', 9340.00, 3000.00, 0.00, 'partially_paid');

INSERT INTO ap_invoices (id, ap_no, purchase_order_id, supplier_id, invoice_date, due_date,
    invoice_amount, paid_amount, status)
VALUES
(9010, 'AP-202602-9010', 9010, 1, '2026-02-17', '2026-03-19', 43600.00, 22000.00, 'partially_paid'),
(9011, 'AP-202602-9011', 9011, 2, '2026-02-22', '2026-03-24', 28600.00, 0.00, 'open'),
(9012, 'AP-202603-9012', 9012, 3, '2026-03-03', '2026-04-17', 51200.00, 0.00, 'open');

INSERT INTO payment_requests (id, request_no, supplier_id, requested_by, request_date,
    planned_pay_date, total_amount, status)
VALUES
(9010, 'PAYREQ-202603-9010', 1, 36, '2026-03-10', '2026-03-18', 21600.00, 'submitted'),
(9011, 'PAYREQ-202603-9011', 2, 36, '2026-03-12', '2026-03-22', 28600.00, 'approved'),
(9012, 'PAYREQ-202604-9012', 3, 37, '2026-04-02', '2026-04-16', 30000.00, 'draft');

INSERT INTO payment_request_items (request_id, ap_invoice_id, requested_amount) VALUES
(9010, 9010, 21600.00),
(9011, 9011, 28600.00),
(9012, 9012, 30000.00);

INSERT INTO warehouse_zones (id, warehouse_id, zone_code, zone_name, zone_type) VALUES
(9010, 1, 'B', '主仓B区', 'storage'),
(9011, 1, 'STAGE', '主仓集货区', 'staging'),
(9012, 2, 'PICK', '二仓拣货区', 'picking'),
(9013, 2, 'QC', '二仓质检区', 'qc');

INSERT INTO warehouse_locations (id, zone_id, location_code, location_type, max_weight_kg, max_volume_m3, status) VALUES
(9010, 9010, 'B-01-01', 'shelf', 600.000, 14.000000, 'active'),
(9011, 9010, 'B-01-02', 'shelf', 600.000, 14.000000, 'active'),
(9012, 9011, 'S-01', 'dock', 1500.000, 28.000000, 'active'),
(9013, 9012, 'P2-01-01', 'bin', 120.000, 3.000000, 'active'),
(9014, 9013, 'QC2-01', 'dock', 1000.000, 18.000000, 'active');

INSERT INTO inventory_location_balances (location_id, product_id, batch_id, quantity, locked_quantity) VALUES
(9010, 10, NULL, 100.0000, 35.0000),
(9011, 47, NULL, 160.0000, 0.0000),
(9012, 9, NULL, 260.0000, 20.0000),
(9013, 34, NULL, 30.0000, 12.0000),
(9014, 35, NULL, 40.0000, 4.0000),
(9003, 22, NULL, 80.0000, 7.0000);

INSERT INTO putaway_tasks (id, task_no, receipt_id, product_id, batch_id, from_location_id,
    to_location_id, quantity, assigned_to, status)
VALUES
(9010, 'PUT-202603-9010', 9010, 47, NULL, 9004, 9011, 160.0000, 45, 'completed'),
(9011, 'PUT-202603-9011', 9010, 9, NULL, 9004, 9012, 260.0000, 46, 'running'),
(9012, 'PUT-202603-9012', 9011, 33, NULL, 9014, 9013, 82.0000, 45, 'pending');

INSERT INTO picking_tasks (id, task_no, sales_order_id, warehouse_id, wave_no, assigned_to, status) VALUES
(9010, 'PICK-202603-9010', 9010, 1, 'WAVE-202603-01', 46, 'allocated'),
(9011, 'PICK-202603-9011', 9011, 1, 'WAVE-202603-01', 51, 'picked'),
(9012, 'PICK-202603-9012', 9012, 2, 'WAVE-202603-02', 46, 'pending');

INSERT INTO picking_task_items (picking_task_id, sales_order_item_id, product_id, batch_id,
    location_id, required_qty, picked_qty)
VALUES
(9010, 9010, 10, NULL, 9010, 35.0000, 0.0000),
(9010, 9011, 11, NULL, 9001, 18.0000, 0.0000),
(9011, 9012, 31, NULL, 9001, 8.0000, 8.0000),
(9011, 9013, 35, NULL, 9014, 5.0000, 5.0000),
(9012, 9014, 34, NULL, 9013, 12.0000, 0.0000);

INSERT INTO service_tickets (id, ticket_no, customer_id, order_id, product_id, ticket_type,
    priority, subject, description, status, assigned_to, resolution, resolved_at, satisfaction_score)
VALUES
(9010, 'ST-202603-9010', 2, 9010, 10, 'complaint', 'normal', '包装破损', '客户反馈外包装挤压破损', 'resolved', 53, '补发包装材料并登记物流索赔', '2026-03-02 16:00:00', 4),
(9011, 'ST-202603-9011', 3, 9011, 31, 'maintenance', 'high', '拉链卡顿', '客户反馈服装拉链卡顿需要维修', 'processing', 54, NULL, NULL, NULL),
(9012, 'ST-202603-9012', 4, 9012, 34, 'inquiry', 'low', '配送时效咨询', '客户询问账期订单预计发货时间', 'closed', 53, '已回复预计发货时间', '2026-03-03 10:00:00', 5);

INSERT INTO repair_orders (id, repair_no, service_ticket_id, customer_id, product_id,
    serial_number_id, received_date, fault_desc, status, technician_id, estimated_cost, actual_cost)
VALUES
(9010, 'RMA-202603-9010', 9011, 3, 31, NULL, '2026-03-04', '服装拉链组件需要更换', 'repairing', 54, 180.00, 0.00),
(9011, 'RMA-202603-9011', 9010, 2, 10, NULL, '2026-03-02', '包装破损复检', 'completed', 53, 30.00, 18.00);

INSERT INTO repair_order_parts (repair_order_id, product_id, batch_id, quantity, unit_cost, issued_from_warehouse_id) VALUES
(9010, 33, NULL, 1.0000, 115.0000, 1),
(9010, 36, NULL, 1.0000, 88.0000, 1),
(9011, 9, NULL, 1.0000, 18.0000, 1);

INSERT INTO numbering_rules (id, document_type, prefix, date_pattern, sequence_length, current_sequence) VALUES
(9010, 'mrp_run', 'MRP', '%Y%m', 4, 9014),
(9011, 'picking_task', 'PICK', '%Y%m', 4, 9012),
(9012, 'finished_goods_receipt', 'FGR', '%Y%m', 4, 9012),
(9013, 'master_data_change', 'MDC', '%Y%m', 4, 9013);

INSERT INTO master_data_change_requests (id, request_no, master_type, master_id,
    change_reason, requested_by, approved_by, approved_at, status)
VALUES
(9010, 'MDC-202603-9010', 'supplier', 1, '供应商开票地址更新', 32, 5, '2026-03-01 13:40:00', 'approved'),
(9011, 'MDC-202603-9011', 'customer', 2, '客户信用联系人变更', 9, 5, '2026-03-05 15:20:00', 'approved'),
(9012, 'MDC-202603-9012', 'product', 34, '女装商品规格补充', 57, NULL, NULL, 'submitted'),
(9013, 'MDC-202604-9013', 'account', 9007, '管理费用科目说明维护', 36, 34, '2026-04-02 11:00:00', 'applied');

INSERT INTO master_data_change_items (request_id, field_name, old_value, new_value) VALUES
(9010, 'billing_address', '南京路88号', '南京路188号'),
(9010, 'tax_no', '91310000OLD', '91310000NEW'),
(9011, 'contact_person', '王经理', '李经理'),
(9011, 'phone', '13800000001', '13800000099'),
(9012, 'spec', '冬季标准款', '冬季加厚款'),
(9013, 'description', '管理费用', '管理费用-含数字化项目');

INSERT INTO data_permission_scopes (role_id, scope_type, scope_id, can_read, can_write) VALUES
(1, 'warehouse', 1, TRUE, TRUE),
(1, 'warehouse', 2, TRUE, TRUE),
(2, 'department', 3, TRUE, FALSE),
(2, 'supplier', 1, TRUE, FALSE),
(3, 'region', 310000, TRUE, FALSE),
(4, 'customer', 2, TRUE, FALSE);

INSERT INTO sensitive_access_logs (employee_id, object_type, object_id, field_name, access_reason, accessed_at) VALUES
(36, 'supplier', 1, 'bank_account', '付款申请复核', '2026-03-10 10:15:00'),
(37, 'supplier', 2, 'tax_no', '供应商主数据审核', '2026-03-12 09:40:00'),
(39, 'customer', 2, 'credit_limit', '客户账期复核', '2026-03-05 16:10:00'),
(53, 'service_ticket', 9010, 'description', '售后投诉处理', '2026-03-02 15:20:00'),
(54, 'repair_order', 9010, 'fault_desc', '维修诊断', '2026-03-04 11:35:00');

-- Semantic-layer example support data: regions, fiscal dates, category dimension,
-- payments, and sales facts.

INSERT INTO region_dim (id, region_code, region_name, province, city, district, sales_region, region_level, is_active) VALUES
(1, 'EAST-SH', '上海销售区', '上海市', '上海市', NULL, '华东', 'city', TRUE),
(2, 'EAST-HZ', '杭州销售区', '浙江省', '杭州市', NULL, '华东', 'city', TRUE),
(3, 'EAST-SZ', '苏州销售区', '江苏省', '苏州市', NULL, '华东', 'city', TRUE),
(4, 'EAST-NJ', '南京销售区', '江苏省', '南京市', NULL, '华东', 'city', TRUE),
(5, 'SOUTH-GZ', '广州销售区', '广东省', '广州市', NULL, '华南', 'city', TRUE),
(6, 'NORTH-BJ', '北京销售区', '北京市', '北京市', NULL, '华北', 'city', TRUE);

INSERT INTO fiscal_calendar (
    calendar_date, fiscal_year, fiscal_quarter, fiscal_month, fiscal_month_name,
    period_code, period_start, period_end, is_current_fiscal_year, accounting_period_id
) VALUES
('2026-01-01', 2026, 1, 1, '2026-01', '2026-01', '2026-01-01', '2026-01-31', TRUE, 1),
('2026-01-15', 2026, 1, 1, '2026-01', '2026-01', '2026-01-01', '2026-01-31', TRUE, 1),
('2026-02-01', 2026, 1, 2, '2026-02', '2026-02', '2026-02-01', '2026-02-28', TRUE, 2),
('2026-02-12', 2026, 1, 2, '2026-02', '2026-02', '2026-02-01', '2026-02-28', TRUE, 2),
('2026-02-18', 2026, 1, 2, '2026-02', '2026-02', '2026-02-01', '2026-02-28', TRUE, 2),
('2026-02-19', 2026, 1, 2, '2026-02', '2026-02', '2026-02-01', '2026-02-28', TRUE, 2),
('2026-03-01', 2026, 1, 3, '2026-03', '2026-03', '2026-03-01', '2026-03-31', TRUE, NULL),
('2026-03-02', 2026, 1, 3, '2026-03', '2026-03', '2026-03-01', '2026-03-31', TRUE, NULL),
('2026-03-05', 2026, 1, 3, '2026-03', '2026-03', '2026-03-01', '2026-03-31', TRUE, NULL),
('2026-03-18', 2026, 1, 3, '2026-03', '2026-03', '2026-03-01', '2026-03-31', TRUE, NULL),
('2026-04-01', 2026, 2, 4, '2026-04', '2026-04', '2026-04-01', '2026-04-30', TRUE, NULL),
('2026-04-08', 2026, 2, 4, '2026-04', '2026-04', '2026-04-01', '2026-04-30', TRUE, NULL),
('2026-05-01', 2026, 2, 5, '2026-05', '2026-05', '2026-05-01', '2026-05-31', TRUE, NULL),
('2026-06-01', 2026, 2, 6, '2026-06', '2026-06', '2026-06-01', '2026-06-30', TRUE, NULL),
('2025-12-20', 2025, 4, 12, '2025-12', '2025-12', '2025-12-01', '2025-12-31', FALSE, NULL);

INSERT INTO category_dim (
    id, source_category_id, category_code, level1_name, level2_name, leaf_name,
    is_womenwear, effective_from, status
) VALUES
(5, 5, 'CLOTHING', '服装鞋帽', NULL, '服装鞋帽', FALSE, '2026-01-01', 'active'),
(17, 17, 'WOMENSWEAR', '服装鞋帽', '女装', '女装', TRUE, '2026-01-01', 'active'),
(18, 18, 'MENSWEAR', '服装鞋帽', '男装', '男装', FALSE, '2026-01-01', 'active'),
(19, 19, 'KIDSWEAR', '服装鞋帽', '童装', '童装', FALSE, '2026-01-01', 'active'),
(1, 1, 'FOOD', '食品饮料', NULL, '食品饮料', FALSE, '2026-01-01', 'active'),
(2, 2, 'FRESH', '食品饮料', '生鲜', '生鲜', FALSE, '2026-01-01', 'active');

INSERT INTO payment_receipts (id, receipt_no, receipt_type, party_type, party_id, account_id,
    receipt_date, amount, currency, status, handled_by, confirmed_at, remark)
VALUES
(9010, 'PR-SEM-9010', 'customer_receipt', 'customer', 2, 1, '2026-02-18', 18000.00, 'CNY', 'confirmed', 39, '2026-02-18 16:00:00', '语义层样例-渠道订单回款'),
(9011, 'PR-SEM-9011', 'customer_receipt', 'customer', 3, 1, '2026-02-19', 16880.00, 'CNY', 'confirmed', 39, '2026-02-19 16:30:00', '语义层样例-全额回款'),
(9012, 'PR-SEM-9012', 'customer_receipt', 'customer', 5, 1, '2026-03-05', 3000.00, 'CNY', 'confirmed', 40, '2026-03-05 15:00:00', '语义层样例-部分回款'),
(9013, 'PR-SEM-9013', 'customer_receipt', 'customer', 4, 1, '2026-03-18', 0.00, 'CNY', 'draft', 40, NULL, '语义层样例-失败支付');

INSERT INTO payment_receipt_allocations (receipt_id, reference_type, reference_id, allocated_amount) VALUES
(9010, 'sales_order', 9010, 18000.00),
(9011, 'sales_order', 9011, 16880.00),
(9012, 'sales_order', 9013, 3000.00),
(9013, 'sales_order', 9012, 0.00);

INSERT INTO cashier_journals (id, journal_no, journal_date, account_id, cashier_id, journal_type,
    amount, counterparty, reference_type, reference_id, voucher_id, bank_account, status, remark)
VALUES
(9010, 'CJ-SEM-9010', '2026-02-18', 1, 39, 'receipt', 18000.00, '华东商贸客户', 'sales_order', 9010, NULL, NULL, 'confirmed', '语义层样例回款'),
(9011, 'CJ-SEM-9011', '2026-02-19', 1, 39, 'receipt', 16880.00, '女装零售客户', 'sales_order', 9011, NULL, NULL, 'confirmed', '语义层样例回款'),
(9012, 'CJ-SEM-9012', '2026-03-05', 1, 40, 'receipt', 3000.00, '小批量客户', 'sales_order', 9013, NULL, NULL, 'confirmed', '语义层样例部分回款'),
(9013, 'CJ-SEM-9013', '2026-03-18', 1, 40, 'receipt', 0.00, '账期客户', 'sales_order', 9012, NULL, NULL, 'draft', '语义层样例失败支付');

INSERT INTO payments (id, payment_no, customer_id, order_id, receipt_id, journal_id, payment_date,
    amount, currency, payment_method, payment_status, failure_reason)
VALUES
(9010, 'PAY-SEM-9010', 2, 9010, 9010, 9010, '2026-02-18', 18000.00, 'CNY', 'transfer', 'paid', NULL),
(9011, 'PAY-SEM-9011', 3, 9011, 9011, 9011, '2026-02-19', 16880.00, 'CNY', 'card', 'paid', NULL),
(9012, 'PAY-SEM-9012', 5, 9013, 9012, 9012, '2026-03-05', 3000.00, 'CNY', 'wechat', 'paid', NULL),
(9013, 'PAY-SEM-9013', 4, 9012, 9013, 9013, '2026-03-18', 0.00, 'CNY', 'credit', 'failed', '授信额度不足'),
(9014, 'PAY-SEM-9014', 2, 9010, NULL, NULL, '2026-03-02', 860.00, 'CNY', 'transfer', 'refunded', '销售退货退款');

INSERT INTO sales_fact (
    id, order_id, order_item_id, customer_id, product_id, category_dim_id, warehouse_id,
    region_dim_id, fiscal_date, payment_id, quantity_sold, sales_amount, paid_amount,
    refund_amount, net_sales_amount, gross_margin_amount, order_status, sales_channel
) VALUES
(9010, 9010, 9010, 2, 10, 1, 1, 1, '2026-02-18', 9010, 35.0000, 13000.00, 8218.00, 860.00, 12140.00, 2100.00, 'confirmed', 'b2b'),
(9011, 9010, 9011, 2, 11, 1, 1, 1, '2026-02-18', 9010, 18.0000, 12400.00, 9782.00, 0.00, 12400.00, 3300.00, 'confirmed', 'b2b'),
(9012, 9011, 9012, 3, 31, 17, 1, 1, '2026-02-19', 9011, 8.0000, 13140.00, 13140.00, 0.00, 13140.00, 2900.00, 'delivered', 'retail'),
(9013, 9011, 9013, 3, 35, 17, 1, 1, '2026-02-19', 9011, 5.0000, 3740.00, 3740.00, 0.00, 3740.00, 900.00, 'delivered', 'retail'),
(9014, 9012, 9014, 4, 34, 17, 2, 3, '2026-03-02', 9013, 12.0000, 30060.00, 0.00, 0.00, 30060.00, 6800.00, 'confirmed', 'credit'),
(9015, 9012, 9015, 4, 33, 17, 2, 3, '2026-03-02', 9013, 20.0000, 5840.00, 0.00, 0.00, 5840.00, 1500.00, 'confirmed', 'credit'),
(9016, 9013, 9016, 5, 22, 2, 2, 3, '2026-03-05', 9012, 7.0000, 6740.00, 2200.00, 0.00, 6740.00, 1200.00, 'delivering', 'wechat'),
(9017, 9013, 9017, 5, 18, 18, 2, 3, '2026-03-05', 9012, 26.0000, 2600.00, 800.00, 0.00, 2600.00, 500.00, 'delivering', 'wechat'),
(9018, 9001, 9001, 1, 1, 1, 1, 1, '2026-02-12', NULL, 20.0000, 17480.00, 12000.00, 0.00, 17480.00, 4500.00, 'confirmed', 'promotion');
