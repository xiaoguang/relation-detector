-- ============================================================
-- ERP系统补充测试数据 - Oracle 12c
-- 目的: 对齐 MySQL 8.0 样例中已有但 Oracle 初稿缺失的数据目标表
-- ============================================================

-- 促销与提成
INSERT INTO commission_rules (id, name, product_category_id, min_amount, max_amount, commission_rate, bonus, effective_date, status) VALUES
(1, '标准销售提成', NULL, 0.00, 99999999.99, 0.0300, 0.00, '2026-01-01', 'active');

INSERT INTO promotions (id, name, code, promotion_type, discount_value, min_purchase_amount, max_discount_amount, usage_limit, used_count, start_date, end_date, status) VALUES
(1, '春节满减活动', 'PROMO-2026-SPRING', 'discount_amount', 500.00, 5000.00, 500.00, 1000, 15, '2026-01-01', '2026-02-28', 'active');

INSERT INTO promotion_products (promotion_id, product_id, category_id) VALUES
(1, 1, NULL),
(1, 2, NULL);

-- 发票、税票、账龄和汇率
INSERT INTO invoices (id, invoice_no, invoice_type, supplier_id, customer_id, invoice_date, due_date, total_amount, tax_amount, tax_rate, status, verified_by, verified_at) VALUES
(1, 'INV-S-202601-0001', 'sales', NULL, 1, '2026-01-15', '2026-02-14', 128000.00, 14725.66, 0.1300, 'verified', 36, '2026-01-16 10:00:00'),
(2, 'INV-P-202601-0001', 'purchase', 1, NULL, '2026-01-20', '2026-02-19', 56000.00, 6442.48, 0.1300, 'received', NULL, NULL);

INSERT INTO tax_invoices (id, invoice_no, invoice_code, invoice_type, tax_direction, party_type, party_id, invoice_date,
    amount_excluding_tax, tax_rate, verification_status, verified_at, verified_by, tax_period, deduction_period,
    reference_type, reference_id, status) VALUES
(1, 'TAX-OUT-202601-0001', '310026001', 'vat_special', 'output', 'customer', 1, '2026-01-15',
 113274.34, 0.1300, 'verified', '2026-01-16 10:00:00', 36, '2026-01', NULL, 'sales_order', 1, 'issued'),
(2, 'TAX-IN-202601-0001', '440026001', 'vat_special', 'input', 'supplier', 1, '2026-01-20',
 49557.52, 0.1300, 'certified', '2026-01-22 09:30:00', 36, '2026-01', '2026-01', 'purchase_order', 1, 'verified');

INSERT INTO ar_aging_snapshots (id, snapshot_date, customer_id, order_id, invoice_amount, paid_amount, due_date, last_collection_date, collection_notes) VALUES
(2, '2026-01-31', 1, 1, 128000.00, 60000.00, '2026-02-14', '2026-01-25', '客户承诺二月结清');

INSERT INTO ap_aging_snapshots (id, snapshot_date, supplier_id, order_id, invoice_amount, paid_amount, due_date, planned_payment_date) VALUES
(1, '2026-01-31', 1, 1, 56000.00, 30000.00, '2026-02-19', '2026-02-10');

INSERT INTO exchange_rates (id, from_currency, to_currency, rate_date, rate, rate_source) VALUES
(1, 'USD', 'CNY', '2026-01-31', 7.180000, 'BOC'),
(2, 'EUR', 'CNY', '2026-01-31', 7.820000, 'BOC');

-- 合同与项目
INSERT INTO contracts (id, contract_no, contract_type, party_type, party_id, subject, total_amount, currency,
    signed_date, start_date, end_date, payment_terms, delivery_terms, status, prepared_by, approved_by, signed_by) VALUES
(1, 'CT-S-202601-0001', 'sales', 'customer', 1, '年度销售框架合同', 1200000.00, 'CNY',
 '2026-01-05', '2026-01-01', '2026-12-31', '月结30天', '分批交付', 'active', 5, 4, '客户采购负责人'),
(2, 'CT-P-202601-0001', 'purchase', 'supplier', 1, '核心物料采购框架合同', 800000.00, 'CNY',
 '2026-01-08', '2026-01-01', '2026-12-31', '票到30天', '按订单交付', 'active', 27, 26, '供应商销售负责人');

INSERT INTO contract_milestones (id, contract_id, milestone_name, milestone_type, planned_date, actual_date, amount, completion_pct, status, responsible_person) VALUES
(1, 1, '第一季度交付', 'delivery', '2026-03-31', NULL, 300000.00, 30.00, 'in_progress', 5),
(2, 2, '首批采购到货', 'acceptance', '2026-02-20', NULL, 120000.00, 0.00, 'pending', 27);

INSERT INTO projects (id, project_no, name, project_type, department_id, manager_id, budget, start_date, planned_end_date, status, priority, description) VALUES
(1, 'PRJ-2026-ERP-001', '华东仓储自动化升级', 'internal', 5, 42, 500000.00, '2026-01-10', '2026-06-30', 'in_progress', 'high', '提升仓储拣货与盘点效率');

INSERT INTO project_costs (id, project_id, cost_type, cost_date, amount, description, reference_type, reference_id, recorded_by) VALUES
(1, 1, 'equipment', '2026-01-20', 85000.00, 'PDA与条码设备采购', 'purchase_order', 1, 36),
(2, 1, 'labor', '2026-01-31', 32000.00, '实施顾问人力成本', 'voucher', 1, 36);

-- 质检、生产、序列号和寄售
INSERT INTO inspection_standards (id, product_id, standard_name, inspection_items, sampling_method, sample_size, aql_level, status) VALUES
(1, 1, '入库外观与功能抽检', '[{"item":"appearance","required":"no scratch"},{"item":"function","required":"power on"}]', 'gb2828', 20, 1.00, 'active'),
(2, 2, '包装完整性抽检', '[{"item":"package","required":"sealed"}]', 'fixed', 10, 1.50, 'active');

INSERT INTO inspection_reports (id, report_no, inspection_type, reference_type, reference_id, product_id, batch_id, standard_id,
    sample_size, inspected_qty, qualified_qty, defective_qty, inspection_result, inspector_id, inspection_date, status) VALUES
(1, 'IR-202601-0001', 'IQC', 'purchase_receipt', 1, 1, 1, 1, 20, 20, 19, 1, 'qualified', 51, '2026-01-21', 'completed');

INSERT INTO boms (id, parent_product_id, child_product_id, quantity, unit, scrap_rate, sort_order, effective_date, status) VALUES
(1, 1, 2, 2.000, 'PCS', 0.0200, 1, '2026-01-01', 'active');

INSERT INTO work_orders (id, order_no, product_id, bom_id, planned_quantity, completed_quantity, rejected_quantity,
    warehouse_id, start_date, due_date, status, priority, released_by, remark) VALUES
(1, 'WO-202601-0001', 1, 1, 100, 60, 2, 1, '2026-01-12', '2026-01-20', 'in_progress', 'high', 42, '首批生产试运行');

INSERT INTO work_order_materials (id, work_order_id, product_id, batch_id, required_qty, issued_qty, returned_qty, actual_consumed, unit, status) VALUES
(1, 1, 2, 2, 200.000, 180.000, 5.000, 175.000, 'PCS', 'issued');

INSERT INTO serial_numbers (id, product_id, batch_id, serial_no, status, warehouse_id, purchase_receipt_id, warranty_start, warranty_end, last_scan_date, last_scan_location) VALUES
(1, 1, 1, 'SN2026010001', 'in_stock', 1, 1, '2026-01-21', '2027-01-20', '2026-01-21 10:00:00', '主仓库A区'),
(2, 1, 1, 'SN2026010002', 'sold', NULL, 1, '2026-01-21', '2027-01-20', '2026-01-25 16:00:00', '客户签收');

INSERT INTO consignment_inventory (id, product_id, batch_id, customer_id, consigned_qty, consumed_qty, unit_price, consigned_date, last_consumed_date, settlement_period, status) VALUES
(1, 1, 1, 1, 50, 12, 899.00, '2026-01-10', '2026-01-25', '2026-01', 'active');

-- 审批流、固定资产、KPI和售后
INSERT INTO approval_workflows (id, workflow_name, workflow_code, target_type, description, is_active) VALUES
(1, '采购审批流', 'PURCHASE_APPROVAL', 'purchase_order', '采购订单金额分级审批', 1),
(2, '库存调整审批流', 'INVENTORY_ADJUST_APPROVAL', 'stocktake', '库存盘点差异审批', 1);

INSERT INTO approval_nodes (id, workflow_id, node_name, node_level, approver_type, approver_id, approval_mode, timeout_hours, can_delegate) VALUES
(1, 1, '部门经理审批', 1, 'department_manager', NULL, 'single', 24, 1),
(2, 1, '财务复核', 2, 'role', 6, 'single', 48, 1),
(3, 2, '仓储经理审批', 1, 'employee', 42, 'single', 24, 0);

INSERT INTO fixed_assets (id, asset_no, name, category, purchase_date, purchase_amount, salvage_value, useful_life_months,
    accumulated_depreciation, department_id, custodian_id, location, status, last_depreciation_date) VALUES
(1, 'FA-202601-0001', '仓库PDA设备', 'equipment', '2026-01-05', 48000.00, 2000.00, 36, 1277.78, 5, 43, '主仓库', 'in_use', '2026-01-31');

INSERT INTO kpi_indicators (id, name, indicator_type, unit, target_direction, target_value, target_min, target_max, weight, department_id, status) VALUES
(1, '销售回款率', 'financial', '%', 'higher_better', 95.00, NULL, NULL, 0.3000, 2, 'active'),
(2, '库存准确率', 'operational', '%', 'higher_better', 98.00, NULL, NULL, 0.2500, 5, 'active');

INSERT INTO service_tickets (id, ticket_no, customer_id, order_id, product_id, ticket_type, priority, subject, description, status, assigned_to, resolution, resolved_at, satisfaction_score) VALUES
(1, 'ST-202601-0001', 1, 1, 1, 'complaint', 'normal', '外包装破损', '客户反馈收货时外箱破损', 'resolved', 52, '补寄包装并优惠券补偿', '2026-01-28 15:00:00', 5);
